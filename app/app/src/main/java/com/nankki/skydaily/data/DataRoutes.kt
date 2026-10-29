package com.nankki.skydaily.data

import com.nankki.skydaily.AppConfig
import com.nankki.skydaily.core.KeyValueStore

enum class DataRoute(val label: String) {
    API("CF API"), PAGES("Pages"), JSDELIVR("jsDelivr"), GHFAST("ghfast.top")
}

enum class Mirror(val route: DataRoute, val base: String) {
    JSDELIVR(DataRoute.JSDELIVR, "https://cdn.jsdelivr.net/gh/GuNanOvO/skydaily@data"),
    GHFAST(DataRoute.GHFAST, "https://ghfast.top/https://raw.githubusercontent.com/GuNanOvO/skydaily/data"),
}

data class RouteRequest(val route: DataRoute, val url: String)

class DataRoutes(private val store: KeyValueStore) {
    @Volatile private var selectedMirror = Mirror.entries.firstOrNull { it.name == store.getString("data:mirror") } ?: Mirror.JSDELIVR
    @Volatile var lastDailyRoute: DataRoute? = DataRoute.entries.firstOrNull { it.name == store.getString("data:last-route") }
        private set

    var mirror: Mirror
        get() = selectedMirror
        set(value) {
            selectedMirror = value
            store.putString("data:mirror", value.name)
        }

    fun daily(date: String): List<RouteRequest> {
        val selected = mirror
        return listOf(
            RouteRequest(DataRoute.API, "${AppConfig.SITE_URL}/v1/daily/$date"),
            RouteRequest(DataRoute.PAGES, "${AppConfig.SITE_URL}/data.json"),
            RouteRequest(selected.route, "${selected.base}/history/daily/$date.json"),
        )
    }

    fun notices(): List<RouteRequest> = listOf(
        RouteRequest(DataRoute.API, "${AppConfig.SITE_URL}/v1/notice"),
        RouteRequest(DataRoute.PAGES, "${AppConfig.SITE_URL}/history/notices.json"),
    )

    fun recordWinner(route: DataRoute) {
        lastDailyRoute = route
        store.putString("data:last-route", route.name)
    }

    fun imageUrl(url: String): String {
        val selected = mirror
        if (lastDailyRoute != selected.route) return url
        val path = url.removePrefix(AppConfig.SITE_URL)
        if (!ARCHIVE_IMAGE.matches(path)) return url
        return selected.base + path
    }

    private companion object {
        val ARCHIVE_IMAGE = Regex("/history/(media|thumbs)/[0-9a-f]{24}\\.webp")
    }
}
