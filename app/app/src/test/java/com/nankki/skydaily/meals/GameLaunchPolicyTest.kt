package com.nankki.skydaily.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameLaunchPolicyTest {
    @Test fun `official client wins over installed channel clients`() {
        val installed = mapOf("com.netease.sky.vivo" to 3, "com.netease.sky" to 1, "com.netease.sky.huawei" to 2)
        assertEquals(1, GameLaunchPolicy.resolve(installed::get))
    }

    @Test fun `channel client is used when official has no launcher`() {
        val installed = mapOf("com.netease.sky.bilibili" to 4, "com.netease.sky.vivo" to 3)
        assertEquals(3, GameLaunchPolicy.resolve(installed::get))
    }

    @Test fun `every supported channel can launch without official`() {
        for (pkg in listOf("com.netease.sky.huawei", "com.netease.sky.vivo", "com.netease.sky.bilibili", "com.netease.sky.aligames", "com.tencent.tmgp.eyou.eygy")) {
            val installed = mapOf(pkg to 7)
            assertEquals(pkg, 7, GameLaunchPolicy.resolve(installed::get))
        }
    }

    @Test fun `international client is not a fallback`() {
        val installed = mapOf("com.tgc.sky.android" to 9)
        assertNull(GameLaunchPolicy.resolve(installed::get))
    }

    @Test fun `no launchable game has no target`() {
        assertNull(GameLaunchPolicy.resolve<Int> { null })
    }
}
