package com.nankki.skydaily.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nankki.skydaily.core.ThemeMode
import com.nankki.skydaily.core.ThemeResolver
import kotlinx.coroutines.delay

@Immutable
data class PaperPalette(
    val dark: Boolean,
    val page: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val margin: Color,
    val outline: Color,
    val shadow: Color,
    val shadowStrong: Color,
    val tape: Color,
    val tapeEdge: Color,
    val noteColors: List<Color>,
    val noteAccents: List<Color>,
    val noteTapes: List<Color>,
    val tabColors: List<Color>,
    val tabInk: Color,
    val tabMuted: Color,
    val noticePaper: Color,
    val photoPaper: Color,
    val accent: Color,
    val scrim: Color,
    val bindingOuter: Color,
    val bindingHole: Color,
    val checkInk: Color,
)

val LightPalette = PaperPalette(
    dark = false,
    page = Color(0xFFF4F4F4),
    paper = Color(0xFFFDFCF9),
    ink = Color(0xFF353539),
    muted = Color(0xFF6B6970),
    rule = Color(0xFFD8DFE8),
    margin = Color(0x8FCF9999),
    outline = Color(0x4DC3BEC7),
    shadow = Color(0x24353539),
    shadowStrong = Color(0x2B353539),
    tape = Color(0x80ECD69B),
    tapeEdge = Color(0x60C1B5A6),
    noteColors = listOf(
        Color(0xFFFFF9E9),
        Color(0xFFFFF0F4),
        Color(0xFFEEF9F6),
        Color(0xFFEFF4FF),
    ),
    noteAccents = listOf(
        Color(0xFFC9A94F),
        Color(0xFFC77A97),
        Color(0xFF6FB8A4),
        Color(0xFF7FA0CF),
    ),
    noteTapes = List(4) { Color(0x80ECD69B) },
    tabColors = listOf(
        Color(0xFFF4E7BD),
        Color(0xFFF3DCE3),
        Color(0xFFDAEEE8),
        Color(0xFFDFE7F9),
        Color(0xFFE9E6F4),
    ),
    tabInk = Color(0xFF353539),
    tabMuted = Color(0xFF6B6970),
    noticePaper = Color(0xFFFFFDF6),
    photoPaper = Color(0xFFFFFEFC),
    accent = Color(0xFF6F5B80),
    scrim = Color(0x99000000),
    bindingOuter = Color(0xFFDEDBDF),
    bindingHole = Color(0xFFBDBAC0),
    checkInk = Color(0xFF353539),
)

val DarkPalette = PaperPalette(
    dark = true,
    page = Color(0xFF17191F),
    paper = Color(0xFF23252E),
    ink = Color(0xFFE7E2EA),
    muted = Color(0xFFBDB7C8),
    rule = Color(0xFF3E4351),
    margin = Color(0x90B48087),
    outline = Color(0x80575260),
    shadow = Color(0x60000000),
    shadowStrong = Color(0x78000000),
    tape = Color(0x80A08B4D),
    tapeEdge = Color(0x70C2AF7A),
    noteColors = listOf(
        Color(0xFF2B2D36),
        Color(0xFF2B2D36),
        Color(0xFF2B2D36),
        Color(0xFF2B2D36),
    ),
    noteAccents = listOf(
        Color(0xFFD9BD63),
        Color(0xFFD991AB),
        Color(0xFF8FCBB9),
        Color(0xFF9DB8DE),
    ),
    noteTapes = listOf(
        Color(0x99F4E7BD),
        Color(0x99F3DCE3),
        Color(0x99DAEEE8),
        Color(0x99DFE7F9),
    ),
    tabColors = listOf(
        Color(0xFFF4E7BD),
        Color(0xFFF3DCE3),
        Color(0xFFDAEEE8),
        Color(0xFFDFE7F9),
        Color(0xFFE9E6F4),
    ),
    tabInk = Color(0xFF353539),
    tabMuted = Color(0xFF6B6970),
    noticePaper = Color(0xFF2A2C35),
    photoPaper = Color(0xFF2C2E37),
    accent = Color(0xFFB9A6D0),
    scrim = Color(0xB3000000),
    bindingOuter = Color(0xFF3E4351),
    bindingHole = Color(0xFF15171E),
    checkInk = Color(0xFFE7E2EA),
)

val LocalPaperPalette = staticCompositionLocalOf { LightPalette }

val PaperTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 32.sp,
        letterSpacing = 1.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun PaperTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = palette.accent,
        background = palette.page,
        surface = palette.paper,
        onBackground = palette.ink,
        onSurface = palette.ink,
        secondary = palette.muted,
    ) else lightColorScheme(
        primary = palette.accent,
        background = palette.page,
        surface = palette.paper,
        onBackground = palette.ink,
        onSurface = palette.ink,
        secondary = palette.muted,
    )
    CompositionLocalProvider(LocalPaperPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PaperTypography,
            content = content,
        )
    }
}

/** 自动主题需要在 19:00 / 07:00 边界自行换色，每分钟复核一次。 */
@Composable
fun rememberDarkMode(mode: ThemeMode): Boolean {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mode) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    return ThemeResolver.isDark(mode, now)
}
