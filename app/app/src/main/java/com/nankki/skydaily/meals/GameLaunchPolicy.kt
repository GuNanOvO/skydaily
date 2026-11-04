package com.nankki.skydaily.meals

/** 只选择可启动的国服客户端，官服优先，其余按固定渠道顺序选择。 */
internal object GameLaunchPolicy {
    private val packages = arrayOf(
        "com.netease.sky",
        "com.netease.sky.huawei",
        "com.netease.sky.vivo",
        "com.netease.sky.bilibili",
        "com.netease.sky.aligames",
        "com.tencent.tmgp.eyou.eygy",
    )

    fun <T : Any> resolve(launcher: (String) -> T?): T? {
        for (packageName in packages) {
            val target = launcher(packageName)
            if (target != null) return target
        }
        return null
    }
}
