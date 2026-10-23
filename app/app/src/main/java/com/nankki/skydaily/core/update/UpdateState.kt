package com.nankki.skydaily.core.update

import java.io.File

/**
 * 关于页与下载服务共享的更新状态。进程丢失后回到 [Idle]，
 * 不虚构下载进度或安装结果。
 */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    data class Available(val release: AppRelease) : UpdateState

    /** 版本存在但要求的系统版本高于当前设备。 */
    data class Incompatible(val release: AppRelease) : UpdateState

    data class Downloading(val received: Long, val total: Long) : UpdateState

    data object Verifying : UpdateState

    /** 已下载并通过校验、等待系统安装器的安装包。 */
    data class Ready(val file: File, val release: AppRelease) : UpdateState

    data object Installing : UpdateState

    data class Failed(val message: String, val retryable: Boolean) : UpdateState
}
