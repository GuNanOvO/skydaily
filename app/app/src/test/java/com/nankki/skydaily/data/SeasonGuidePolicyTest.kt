package com.nankki.skydaily.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SeasonGuidePolicyTest {
    private val policy = requireNotNull(SeasonGuidePolicy.parse(POLICY_JSON))

    private val detailUrl = "https://h5.ds.163.com/v1/$DETAIL_HASH/"
    private val fallbackUrl = "https://h5.ds.163.com/v1/$FALLBACK_HASH/"

    @Test
    fun `official link is normalized to a single trailing slash`() {
        assertEquals(detailUrl, policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertEquals(detailUrl, policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH"))
        assertEquals(detailUrl, policy.normalize("  https://h5.ds.163.com/v1/$DETAIL_HASH/  "))
        assertEquals(detailUrl, policy.normalize("HTTPS://H5.DS.163.COM/v1/$DETAIL_HASH/"))
        assertEquals(detailUrl, policy.normalize("https://h5.ds.163.com:443/v1/$DETAIL_HASH/"))
    }

    @Test
    fun `query and fragment are stripped like the website`() {
        assertEquals(detailUrl, policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH/?from=share#top"))
        assertEquals(detailUrl, policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH/#/inner/route"))
    }

    @Test
    fun `rejects origins outside the policy`() {
        assertNull(policy.normalize("http://h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://evil.example/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com.evil.example/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://sub.h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com:8443/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("//h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(policy.normalize("about:blank"))
        assertNull(policy.normalize(""))
        assertNull(policy.normalize(null))
    }

    @Test
    fun `rejects credentials in the authority`() {
        assertNull(policy.normalize("https://user:pass@h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://user@h5.ds.163.com/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com@evil.example/v1/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://:token@h5.ds.163.com/v1/$DETAIL_HASH/"))
    }

    @Test
    fun `rejects paths outside the official pattern`() {
        assertNull(policy.normalize("https://h5.ds.163.com/"))
        assertNull(policy.normalize("https://h5.ds.163.com/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v/$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v1/0123456789abcdef0123456/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v1/${DETAIL_HASH.uppercase()}/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v1/../$DETAIL_HASH/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH/extra/"))
        assertNull(policy.normalize("https://h5.ds.163.com/v1/$DETAIL_HASH%2F/"))
        assertNull(policy.normalize("https://h5.ds.163.com//v1/$DETAIL_HASH/"))
    }

    @Test
    fun `top level navigation only allows policy urls`() {
        assertTrue(policy.isAllowedTopLevel(detailUrl))
        assertTrue(policy.isAllowedTopLevel("$detailUrl?from=nav"))
        assertFalse(policy.isAllowedTopLevel("https://evil.example/v1/$DETAIL_HASH/"))
        assertFalse(policy.isAllowedTopLevel("https://h5.ds.163.com/v1/$DETAIL_HASH/extra"))
        assertFalse(policy.isAllowedTopLevel(null))
    }

    @Test
    fun `valid taskDetails link wins over the fallback`() {
        val data = DailyData(
            taskDetails = listOf(
                TaskDetail(keyword = KEYWORD, links = listOf("$detailUrl?from=task")),
            ),
        )

        assertEquals(listOf(detailUrl), policy.resolve(data))
        assertEquals(fallbackUrl, policy.fallback)
    }

    @Test
    fun `candles links are never used`() {
        val data = DailyData(
            candles = listOf(
                TaskDetail(keyword = KEYWORD, links = listOf("https://h5.ds.163.com/v1/$OTHER_HASH/")),
            ),
        )

        assertEquals(listOf(fallbackUrl), policy.resolve(data))
    }

    @Test
    fun `deduplicates valid links while keeping first seen order`() {
        val data = DailyData(
            taskDetails = listOf(
                TaskDetail(
                    keyword = KEYWORD,
                    links = listOf(
                        detailUrl,
                        "https://h5.ds.163.com/v1/$OTHER_HASH/",
                        "$detailUrl?dup=1",
                        "https://evil.example/v1/$DETAIL_HASH/",
                        "https://h5.ds.163.com/v1/$OTHER_HASH",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(detailUrl, "https://h5.ds.163.com/v1/$OTHER_HASH/"),
            policy.resolve(data),
        )
    }

    @Test
    fun `falls back when no link survives validation`() {
        val data = DailyData(
            taskDetails = listOf(
                TaskDetail(
                    keyword = KEYWORD,
                    links = listOf(
                        "https://evil.example/v1/$DETAIL_HASH/",
                        "https://h5.ds.163.com:8443/v1/$DETAIL_HASH/",
                        "https://user@h5.ds.163.com/v1/$DETAIL_HASH/",
                    ),
                ),
                TaskDetail(keyword = "其他任务", links = listOf("https://h5.ds.163.com/v1/$OTHER_HASH/")),
            ),
        )

        assertEquals(listOf(fallbackUrl), policy.resolve(data))
    }

    @Test
    fun `duplicate keywords follow the renderer and use the last detail`() {
        val data = DailyData(
            taskDetails = listOf(
                TaskDetail(keyword = KEYWORD, links = listOf("https://h5.ds.163.com/v1/$OTHER_HASH/")),
                TaskDetail(keyword = KEYWORD, links = listOf(detailUrl)),
            ),
        )

        assertEquals(listOf(detailUrl), policy.resolve(data))
    }

    @Test
    fun `policy rejects malformed sections`() {
        assertNull(SeasonGuidePolicy.parse("""{"noticeLinks":{"hosts":["sky.163.com"]}}"""))
        assertNull(SeasonGuidePolicy.parse("""{"seasonGuide":{"keyword":"","origin":"https://h5.ds.163.com","pathPattern":"^/v1/[a-f0-9]{24}/?$","fallback":""}}"""))
        assertNull(SeasonGuidePolicy.parse("""{"seasonGuide":{"keyword":"k","origin":"http://h5.ds.163.com","pathPattern":"^/v1/[a-f0-9]{24}/?$","fallback":""}}"""))
        assertNull(SeasonGuidePolicy.parse("""{"seasonGuide":{"keyword":"k","origin":"https://h5.ds.163.com","pathPattern":"^/v1/[a-f0-9]{24}/?$","fallback":""}}""".replace("^/v1/[a-f0-9]{24}/?$", "^(unclosed")))
        assertNull(SeasonGuidePolicy.parse("not json"))
    }

    @Test
    fun `policy tolerates invalid fallback by exposing no fallback`() {
        val parsed = SeasonGuidePolicy.parse(
            """{"seasonGuide":{"keyword":"季节蜡烛探索指南","origin":"https://h5.ds.163.com","pathPattern":"^/v[0-9]+/[a-f0-9]{24}/?$","fallback":"https://evil.example/v1/$DETAIL_HASH/"}}""",
        )

        assertNotNull(parsed)
        assertNull(parsed!!.fallback)
        assertTrue(parsed.resolve(DailyData()).isEmpty())
    }

    @Test
    fun `shared repository policy produces the same guide rules`() {
        val policyFile = listOf(
            File("../../src/link-policy.json"),
            File("../src/link-policy.json"),
            File("src/link-policy.json"),
        ).firstOrNull { it.isFile }
        assumeTrue("shared src/link-policy.json is available", policyFile != null)

        val shared = requireNotNull(SeasonGuidePolicy.parse(policyFile!!.readText()))

        assertEquals(KEYWORD, shared.keyword)
        assertEquals("https://h5.ds.163.com", shared.origin)
        assertEquals(fallbackUrl, shared.fallback)
        assertEquals(detailUrl, shared.normalize("$detailUrl?shared=1#frag"))
        assertEquals(listOf(fallbackUrl), shared.resolve(DailyData()))
    }

    private companion object {
        const val KEYWORD = "季节蜡烛探索指南"
        const val DETAIL_HASH = "0123456789abcdef01234567"
        const val OTHER_HASH = "fedcba9876543210fedcba98"
        const val FALLBACK_HASH = "6a54aac92a493300338e56aa"

        val POLICY_JSON = """
            {
              "seasonGuide": {
                "keyword": "$KEYWORD",
                "origin": "https://h5.ds.163.com",
                "pathPattern": "^/v[0-9]+/[a-f0-9]{24}/?$",
                "fallback": "https://h5.ds.163.com/v1/$FALLBACK_HASH/"
              },
              "noticeLinks": { "hosts": ["sky.163.com"] }
            }
        """.trimIndent()
    }
}
