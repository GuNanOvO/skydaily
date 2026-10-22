package com.nankki.skydaily.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalFormatTest {
    @Test
    fun `progress and editability follow the data date`() {
        assertEquals("2 / 4 已完成", JournalFormat.progress(2, 4))
        assertTrue(JournalFormat.canEdit("2026-10-21", "2026-10-21"))
        assertFalse(JournalFormat.canEdit("2026-10-20", "2026-10-21"))
        assertEquals("数据日期 2026-10-20", JournalFormat.staleLabel("2026-10-20", "2026-10-21"))
        assertNull(JournalFormat.staleLabel("2026-10-21", "2026-10-21"))
    }

    @Test
    fun `updated label marks offline and stale states`() {
        val epoch = java.time.Instant.parse("2026-10-21T16:11:00Z").toEpochMilli()
        val label = JournalFormat.updatedLabel(epoch, offline = true, stale = true)
        assertTrue(label.contains("更新于"))
        assertTrue(label.contains("离线缓存"))
        assertTrue(label.contains("缓存"))
        assertEquals("等待更新", JournalFormat.updatedLabel(0, offline = false, stale = false))
    }

    @Test
    fun `raw transport errors are humanized`() {
        assertEquals(
            "网络不可用，显示本地缓存",
            JournalFormat.humanizeReason("failed to connect to skydaily.nankki.com/1.2.3.4:443"),
        )
        assertEquals("数据解析失败", JournalFormat.humanizeReason("数据解析失败"))
    }

    @Test
    fun `notice lines split headings items and bullets`() {
        val lines = noticeLines("本次更新内容：\n\n【秋宵节】\n1、活动开启\n· 备注")
        assertEquals(
            listOf(
                NoticeLine("本次更新内容：", NoticeLineKind.Paragraph),
                NoticeLine("【秋宵节】", NoticeLineKind.Heading),
                NoticeLine("1、活动开启", NoticeLineKind.Item),
                NoticeLine("· 备注", NoticeLineKind.Bullet),
            ),
            lines,
        )
    }

    @Test
    fun `notice preview collapses whitespace and truncates`() {
        assertEquals("第一段 第二段", JournalFormat.noticePreview("第一段\n\n第二段"))
        val long = "字".repeat(120)
        assertTrue(JournalFormat.noticePreview(long).endsWith("…"))
    }
}
