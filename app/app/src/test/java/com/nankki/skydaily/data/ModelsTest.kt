package com.nankki.skydaily.data

import com.nankki.skydaily.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun `parses the published journal fixture`() {
        val envelope = JournalJson.parseEnvelope(fixture("sample-daily.json"))
        assertEquals("2026-10-21", envelope.date)
        assertEquals(4, envelope.data.tasks.size)
        assertEquals("在云野的锦鲤池旁冥想", envelope.data.tasks.first().text)
        assertNotNull(envelope.data.weather)
        assertEquals(5, envelope.data.weather?.images?.size)
        assertTrue(envelope.data.candles.isNotEmpty())
        val detail = envelope.data.taskDetails.first { it.keyword == "苦修行者任务" }
        assertEquals(2, detail.variants.size)
        assertEquals("在云峰找到苦修行者", detail.variants.first().title)
    }

    @Test
    fun `parses the notice archive and selects the current version`() {
        val archive = JournalJson.parseNotices(fixture("sample-notices.json"))
        val notice = archive.currentVersion()
        assertNotNull(notice)
        assertEquals("9月24日版本更新公告", notice?.title)
        assertTrue(notice?.message?.isNotBlank() == true)
    }

    @Test
    fun `media paths are made absolute and thumbs swap the directory`() {
        val path = "/history/media/abc123.webp"
        assertEquals("https://skydaily.nankki.com/history/media/abc123.webp", Media.absolute("https://skydaily.nankki.com", path))
        assertEquals("/history/thumbs/abc123.webp", Media.thumb(path))
        assertEquals("/history/media/abc123.png", Media.thumb("/history/media/abc123.png"))
        assertEquals("https://example.com/x.webp", Media.absolute("https://skydaily.nankki.com", "https://example.com/x.webp"))
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """{"schema_version":1,"date":"2026-10-21","data":{"tasks":[],"future":true},"extra":1}"""
        val envelope = JournalJson.parseEnvelope(raw)
        assertEquals(0, envelope.data.tasks.size)
        assertNull(envelope.data.weather)
    }
}
