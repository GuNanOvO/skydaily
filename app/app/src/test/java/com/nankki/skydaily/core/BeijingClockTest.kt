package com.nankki.skydaily.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeijingClockTest {
    @Test
    fun `date changes at beijing midnight`() {
        assertEquals("2026-10-21", BeijingClock.today(java.time.Instant.parse("2026-10-21T15:59:59Z").toEpochMilli()))
        assertEquals("2026-10-22", BeijingClock.today(java.time.Instant.parse("2026-10-21T16:00:00Z").toEpochMilli()))
    }

    @Test
    fun `weekday follows the date`() {
        assertEquals("星期三", BeijingClock.weekday("2026-10-21"))
        assertEquals("星期日", BeijingClock.weekday("2026-10-25"))
    }

    @Test
    fun `labels use beijing time`() {
        val epoch = java.time.Instant.parse("2026-10-21T16:11:00Z").toEpochMilli()
        assertEquals("2026-10-22", BeijingClock.updatedDate(epoch))
        assertEquals("00:11", BeijingClock.updatedAt(epoch))
        assertEquals("2026 / 10", BeijingClock.monthLabel("2026-10-22"))
        assertEquals("22", BeijingClock.dayLabel("2026-10-22"))
    }

    @Test
    fun `notice timestamps are shown in beijing time`() {
        assertEquals("2026.10.09", BeijingClock.noticeDate("2026-10-09T00:27:39.000Z"))
    }

    @Test
    fun `auto theme follows beijing night hours`() {
        val at = { iso: String -> java.time.Instant.parse(iso).toEpochMilli() }
        assertFalse(ThemeResolver.isDark(ThemeMode.Auto, at("2026-10-21T10:59:00Z")))
        assertTrue(ThemeResolver.isDark(ThemeMode.Auto, at("2026-10-21T11:00:00Z")))
        assertTrue(ThemeResolver.isDark(ThemeMode.Auto, at("2026-10-21T22:59:00Z")))
        assertFalse(ThemeResolver.isDark(ThemeMode.Auto, at("2026-10-21T23:00:00Z")))
    }

    @Test
    fun `manual theme wins over the hour`() {
        val night = java.time.Instant.parse("2026-10-21T13:00:00Z").toEpochMilli()
        assertFalse(ThemeResolver.isDark(ThemeMode.Light, night))
        assertTrue(ThemeResolver.isDark(ThemeMode.Dark, java.time.Instant.parse("2026-10-21T02:00:00Z").toEpochMilli()))
    }
}
