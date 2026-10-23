package com.nankki.skydaily.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** 系统设置页缺失时退回本应用信息，权限状态仍由返回前台后的检查决定。 */
internal fun openSystemSettings(context: Context, intent: Intent) {
    fun launch(value: Intent): Boolean = try {
        if (context !is Activity) value.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(value)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
    if (launch(intent)) return
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    if (launch(details)) return
    Toast.makeText(context, "无法打开系统设置，请从应用信息中调整权限", Toast.LENGTH_LONG).show()
}
