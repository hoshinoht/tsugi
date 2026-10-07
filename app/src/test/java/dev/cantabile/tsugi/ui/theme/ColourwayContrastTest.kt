package dev.cantabile.tsugi.ui.theme

import androidx.compose.ui.graphics.Color
import dev.cantabile.tsugi.data.Colourway
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Every Ink & Paper scheme, light and dark, must reach WCAG AA for the text it carries. */
class ColourwayContrastTest {
    private val inkColourways = Colourway.entries - Colourway.Wallpaper

    private fun channel(c: Float) = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(name: String, text: Color, background: Color, minimum: Double) {
        val ratio = contrast(text, background)
        assertTrue("$name is %.2f:1, needs %.1f:1".format(ratio, minimum), ratio >= minimum)
    }

    private fun forEachScheme(check: (String, InkPalette, Boolean) -> Unit) {
        for (c in inkColourways) for (dark in listOf(false, true)) {
            check("${c.label} ${if (dark) "night" else "day"}", c.palette(dark)!!, dark)
        }
    }

    @Test
    fun bodyTextReachesAA() = forEachScheme { name, p, _ ->
        assertContrast("$name ink on paper", p.ink, p.paper, BODY)
        assertContrast("$name ink on card", p.ink, p.card, BODY)
        assertContrast("$name muted on paper", p.muted, p.paper, BODY)
        assertContrast("$name muted on card", p.muted, p.card, BODY)
        assertContrast("$name badge text", p.onBadge, p.badge, BODY)
        assertContrast("$name chip text", p.chipInk, p.chip, BODY)
        assertContrast("$name ink on chip", p.ink, p.chip, BODY)
        assertContrast("$name toolbar text", p.navInk, p.nav, BODY)
        // The toolbar's active slot is a paper pill with ink text.
        assertContrast("$name active slot", p.ink, p.paper, BODY)
    }

    @Test
    fun nowReachesAAForLargeMincho() = forEachScheme { name, p, _ ->
        assertContrast("$name Now on card", p.now, p.card, LARGE)
        assertContrast("$name Now on paper", p.now, p.paper, LARGE)
    }

    @Test
    fun colorSchemeRolesReachAA() = forEachScheme { name, p, dark ->
        val s = p.toColorScheme(dark)
        listOf(
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onSurfaceVariant/surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
            "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
            "onBackground/background" to (s.onBackground to s.background),
            "primary/surfaceContainer" to (s.primary to s.surfaceContainer),
        ).forEach { (role, pair) -> assertContrast("$name $role", pair.first, pair.second, BODY) }
        assertContrast("$name tertiary/surfaceContainer", s.tertiary, s.surfaceContainer, LARGE)
    }

    @Test
    fun wallpaperHasNoPalette() {
        assertNull(Colourway.Wallpaper.palette(dark = false))
        assertNull(Colourway.Wallpaper.palette(dark = true))
    }

    private companion object {
        const val BODY = 4.5
        const val LARGE = 3.0
    }
}
