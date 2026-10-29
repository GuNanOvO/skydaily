package com.nankki.skydaily.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshNetworkTest {
    @Test fun `only connected unmetered noncellular networks permit restricted automatic refresh`() {
        val cases = listOf(
            booleanArrayOf(false, false, false, false),
            booleanArrayOf(true, false, false, true),
            booleanArrayOf(true, true, false, false),
            booleanArrayOf(true, true, true, false),
            booleanArrayOf(true, false, true, false),
        )
        for ((connected, metered, cellular, expected) in cases) {
            assertEquals(expected, isUnmeteredRefreshNetwork(connected, metered, cellular))
        }
    }
}
