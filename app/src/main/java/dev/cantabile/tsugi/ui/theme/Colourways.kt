package dev.cantabile.tsugi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.cantabile.tsugi.data.Colourway

/**
 * One Ink & Paper colourway in one mode, in the design's own terms. [now] is the accent and means
 * only one thing: a bus arriving now.
 */
data class InkPalette(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val badge: Color,
    val onBadge: Color,
    val chip: Color,
    val chipInk: Color,
    val now: Color,
    val nav: Color,
    val navInk: Color,
)

private val Ai = InkPalette(
    paper = Color(0xFFF6F1E7), card = Color(0xFFFFFDF8), ink = Color(0xFF1E2433), muted = Color(0xFF5B6170),
    line = Color(0xFFE2DACB), badge = Color(0xFF2F3E6E), onBadge = Color(0xFFFFFDF8), chip = Color(0xFFE7EAF3),
    chipInk = Color(0xFF2F3E6E), now = Color(0xFFC8402B), nav = Color(0xFF1E2433), navInk = Color(0xFFC9CDD8),
)

private val Matcha = InkPalette(
    paper = Color(0xFFEFF0E3), card = Color(0xFFFCFDF6), ink = Color(0xFF1F2A22), muted = Color(0xFF5A6558),
    line = Color(0xFFDCE0CC), badge = Color(0xFF4E6B3A), onBadge = Color(0xFFFCFDF6), chip = Color(0xFFE3EBD8),
    chipInk = Color(0xFF3E5A2C), now = Color(0xFFB8452F), nav = Color(0xFF1F2A22), navInk = Color(0xFFC7CFC2),
)

private val Sakura = InkPalette(
    paper = Color(0xFFF8EFEF), card = Color(0xFFFFFBFA), ink = Color(0xFF2A2026), muted = Color(0xFF6B5D63),
    line = Color(0xFFEDDCDD), badge = Color(0xFF8E4A5E), onBadge = Color(0xFFFFFBFA), chip = Color(0xFFF3E1E6),
    chipInk = Color(0xFF7A3A4E), now = Color(0xFFC23B5A), nav = Color(0xFF2A2026), navInk = Color(0xFFD6C9CE),
)

private val Fuji = InkPalette(
    paper = Color(0xFFF1EEF6), card = Color(0xFFFCFBFE), ink = Color(0xFF221F33), muted = Color(0xFF625E74),
    line = Color(0xFFE0DAEA), badge = Color(0xFF5B4E8C), onBadge = Color(0xFFFCFBFE), chip = Color(0xFFE6E1F2),
    chipInk = Color(0xFF4A3E7A), now = Color(0xFFC8402B), nav = Color(0xFF221F33), navInk = Color(0xFFCBC7D8),
)

private val Kaki = InkPalette(
    paper = Color(0xFFF8EFE4), card = Color(0xFFFFFAF3), ink = Color(0xFF2B211B), muted = Color(0xFF6C5E52),
    line = Color(0xFFEADBC8), badge = Color(0xFF7A3B1E), onBadge = Color(0xFFFFFAF3), chip = Color(0xFFF3E1D2),
    chipInk = Color(0xFF6A3017), now = Color(0xFFD2672A), nav = Color(0xFF2B211B), navInk = Color(0xFFD8CCC0),
)

/**
 * Night (墨 Sumi): the same paper, card, line and ink for every colourway, with that colourway's
 * badge and accent lightened. Ai's values are the B-Yoru board's; the others keep their own hue at
 * the same lightness.
 */
private fun sumi(badge: Color, chip: Color, chipInk: Color, now: Color) = InkPalette(
    paper = Color(0xFF14171E), card = Color(0xFF1D222C), ink = Color(0xFFECE5D6), muted = Color(0xFF9AA0AE),
    line = Color(0xFF2C3240), badge = badge, onBadge = Color(0xFF14171E), chip = chip, chipInk = chipInk,
    now = now, nav = Color(0xFFECE5D6), navInk = Color(0xFF3A4152),
)

private val AiNight = sumi(Color(0xFF8C9BD6), Color(0xFF262C3A), Color(0xFFC3CBEB), Color(0xFFE0573F))
private val MatchaNight = sumi(Color(0xFFACCE94), Color(0xFF2E3A26), Color(0xFFD3EBC3), Color(0xFFD8543B))
private val SakuraNight = sumi(Color(0xFFCF93A5), Color(0xFF3A262C), Color(0xFFEBC3CF), Color(0xFFD55472))
private val FujiNight = sumi(Color(0xFFA195CD), Color(0xFF2A263A), Color(0xFFCBC3EB), Color(0xFFE0573F))
private val KakiNight = sumi(Color(0xFFD6A38C), Color(0xFF3A2C26), Color(0xFFEBD0C3), Color(0xFFE47E45))

/** The palette for an Ink & Paper colourway, or null for Wallpaper, which uses dynamic colour. */
fun Colourway.palette(dark: Boolean): InkPalette? = when (this) {
    Colourway.Ai -> if (dark) AiNight else Ai
    Colourway.Matcha -> if (dark) MatchaNight else Matcha
    Colourway.Sakura -> if (dark) SakuraNight else Sakura
    Colourway.Fuji -> if (dark) FujiNight else Fuji
    Colourway.Kaki -> if (dark) KakiNight else Kaki
    Colourway.Wallpaper -> null
}

/**
 * Maps a palette onto M3 roles, so screens that only read the colour scheme pick it up as is.
 * Tertiary is the accent; tertiaryContainer is a plain warm tint so notices don't borrow it.
 */
fun InkPalette.toColorScheme(dark: Boolean): ColorScheme {
    val tint = lerp(card, line, 0.45f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = badge,
        onPrimary = onBadge,
        primaryContainer = chip,
        onPrimaryContainer = chipInk,
        inversePrimary = chip,
        secondary = chipInk,
        onSecondary = card,
        secondaryContainer = chip,
        onSecondaryContainer = chipInk,
        tertiary = now,
        onTertiary = if (dark) paper else card,
        tertiaryContainer = tint,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = tint,
        onSurfaceVariant = muted,
        surfaceTint = badge,
        inverseSurface = nav,
        inverseOnSurface = navInk,
        outline = lerp(line, muted, 0.5f),
        outlineVariant = line,
        surfaceBright = card,
        surfaceDim = lerp(paper, line, 0.5f),
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = lerp(card, line, 0.3f),
        surfaceContainerHighest = tint,
    )
}
