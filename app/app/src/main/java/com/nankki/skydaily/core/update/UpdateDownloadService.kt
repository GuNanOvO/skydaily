package com.nankki.skydaily.core.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.nankki.skydaily.MainActivity
import com.nankki.skydaily.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call

/**
 * 短暂的 dataSync 前台服务：只在用户点按下载后启动，
 * 流式写入应用私有的 `.part` 文件，校验通过后才改名为 `.apk`。
 */
class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var job: Job? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var activeCall: Call? = null

    @Volatile
    private var partFile: File? = null

    private var session = 0L

    private var lastStoreReport = 0L
    private var lastNotificationReport = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        UpdateStore.attach(this)
        isRunning = true
        startForeground(NOTIFICATION_ID, notification(0, 0, indeterminate = true))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getLongExtra(EXTRA_SESSION, 0L) ?: 0L
        if (intent?.action != ACTION_DOWNLOAD ||
            requested <= 0L ||
            !UpdateStore.isDownloadActive(requested)
        ) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val release = UpdateStore.release.value
        if (release == null) {
            UpdateStore.update(UpdateState.Failed("更新信息已失效，请重新检查", retryable = true))
            UpdateStore.cancelDownload()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        session = requested
        job = scope.launch { download(release, requested) }
        return START_NOT_STICKY
    }

    private suspend fun download(release: AppRelease, session: Long) {
        if (!UpdateStore.isDownloadActive(session)) return
        val managed = UpdatePaths.directory(filesDir)
        var part: File? = null
        try {
            if (release.size !in 1..MAX_APK_BYTES) {
                throw UpdateRequestException("安装包大小超出允许范围", retryable = false)
            }
            publish(session, UpdateState.Downloading(0, release.size))
            if (!managed.exists() && !managed.mkdirs()) throw IOException("无法创建更新目录")
            UpdatePaths.clearOtherApks(filesDir, release.versionCode)
            val target = UpdatePaths.part(filesDir, release.versionCode, session)
            part = target
            partFile = target
            UpdatePaths.clearOtherParts(filesDir, target)
            target.delete()

            val client = SameOriginClient(updateHttpClient)
            client.open(release.apkPath, "application/octet-stream") { activeCall = it }.use { response ->
                if (!response.isSuccessful) throw statusFailure(response.code)
                val contentType = response.body.contentType()?.toString().orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) {
                    throw UpdateRequestException("下载地址返回了网页内容", retryable = true)
                }
                val declared = response.body.contentLength()
                if (declared > 0 && declared != release.size) {
                    throw UpdateRequestException("安装包大小与发布信息不一致", retryable = false)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                response.body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (!UpdateStore.isDownloadActive(session)) {
                                throw CancellationException("下载已取消")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            received += read
                            if (received > release.size) {
                                throw UpdateRequestException("安装包超过发布大小", retryable = false)
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            reportProgress(received, release.size, session)
                        }
                        output.flush()
                    }
                }
                if (received != release.size) {
                    throw UpdateRequestException("安装包下载不完整", retryable = true)
                }
                if (!UpdateStore.isDownloadActive(session)) throw CancellationException("下载已取消")
                publish(session, UpdateState.Verifying)
                val installedCode = ApkVerifier.installedVersionCode(this@UpdateDownloadService)
                    ?: throw UpdateRequestException("无法读取当前版本", retryable = true)
                val result = ApkVerifier.verifyArchive(
                    context = this@UpdateDownloadService,
                    file = target,
                    release = release,
                    installedCertSha256 = ApkVerifier.installedCertSha256(this@UpdateDownloadService),
                    installedCode = installedCode,
                    knownSha256 = digest.digest().toHex(),
                )
                val problem = when (result) {
                    VerifyResult.Ok -> null
                    VerifyResult.WrongSize -> "安装包大小校验失败" to true
                    VerifyResult.WrongHash -> "安装包摘要校验失败" to true
                    VerifyResult.WrongPackage -> "安装包不属于当前应用" to false
                    VerifyResult.WrongVersion -> "安装包版本不匹配" to false
                    VerifyResult.WrongCertificate -> "安装包签名与当前应用不一致" to false
                }
                if (problem != null) throw UpdateRequestException(problem.first, problem.second)
            }

            val apk = UpdatePaths.apk(filesDir, release.versionCode)
            synchronized(FILE_LOCK) {
                if (!UpdateStore.isDownloadActive(session) || stopping) {
                    target.delete()
                } else {
                    apk.delete()
                    if (!target.renameTo(apk)) throw UpdateRequestException("无法保存安装包", retryable = true)
                    partFile = null
                    part = null
                    UpdateStore.hold(release)
                    publish(session, UpdateState.Ready(apk, release))
                    // 取消与落盘交错时不留没有界面状态的安装包。
                    if (!UpdateStore.isDownloadActive(session) || stopping) apk.delete()
                }
            }
        } catch (cancelled: CancellationException) {
            part?.delete()
            throw cancelled
        } catch (error: UpdateRequestException) {
            part?.delete()
            publish(session, UpdateState.Failed(error.message ?: "下载失败", error.retryable))
        } catch (error: IOException) {
            part?.delete()
            publish(session, UpdateState.Failed("网络中断，请重试", retryable = true))
        } catch (error: Exception) {
            part?.delete()
            publish(session, UpdateState.Failed("更新下载失败", retryable = true))
        } finally {
            activeCall = null
            partFile = null
            if (stopping) part?.delete()
            job = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun publish(session: Long, state: UpdateState) {
        if (!stopping && UpdateStore.isDownloadActive(session)) {
            UpdateStore.update(state)
        }
    }

    private fun reportProgress(received: Long, total: Long, session: Long) {
        val now = SystemClock.elapsedRealtime()
        if (received == total || now - lastStoreReport >= PROGRESS_INTERVAL_MS) {
            lastStoreReport = now
            publish(session, UpdateState.Downloading(received, total))
        }
        if (received == total || now - lastNotificationReport >= NOTIFICATION_INTERVAL_MS) {
            lastNotificationReport = now
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification(received, total, indeterminate = false))
        }
    }

    private fun notification(received: Long, total: Long, indeterminate: Boolean): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val percent = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 100) else 0
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在下载更新")
            .setContentText(if (indeterminate) "准备下载" else "已下载 $percent%")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
        if (indeterminate || total <= 0) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent, false)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        stopping = true
        isRunning = false
        activeCall?.cancel()
        activeCall = null
        job?.cancel()
        job = null
        partFile?.delete()
        partFile = null
        // 服务被系统销毁或用户划掉任务时，把停在进行中的状态收敛为可重试状态。
        val release = UpdateStore.release.value
        val state = UpdateStore.state.value
        if (session > 0L && UpdateStore.isDownloadActive(session)) {
            UpdateStore.cancelDownload()
            if (state is UpdateState.Downloading || state is UpdateState.Verifying) {
                UpdateStore.update(
                    if (release != null) UpdateState.Available(release) else UpdateState.Idle,
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** 新旧服务实例可能短暂并存，最终安装包路径必须串行更新。 */
        private val FILE_LOCK = Any()
        private const val CHANNEL_ID = "skydaily-updates"
        private const val CHANNEL_NAME = "应用更新"
        private const val CHANNEL_DESCRIPTION = "下载新版本安装包"
        private const val NOTIFICATION_ID = 1101
        private const val ACTION_DOWNLOAD = "com.nankki.skydaily.action.UPDATE_DOWNLOAD"
        private const val EXTRA_SESSION = "com.nankki.skydaily.extra.UPDATE_SESSION"
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 100L
        private const val NOTIFICATION_INTERVAL_MS = 500L

        /** 服务实例仍存活时不再接受新的下载启动，避免取消后立即重下被旧实例吞掉。 */
        @Volatile
        internal var isRunning: Boolean = false

        fun start(context: Context, session: Long) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_SESSION, session)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UpdateDownloadService::class.java))
        }
    }
}
