package com.nankki.skydaily.ui.journal

import com.nankki.skydaily.data.Calendar
import com.nankki.skydaily.data.DailyData
import com.nankki.skydaily.data.Task
import com.nankki.skydaily.data.TaskDetail
import com.nankki.skydaily.data.TaskVariant
import com.nankki.skydaily.data.Weather
import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredImagesTest {
    @Test
    fun `prefetch prioritizes linked task variants and deduplicates repeated media`() {
        val task = "/history/media/task.webp"
        val variant = "/history/media/variant.webp"
        val weather = "/history/media/weather.webp"
        val data = DailyData(
            tasks = listOf(Task(1, "任务", "guide"), Task(2, "同一指南", "guide")),
            taskDetails = listOf(
                TaskDetail(keyword = "guide", images = listOf(task), variants = listOf(TaskVariant(images = listOf(variant, task)))),
                TaskDetail(keyword = "unrelated", images = listOf("/history/media/not-needed.webp")),
            ),
            weather = Weather(images = listOf(weather)),
            calendar = Calendar(images = listOf(weather)),
        )
        assertEquals(
            listOf(
                "https://example.test$task",
                "https://example.test$variant",
                "https://example.test/history/thumbs/weather.webp",
                "https://example.test$weather",
            ),
            deferredImageUrls(data, "https://example.test"),
        )
    }
}
