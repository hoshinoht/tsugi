package dev.cantabile.tsugi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Colourway
import dev.cantabile.tsugi.ui.theme.InkPalette
import dev.cantabile.tsugi.ui.theme.Mincho
import dev.cantabile.tsugi.ui.theme.palette

/**
 * Settings › Colours: a grouped gallery. 伝統色 Traditional holds the Ink & Paper colourways;
 * Dynamic holds Wallpaper, the M3 Expressive look in your wallpaper's colours. Each tile previews the
 * Next up card in that colourway and the current light or dark mode.
 */
@Composable
fun ColourwayPicker(selected: Colourway, onSelect: (Colourway) -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GroupLabel("伝統色", "Traditional")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Colourway.entries.filter { it != Colourway.Wallpaper }.forEach { c ->
                ColourwayTile(c.kanji, c.label, c.palette(dark)!!, selected = c == selected, onClick = { onSelect(c) })
            }
        }
        GroupLabel(null, "Dynamic")
        val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        ColourwayTile(null, Colourway.Wallpaper.label, dynamic.previewPalette(), selected = selected == Colourway.Wallpaper, onClick = { onSelect(Colourway.Wallpaper) })
    }
}

@Composable
private fun GroupLabel(kanji: String?, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (kanji != null) Text(kanji, fontFamily = Mincho, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small phone-shaped preview of the Next up card, with the colourway's name underneath. */
@Composable
private fun ColourwayTile(kanji: String?, label: String, p: InkPalette, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Column(
                Modifier
                    .size(width = 80.dp, height = 128.dp)
                    .border(
                        if (selected) BorderStroke(2.dp, colors.onSurface) else BorderStroke(1.dp, colors.outlineVariant),
                        RoundedCornerShape(16.dp),
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.paper)
                    .padding(horizontal = 7.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Next up: the badge cookie and the minutes.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, p.line, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.card)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(24.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(p.badge), contentAlignment = Alignment.Center) {
                            Text("32", color = p.onBadge, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("3", color = p.ink, fontFamily = Mincho, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    Box(Modifier.size(width = 36.dp, height = 3.dp).background(p.muted.copy(alpha = 0.5f)))
                }
                // A row with a bus arriving now.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, p.line, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.card)
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(width = 16.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).background(p.chip))
                    Box(Modifier.weight(1f))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(p.now))
                    Text("Now", color = p.now, fontFamily = Mincho, fontWeight = FontWeight.Black, fontSize = 9.sp)
                }
                Box(Modifier.weight(1f))
                // The floating toolbar.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 48.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(p.nav)
                        .padding(2.dp),
                ) {
                    Box(Modifier.size(width = 16.dp, height = 8.dp).clip(CircleShape).background(p.paper))
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(colors.onSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.surface, modifier = Modifier.size(14.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (kanji != null) Text(kanji, fontFamily = Mincho, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** The dynamic scheme in the preview's terms, so the Wallpaper tile shows your wallpaper's colours. */
private fun ColorScheme.previewPalette() = InkPalette(
    paper = surface,
    card = surfaceContainer,
    ink = onSurface,
    muted = onSurfaceVariant,
    line = outlineVariant,
    badge = primary,
    onBadge = onPrimary,
    chip = secondaryContainer,
    chipInk = onSecondaryContainer,
    now = primary,
    nav = primaryContainer,
    navInk = onPrimaryContainer,
)
