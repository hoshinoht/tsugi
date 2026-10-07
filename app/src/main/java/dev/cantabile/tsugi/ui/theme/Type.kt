package dev.cantabile.tsugi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.cantabile.tsugi.R

/**
 * Zen Old Mincho, subset to Latin and the few kanji the UI uses (see scripts/subset_mincho.sh).
 * The display face for Ink & Paper: titles, stop names, minute numerals and "Now".
 */
val Mincho = FontFamily(
    Font(R.font.zen_old_mincho_semibold, FontWeight.SemiBold),
    Font(R.font.zen_old_mincho_black, FontWeight.Black),
)

private fun TextStyle.mincho() = copy(fontFamily = Mincho, fontWeight = FontWeight.Black)

/** Display, headline and large title styles in Mincho 900; everything else keeps the body font. */
val InkTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.mincho(),
        displayMedium = displayMedium.mincho(),
        displaySmall = displaySmall.mincho(),
        headlineLarge = headlineLarge.mincho(),
        headlineMedium = headlineMedium.mincho(),
        headlineSmall = headlineSmall.mincho(),
        titleLarge = titleLarge.mincho(),
    )
}
