package com.nankki.skydaily.core.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 更新入口：前台返回时最多每 24 小时检查一次，手动检查不受限制。
 * 所有方法都可重复调用；下载与安装只在用户明确操作后启动。
 */
object UpdateController {
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 前台返回时的按需检查；下载或待安装期间不打扰当前流程。 */
    fun maybeCheck(context: Context) {
        val app = context.applicationContext ?: context
        UpdateStore.attach(app)
        reconcile(app)
        if (!backgroundCheckAllowed(app)) return
        launchCheck(app)
    }

    fun check(context: Context, manual: Boolean) {
        val app = context.applicationContext ?: context
        UpdateStore.attach(app)
        reconcile(app)
        if (!manual && !backgroundCheckAllowed(app)) return
        launchCheck(app)
    }

    fun download(context: Context) {
        val app = context.applicationContext ?: context
        UpdateStore.attach(app)
        val release = UpdateStore.release.value ?: return
        when (UpdateStore.state.value) {
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.Ready,
            is UpdateState.Installing,
            -> return

            else -> Unit
        }
        if (UpdateDownloadService.isRunning) return
        val session = UpdateStore.beginDownload()
        UpdateStore.update(UpdateState.Downloading(0, release.size))
        try {
            UpdateDownloadService.start(app, session)
        } catch (_: RuntimeException) {
            // 前台服务被系统拒绝时回滚会话，避免界面永远停在 0%。
            UpdateStore.cancelDownload()
            UpdateStore.update(UpdateState.Failed("无法启动下载服务，请重试", retryable = true))
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext ?: context
        UpdateStore.attach(app)
        val state = UpdateStore.state.value
        if (state !is UpdateState.Downloading && state !is UpdateState.Verifying) return
        // 先作废旧会话，再停止服务；晚到的网络回调不会把「已取消」改成失败。
        UpdateStore.cancelDownload()
        UpdateDownloadService.stop(app)
        val release = UpdateStore.release.value
        UpdateStore.update(if (release != null) UpdateState.Available(release) else UpdateState.Idle)
    }

    fun install(context: Context): InstallOutcome {
        val app = context.applicationContext ?: context
        UpdateStore.attach(app)
        val ready = UpdateStore.state.value as? UpdateState.Ready ?: return InstallOutcome.Unavailable
        return when (val outcome = UpdateInstaller.install(app, ready.file)) {
            InstallOutcome.Started -> {
                UpdateStore.update(UpdateState.Installing)
                outcome
            }

            InstallOutcome.NeedsUnknownSources -> outcome

            InstallOutcome.Unavailable -> {
                UpdateStore.hold(null)
                UpdateStore.update(UpdateState.Failed("安装包不可用，请重新下载", retryable = true))
                outcome
            }
        }
    }

    /** 忽略当前更新；已下载的候选安装包一并删除。 */
    fun dismiss() {
        val ready = UpdateStore.state.value as? UpdateState.Ready
        UpdateStore.hold(null)
        UpdateStore.update(UpdateState.Idle)
        ready?.file?.delete()
    }

    private fun launchCheck(app: Context) {
        val state = UpdateStore.state.value
        if (state is UpdateState.Checking ||
            state is UpdateState.Downloading ||
            state is UpdateState.Verifying ||
            state is UpdateState.Ready ||
            state is UpdateState.Installing
        ) {
            return
        }
        UpdateStore.update(UpdateState.Checking)
        UpdateStore.hold(null)
        scope.launch { runCheck(app) }
    }

    private fun runCheck(app: Context) {
        val installed = installed(app)
        if (installed == null) {
            UpdateStore.update(UpdateState.Failed("无法读取当前版本", retryable = true))
            return
        }
        val result = runCatching {
            UpdateChecker(app.packageName).check(installed.code, installed.name)
        }.getOrElse {
            UpdateCheck.Failed(it.message ?: "检查更新失败", retryable = true)
        }
        val now = System.currentTimeMillis()
        when (result) {
            UpdateCheck.UpToDate -> {
                UpdateStore.recordCheck(now, installed.code)
                UpdateStore.update(UpdateState.UpToDate(now))
            }

            is UpdateCheck.Available -> {
                UpdateStore.recordCheck(now, installed.code)
                UpdateStore.hold(result.release)
                UpdateStore.update(UpdateState.Available(result.release))
            }

            is UpdateCheck.Incompatible -> {
                UpdateStore.recordCheck(now, installed.code)
                UpdateStore.update(UpdateState.Incompatible(result.release))
            }

            is UpdateCheck.Failed -> {
                // 失败也记录尝试时间：前台自动检查同样保持最多每 24 小时一次，
                // 但不能把失败展示成「已是最新版本」。
                UpdateStore.recordCheck(now, installed.code)
                UpdateStore.update(UpdateState.Failed(result.message, result.retryable))
            }
        }
    }

    private fun backgroundCheckAllowed(app: Context): Boolean {
        when (UpdateStore.state.value) {
            is UpdateState.Checking,
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.Ready,
            is UpdateState.Installing,
            -> return false

            else -> Unit
        }
        val installed = installed(app) ?: return false
        val lastCheck = UpdateStore.lastCheckAt()
        if (lastCheck <= 0L) return true
        if (UpdateStore.lastCheckCode() != installed.code) return true
        return System.currentTimeMillis() - lastCheck >= CHECK_INTERVAL_MS
    }

    /**
     * 从系统安装器返回后核对结果：安装成功则把版本标记为最新并删除旧安装包，
     * 安装被取消则保留已校验的安装包，允许再次安装。
     * 没有活动流程时清掉进程重启留下的 `.part` 与孤立 `.apk`；
     * 下载不能续传，也不展示没有发布信息对应的旧文件。
     */
    private fun reconcile(app: Context) {
        val installed = installed(app) ?: return
        when (val state = UpdateStore.state.value) {
            is UpdateState.Checking,
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            -> Unit

            is UpdateState.Installing -> {
                val release = UpdateStore.release.value
                if (release == null) {
                    UpdatePaths.clearStale(app.filesDir)
                    UpdateStore.update(UpdateState.Idle)
                    return
                }
                if (installed.code >= release.versionCode) {
                    UpdatePaths.apk(app.filesDir, release.versionCode).delete()
                    UpdatePaths.clearStale(app.filesDir)
                    val now = System.currentTimeMillis()
                    UpdateStore.recordCheck(now, installed.code)
                    UpdateStore.hold(null)
                    UpdateStore.update(UpdateState.UpToDate(now))
                } else {
                    val file = UpdatePaths.apk(app.filesDir, release.versionCode)
                    UpdateStore.update(
                        if (file.isFile) UpdateState.Ready(file, release) else UpdateState.Available(release),
                    )
                }
            }

            is UpdateState.Ready -> {
                val release = UpdateStore.release.value ?: return
                when {
                    installed.code >= release.versionCode -> {
                        state.file.delete()
                        UpdatePaths.clearStale(app.filesDir)
                        val now = System.currentTimeMillis()
                        UpdateStore.recordCheck(now, installed.code)
                        UpdateStore.hold(null)
                        UpdateStore.update(UpdateState.UpToDate(now))
                    }

                    !state.file.isFile -> UpdateStore.update(UpdateState.Available(release))
                    else -> Unit
                }
            }

            else -> UpdatePaths.clearStale(app.filesDir)
        }
    }

    private fun installed(context: Context): InstalledApp? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        InstalledApp(code = info.longVersionCode.toInt(), name = info.versionName.orEmpty())
    }.getOrNull()

    private data class InstalledApp(val code: Int, val name: String)
}
