package ru.antonshkel.subly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Палитра в духе системных цветов Apple: светлая тема — «grouped background» F2F2F7 и белые карточки,
 * тёмная — чистый чёрный (экономит батарею на OLED) и карточки 1C1C1E.
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val cardElevated: Color,
    val fill: Color,
    val separator: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val isDark: Boolean,
)

val LightPalette = Palette(
    background = Color(0xFFF2F2F7), card = Color.White, cardElevated = Color.White,
    fill = Color(0xFFE9E9EE), separator = Color(0xFFE3E3E8),
    label = Color(0xFF1D1D1F), secondary = Color(0xFF6E6E73), tertiary = Color(0xFFAEAEB2),
    accent = Color(0xFF007AFF), green = Color(0xFF34C759), red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500), purple = Color(0xFFAF52DE), isDark = false,
)

val DarkPalette = Palette(
    background = Color.Black, card = Color(0xFF1C1C1E), cardElevated = Color(0xFF2C2C2E),
    fill = Color(0xFF2C2C2E), separator = Color(0xFF38383A),
    label = Color(0xFFF5F5F7), secondary = Color(0xFF98989D), tertiary = Color(0xFF636366),
    accent = Color(0xFF0A84FF), green = Color(0xFF30D158), red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A), purple = Color(0xFFBF5AF2), isDark = true,
)

/** Шкала текста по мотивам iOS: Large Title 34, Title 28/22, Headline 17, Body 17, Footnote 13. */
@Immutable
data class Type(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val hero: TextStyle,
)

private val base = TextStyle(fontFamily = FontFamily.Default)

val SublyType = Type(
    largeTitle = base.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    title1 = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
    title2 = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    title3 = base.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    headline = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    body = base.copy(fontSize = 17.sp, lineHeight = 22.sp),
    callout = base.copy(fontSize = 16.sp, lineHeight = 21.sp),
    subhead = base.copy(fontSize = 15.sp, lineHeight = 20.sp),
    footnote = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    hero = base.copy(fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalType = staticCompositionLocalOf { SublyType }

object Subly {
    val colors: Palette @Composable get() = LocalPalette.current
    val type: Type @Composable get() = LocalType.current
}

@Composable
fun SublyTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, background = p.background, surface = p.card, onSurface = p.label,
        onBackground = p.label, surfaceVariant = p.fill, onSurfaceVariant = p.secondary, error = p.red,
        surfaceContainerHigh = p.cardElevated, surfaceContainerHighest = p.cardElevated,
    ) else lightColorScheme(
        primary = p.accent, background = p.background, surface = p.card, onSurface = p.label,
        onBackground = p.label, surfaceVariant = p.fill, onSurfaceVariant = p.secondary, error = p.red,
        surfaceContainerHigh = p.card, surfaceContainerHighest = p.card,
    )
    CompositionLocalProvider(LocalPalette provides p, LocalType provides SublyType) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF8E8E93))
