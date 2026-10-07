package dev.cantabile.tsugi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import dev.cantabile.tsugi.data.Colourway

/**
 * The component style. The colourway decides it: the traditional colourways are Ink & Paper,
 * Wallpaper keeps M3 Expressive. Components branch on it; screens keep their logic.
 */
enum class TsugiStyle { Ink, Expressive }

val LocalTsugiStyle = staticCompositionLocalOf { TsugiStyle.Expressive }

object TsugiTheme {
    val style: TsugiStyle
        @Composable @ReadOnlyComposable get() = LocalTsugiStyle.current

    val isInk: Boolean
        @Composable @ReadOnlyComposable get() = LocalTsugiStyle.current == TsugiStyle.Ink
}

/** minSdk 31 (Android 12), so dynamic (wallpaper) colour is always available — no static fallback. */
@Composable
fun TsugiTheme(colourway: Colourway, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val palette = colourway.palette(dark)
    val colorScheme = palette?.toColorScheme(dark)
        ?: if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    CompositionLocalProvider(LocalTsugiStyle provides if (palette != null) TsugiStyle.Ink else TsugiStyle.Expressive) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = if (palette != null) InkTypography else MaterialTheme.typography,
            content = content,
        )
    }
}
