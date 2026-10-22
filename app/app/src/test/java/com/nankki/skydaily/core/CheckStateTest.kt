package com.nankki.skydaily.core

import com.nankki.skydaily.MemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckStateTest {
    @Test
    fun `checks are scoped to a date`() {
        val state = CheckState(MemoryKeyValueStore())
        assertFalse(state.isDone("2026-10-21", 1))
        state.setDone("2026-10-21", 1, true)
        state.setDone("2026-10-21", 3, true)
        assertTrue(state.isDone("2026-10-21", 1))
        assertEquals(setOf(1, 3), state.done("2026-10-21"))
        assertEquals(0, state.completed("2026-10-22"))
    }

    @Test
    fun `unchecking removes the mark`() {
        val state = CheckState(MemoryKeyValueStore())
        state.setDone("2026-10-21", 2, true)
        state.setDone("2026-10-21", 2, false)
        assertFalse(state.isDone("2026-10-21", 2))
    }

    @Test
    fun `prune drops dates outside the window`() {
        val store = MemoryKeyValueStore()
        val state = CheckState(store)
        state.setDone("2026-10-20", 1, true)
        state.setDone("2026-10-21", 1, true)
        state.prune(listOf("2026-10-21"))
        assertFalse(state.isDone("2026-10-20", 1))
        assertTrue(state.isDone("2026-10-21", 1))
    }
}
