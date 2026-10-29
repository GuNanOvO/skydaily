package com.nankki.skydaily.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal fun isUnmeteredRefreshNetwork(connected: Boolean, metered: Boolean, cellular: Boolean): Boolean =
    connected && !metered && !cellular

/** 计费 Wi-Fi 和标记为不限量的移动网络仍受移动/计费开关控制。 */
internal fun isUnmeteredRefreshNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return isUnmeteredRefreshNetwork(
        connected = true,
        metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    )
}
