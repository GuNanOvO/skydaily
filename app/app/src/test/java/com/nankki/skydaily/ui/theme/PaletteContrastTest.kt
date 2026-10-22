package com.nankki.skydaily.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 移动端可读性守门：正文、次级文字和主题色在纸面/便签上达到 WCAG AA。 */
class PaletteContrastTest {
    @Test
    fun `light palette meets AA for body and secondary text`() {
        assertPalette(LightPalette)
    }

    @Test
    fun `dark palette meets AA for body and secondary text`() {
        assertPalette(DarkPalette)
    }

    private fun assertPalette(palette: PaperPalette) {
        assertAtLeast(4.5, palette.ink, palette.paper, "ink on paper")
        assertAtLeast(4.5, palette.muted, palette.paper, "muted on paper")
        assertAtLeast(4.5, palette.tabInk, palette.tabColors.first(), "tab ink")
        assertAtLeast(4.5, palette.tabInk, palette.tabColors.last(), "tab ink")
        palette.noteColors.forEachIndexed { index, note ->
            assertAtLeast(4.5, palette.ink, note, "ink on note $index")
            assertAtLeast(4.5, palette.muted, note, "muted on note $index")
        }
        assertAtLeast(3.0, palette.accent, palette.noteColors.first(), "accent on note")
    }

    private fun assertAtLeast(minRatio: Double, foreground: Color, background: Color, label: String) {
        val ratio = contrast(foreground, background)
        assertTrue("$label contrast was $ratio, expected >= $minRatio", ratio >= minRatio)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
