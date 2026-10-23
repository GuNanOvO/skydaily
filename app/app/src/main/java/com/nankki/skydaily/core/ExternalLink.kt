package com.nankki.skydaily.core

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun openWebLink(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (error: RuntimeException) {
        if (error !is ActivityNotFoundException && error !is SecurityException) throw error
        Toast.makeText(context, "无法打开链接，请检查浏览器设置", Toast.LENGTH_LONG).show()
    }
}
