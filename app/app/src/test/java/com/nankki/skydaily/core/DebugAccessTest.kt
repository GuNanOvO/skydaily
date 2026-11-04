package com.nankki.skydaily.core

import com.nankki.skydaily.MemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAccessTest {
    @Test fun `seven consecutive taps unlock and survive recreation`() {
        val store = MemoryKeyValueStore()
        val access = DebugAccess(store)
        assertFalse(access.isUnlocked)
        repeat(6) { assertFalse(access.tap(it * 200L)) }
        assertFalse(access.isUnlocked)
        assertTrue(access.tap(1200L))
        assertTrue(DebugAccess(store).isUnlocked)
        assertFalse(access.tap(1300L))
    }

    @Test fun `a gap longer than eight hundred milliseconds restarts the sequence`() {
        val access = DebugAccess(MemoryKeyValueStore())
        repeat(6) { access.tap(it * 100L) }
        assertFalse(access.tap(1301L))
        repeat(5) { assertFalse(access.tap(1401L + it * 100L)) }
        assertTrue(access.tap(1901L))
    }

    @Test fun `eight hundred millisecond gaps still count as consecutive`() {
        val access = DebugAccess(MemoryKeyValueStore())
        repeat(6) { assertFalse(access.tap(it * 800L)) }
        assertTrue(access.tap(4800L))
    }

    @Test fun `hide persists and starts a fresh unlock sequence`() {
        val store = MemoryKeyValueStore()
        val access = DebugAccess(store)
        repeat(7) { access.tap(it * 100L) }
        access.hide()
        assertFalse(DebugAccess(store).isUnlocked)
        repeat(6) { assertFalse(access.tap(1000L + it * 100L)) }
        assertTrue(access.tap(1600L))
    }

    @Test fun `backward clock restarts instead of completing an old sequence`() {
        val access = DebugAccess(MemoryKeyValueStore())
        repeat(6) { access.tap(1000L + it * 100L) }
        assertFalse(access.tap(100L))
        repeat(5) { assertFalse(access.tap(200L + it * 100L)) }
        assertTrue(access.tap(700L))
    }
}
