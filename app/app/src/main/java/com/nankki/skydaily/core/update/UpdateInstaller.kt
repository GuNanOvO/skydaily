package com.nankki.skydaily.core.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.File

/** 安装请求的结果；未知来源设置只提供给用户手动允许。 */
sealed interface InstallOutcome {
    data object Started : InstallOutcome
    data object NeedsUnknownSources : InstallOutcome
    data object Unavailable : InstallOutcome
}

/**
 * 通过系统安装器安装已校验的安装包。只接受 updates 目录内的 APK，
 * 以只读 content URI 临时授权，不做静默安装。
 */
object UpdateInstaller {
    fun install(context: Context, file: File): InstallOutcome {
        val app = context.applicationContext ?: context
        if (!file.isFile) return InstallOutcome.Unavailable
        if (!UpdatePaths.contains(UpdatePaths.directory(app.filesDir), file)) return InstallOutcome.Unavailable
        if (!app.packageManager.canRequestPackageInstalls()) return InstallOutcome.NeedsUnknownSources
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(UpdateFileProvider.uriFor(file.name), UpdateFileProvider.APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            app.startActivity(intent)
            InstallOutcome.Started
        } catch (_: ActivityNotFoundException) {
            InstallOutcome.Unavailable
        } catch (_: SecurityException) {
            InstallOutcome.Unavailable
        }
    }

    /** 应用级的「安装未知应用」设置入口。 */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
}
