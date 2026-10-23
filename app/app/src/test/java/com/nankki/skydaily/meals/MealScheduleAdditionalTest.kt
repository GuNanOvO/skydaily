package com.nankki.skydaily.meals

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealScheduleAdditionalTest {
    private fun at(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()

    private fun startOfHour(hour: Int): Long =
        at("2026-10-23T%02d:00:00+08:00".format(hour))

    @Test
    fun `every meal window opens on the hour and excludes its half past end`() {
        for (hour in listOf(8, 10, 12, 16, 18, 20, 22)) {
            val start = startOfHour(hour)
            val lastMinute = at("2026-10-23T%02d:29:59.999+08:00".format(hour))
            val end = at("2026-10-23T%02d:30:00+08:00".format(hour))
            assertEquals(hour, MealSchedule.status(start).current?.hour)
            assertEquals(hour, MealSchedule.status(lastMinute).current?.hour)
            assertNull(MealSchedule.status(end).current)
        }
    }

    @Test
    fun `the last session still points at tomorrow morning`() {
        val state = MealSchedule.status(at("2026-10-23T22:10:00+08:00"))
        assertEquals(22, state.current?.hour)
        assertEquals(at("2026-10-24T08:00:00+08:00"), state.next.startsAtMillis)
        assertFalse(state.finishedToday)
    }

    @Test
    fun `a full hour lead only accepts triggers strictly in the future`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(8), leadMinutes = 60)
        assertEquals(
            at("2026-10-23T07:00:00+08:00"),
            MealSchedule.reminders(preferences, at("2026-10-23T06:59:59+08:00")).single().triggerAtMillis,
        )
        assertEquals(
            at("2026-10-24T07:00:00+08:00"),
            MealSchedule.reminders(preferences, at("2026-10-23T07:00:00+08:00")).single().triggerAtMillis,
        )
    }

    @Test
    fun `readiness reports the first blocking condition`() {
        val selected = MealPreferences(enabled = true, hours = setOf(8), leadMinutes = 0)
        assertEquals(
            MealReadiness.Off,
            MealReminderPolicy.readiness(selected.copy(enabled = false, hours = emptySet()), false, false),
        )
        assertEquals(
            MealReadiness.NoMeals,
            MealReminderPolicy.readiness(selected.copy(hours = emptySet()), false, false),
        )
        assertEquals(MealReadiness.NotificationBlocked, MealReminderPolicy.readiness(selected, false, false))
        assertEquals(MealReadiness.ExactAlarmBlocked, MealReminderPolicy.readiness(selected, true, false))
    }

    @Test
    fun `delivery needs exact alarm access but allows an earlier day's notification`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(12), leadMinutes = 5)
        val reminder = MealSchedule.reminders(preferences, at("2026-10-23T11:00:00+08:00")).single()
        val now = at("2026-10-23T11:55:00+08:00")
        assertTrue(MealReminderPolicy.shouldNotify(reminder, preferences, now, null, true, true))
        assertFalse(MealReminderPolicy.shouldNotify(reminder, preferences, now, null, true, false))
        assertTrue(
            MealReminderPolicy.shouldNotify(
                reminder,
                preferences,
                now,
                at("2026-10-22T12:00:00+08:00"),
                true,
                true,
            ),
        )
    }

    @Test
    fun `breakfast and the last meal retain their next day order after missed lead times`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(8, 22), leadMinutes = 60)
        val plan = MealSchedule.reminders(preferences, at("2026-10-23T21:00:00+08:00"))
        assertEquals(listOf(8, 22), plan.map { it.meal.hour })
        assertEquals(
            listOf(at("2026-10-24T07:00:00+08:00"), at("2026-10-24T21:00:00+08:00")),
            plan.map { it.triggerAtMillis },
        )
    }
}
