package com.nankki.skydaily.core.update

import android.content.Context
import com.nankki.skydaily.core.AndroidKeyValueStore
import com.nankki.skydaily.core.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内共享的更新状态。界面重建后仍读取同一份状态；
 * 只有上次检查时间与当时的安装版本写入 SharedPreferences。
 */
object UpdateStore {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    /** 当前已发布、正在下载或等待安装的版本；检查失败或忽略后清空。 */
    private val mutableRelease = MutableStateFlow<AppRelease?>(null)
    val release: StateFlow<AppRelease?> = mutableRelease.asStateFlow()

    @Volatile
    private var store: KeyValueStore? = null

    @Synchronized
    fun attach(context: Context) {
        if (store == null) store = AndroidKeyValueStore(context.applicationContext ?: context)
    }

    fun update(state: UpdateState) {
        mutableState.value = state
    }

    fun hold(release: AppRelease?) {
        mutableRelease.value = release
    }

    fun lastCheckAt(): Long = store?.getString(KEY_LAST_CHECK)?.toLongOrNull() ?: 0L

    fun lastCheckCode(): Int = store?.getString(KEY_LAST_CODE)?.toIntOrNull() ?: -1

    fun recordCheck(checkedAt: Long, installedCode: Int) {
        val target = store ?: return
        target.putString(KEY_LAST_CHECK, checkedAt.toString())
        target.putString(KEY_LAST_CODE, installedCode.toString())
    }

    /**
     * 下载会话标记：取消或开始新一轮下载都会递增。
     * 旧会话无论因为慢网络还是线程调度才返回，都不能再覆盖当前状态。
     */
    @Volatile
    private var downloadSession = 0L

    @Synchronized
    internal fun beginDownload(): Long {
        downloadSession += 1
        return downloadSession
    }

    @Synchronized
    internal fun cancelDownload() {
        downloadSession += 1
    }

    @Synchronized
    internal fun isDownloadActive(session: Long): Boolean = session == downloadSession

    private const val KEY_LAST_CHECK = "update:last_check"
    private const val KEY_LAST_CODE = "update:last_code"
}
