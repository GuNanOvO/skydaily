package com.nankki.skydaily.meals

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealScheduleTest {
    private fun at(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()

    @Test
    fun `before breakfast the next meal is today's eight o'clock`() {
        val state = MealSchedule.status(at("2026-10-23T07:59:59+08:00"))
        assertNull(state.current)
        assertEquals(at("2026-10-23T08:00:00+08:00"), state.next.startsAtMillis)
        assertFalse(state.finishedToday)
    }

    @Test
    fun `meal opens on the hour and closes exactly at half past`() {
        val open = MealSchedule.status(at("2026-10-23T16:00:00+08:00"))
        assertEquals(16, open.current?.hour)
        assertEquals(at("2026-10-23T16:30:00+08:00"), open.current?.endsAtMillis)
        assertEquals(16, MealSchedule.status(at("2026-10-23T16:29:59.999+08:00")).current?.hour)
        val closed = MealSchedule.status(at("2026-10-23T16:30:00+08:00"))
        assertNull(closed.current)
        assertEquals(18, closed.next.hour)
    }

    @Test
    fun `the lunch gap has no invented fourteen o'clock session`() {
        val state = MealSchedule.status(at("2026-10-23T14:00:00+08:00"))
        assertNull(state.current)
        assertEquals(at("2026-10-23T16:00:00+08:00"), state.next.startsAtMillis)
    }

    @Test
    fun `last session rolls into tomorrow across the year boundary`() {
        val state = MealSchedule.status(at("2026-12-31T22:30:00+08:00"))
        assertNull(state.current)
        assertTrue(state.finishedToday)
        assertEquals(at("2027-01-01T08:00:00+08:00"), state.next.startsAtMillis)
        assertFalse(MealSchedule.status(at("2027-01-01T00:00:00+08:00")).finishedToday)
    }

    @Test
    fun `UTC timestamps are interpreted on the Beijing meal calendar`() {
        val state = MealSchedule.status(at("2026-10-23T00:10:00Z"))
        assertEquals(8, state.current?.hour)
        assertEquals(at("2026-10-23T00:30:00Z"), state.current?.endsAtMillis)
    }

    @Test
    fun `selected reminders are future only and ordered by actual delivery time`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(18, 20), leadMinutes = 5)
        val plan = MealSchedule.reminders(preferences, at("2026-10-23T19:54:00+08:00"))
        assertEquals(listOf(20, 18), plan.map { it.meal.hour })
        assertEquals(
            listOf(at("2026-10-23T19:55:00+08:00"), at("2026-10-24T17:55:00+08:00")),
            plan.map { it.triggerAtMillis },
        )
        val changed = MealSchedule.reminders(preferences.copy(leadMinutes = 10), at("2026-10-23T19:54:00+08:00"))
        assertEquals(at("2026-10-24T17:50:00+08:00"), changed.first().triggerAtMillis)
    }

    @Test
    fun `at-start reminder never immediately backfills an elapsed occurrence`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(8), leadMinutes = 0)
        val plan = MealSchedule.reminders(preferences, at("2026-10-23T08:00:00+08:00"))
        assertEquals(at("2026-10-24T08:00:00+08:00"), plan.single().triggerAtMillis)
        assertEquals(emptyList<MealReminder>(), MealSchedule.reminders(preferences.copy(enabled = false), at("2026-10-23T07:00:00+08:00")))
    }

    @Test
    fun `saved intent is not reported ready without both delivery permissions`() {
        val selected = MealPreferences(enabled = true, hours = setOf(18), leadMinutes = 5)
        assertEquals(MealReadiness.Off, MealReminderPolicy.readiness(selected.copy(enabled = false), true, true))
        assertEquals(MealReadiness.NoMeals, MealReminderPolicy.readiness(selected.copy(hours = emptySet()), true, true))
        assertEquals(MealReadiness.NotificationBlocked, MealReminderPolicy.readiness(selected, false, true))
        assertEquals(MealReadiness.ExactAlarmBlocked, MealReminderPolicy.readiness(selected, true, false))
        assertEquals(MealReadiness.Ready, MealReminderPolicy.readiness(selected, true, true))
    }

    @Test
    fun `late useful deliveries are allowed but early expired or duplicate ones are not`() {
        val preferences = MealPreferences(enabled = true, hours = setOf(18), leadMinutes = 5)
        val reminder = MealSchedule.reminders(preferences, at("2026-10-23T17:00:00+08:00")).single()
        fun allowed(time: String, last: Long? = null, value: MealPreferences = preferences): Boolean =
            MealReminderPolicy.shouldNotify(reminder, value, at(time), last, true, true)
        assertFalse(allowed("2026-10-23T17:54:59+08:00"))
        assertTrue(allowed("2026-10-23T17:55:00+08:00"))
        assertTrue(allowed("2026-10-23T18:29:59+08:00"))
        assertFalse(allowed("2026-10-23T18:30:00+08:00"))
        assertFalse(allowed("2026-10-23T17:55:00+08:00", last = reminder.meal.startsAtMillis))
        assertFalse(allowed("2026-10-23T17:55:00+08:00", value = preferences.copy(leadMinutes = 10)))
        assertFalse(allowed("2026-10-23T17:55:00+08:00", value = preferences.copy(hours = setOf(20))))
        assertFalse(MealReminderPolicy.shouldNotify(reminder, preferences, at("2026-10-23T17:55:00+08:00"), null, false, true))
    }
}
