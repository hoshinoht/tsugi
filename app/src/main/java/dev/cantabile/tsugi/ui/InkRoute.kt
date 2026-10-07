package dev.cantabile.tsugi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Bus
import dev.cantabile.tsugi.ui.theme.Mincho
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sin

/** Ink & Paper's service header: the number on the indigo cookie, then where it's heading in Mincho. */
@Composable
fun InkServiceHeader(serviceNo: String, towards: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.primary), contentAlignment = Alignment.Center) {
            Text(serviceNo, color = colors.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (towards != null) "TOWARDS" else "BUS", fontSize = 12.sp, letterSpacing = 2.sp, color = colors.onSurfaceVariant)
            Text(towards ?: serviceNo, style = MaterialTheme.typography.headlineMedium, fontSize = 30.sp, lineHeight = 34.sp)
        }
    }
}

/** Where a stop sits on the route relative to the bus you're waiting for and the stop you board at. */
enum class RouteMark { Ahead, Passed, Bus, You }

/**
 * One stop on Ink & Paper's route: a dot on a straight brush line, the stop's name and details,
 * and the bus or "You board here" when either is at this stop. Rows sit edge to edge in one card;
 * [index] and [count] say which ends of the line this row draws.
 *
 * The line is drawn per row, from the row's top to its bottom through the dot's centre, so it
 * always passes exactly through the dots. [travelledTo] is the route index the ink reaches (the
 * bus), or -1 when no bus is shown; past it the line is a pale wash.
 */
@Composable
fun InkRouteStop(
    index: Int,
    count: Int,
    name: String,
    meta: String,
    mark: RouteMark,
    travelledTo: Int,
    buses: List<Bus>,
    now: Instant,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface
    val wash = lerp(colors.outlineVariant, colors.outline, 0.35f).copy(alpha = 0.75f)
    val card = colors.surfaceContainer
    val dotSize = if (mark == RouteMark.You) 22.dp else 16.dp
    Box(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .drawBehind {
                    val x = (LINE_START + DOT_COLUMN / 2).toPx()
                    val cy = size.height / 2
                    // Edge from the previous stop into this one.
                    if (index > 0) {
                        val travelled = index <= travelledTo
                        if (travelled) {
                            drawBrush(x, 0f, cy, INK_WIDTH.toPx(), ink, card, seed = index * 2, taper = index == travelledTo)
                        } else {
                            drawBrush(x, 0f, cy, WASH_WIDTH.toPx(), wash, card, seed = index * 2, dryBrush = false)
                        }
                    }
                    // Edge from this stop on to the next.
                    if (index < count - 1) {
                        if (index < travelledTo) {
                            drawBrush(x, cy, size.height, INK_WIDTH.toPx(), ink, card, seed = index * 2 + 1)
                        } else {
                            drawBrush(x, cy, size.height, WASH_WIDTH.toPx(), wash, card, seed = index * 2 + 1, dryBrush = false)
                        }
                    }
                }
                .padding(start = LINE_START, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(DOT_COLUMN), contentAlignment = Alignment.Center) {
                val fill = when (mark) {
                    RouteMark.You -> colors.tertiary
                    RouteMark.Passed, RouteMark.Bus -> ink
                    RouteMark.Ahead -> card
                }
                Box(
                    Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(fill)
                        .border(BorderStroke(3.dp, if (mark == RouteMark.You) colors.tertiary else ink), CircleShape),
                )
            }
            Column(Modifier.weight(1f).alpha(if (mark == RouteMark.Passed) PASSED_ALPHA else 1f)) {
                Text(
                    name,
                    style = if (mark == RouteMark.You) {
                        TextStyle(fontFamily = Mincho, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (buses.isNotEmpty()) {
                BusHere(buses.first(), now)
            } else if (mark == RouteMark.You) {
                Box(
                    Modifier
                        .heightIn(min = 28.dp)
                        .border(1.5.dp, colors.tertiary, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("You board here", color = colors.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The bus at a stop: its minutes in Mincho and an indigo disc with a bus glyph. */
@Composable
private fun BusHere(bus: Bus, now: Instant) {
    val colors = MaterialTheme.colorScheme
    val m = minutesUntil(bus.eta, now)
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Bus here, ${spokenEta(bus, now)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (m < 1) ArrivingNow(fontSize = 22.sp) else InkMinutes(m, fontSize = 22.sp)
        Box(Modifier.size(38.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bus), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

private val LINE_START = 14.dp
private val DOT_COLUMN = 26.dp
private val INK_WIDTH = 10.dp
private val WASH_WIDTH = 6.dp
private const val PASSED_ALPHA = 0.4f

/**
 * A straight brush stroke from [top] to [bottom] centred on [x]: slightly irregular edges, thin
 * dry-brush gaps in the paper colour and, with [taper], a tip that narrows to a point at [bottom].
 * Edges are smooth at both ends so strokes in neighbouring rows join without a seam. [seed] keeps
 * each row's texture stable between frames.
 */
private fun DrawScope.drawBrush(
    x: Float,
    top: Float,
    bottom: Float,
    width: Float,
    color: Color,
    paper: Color,
    seed: Int,
    taper: Boolean = false,
    dryBrush: Boolean = true,
) {
    val length = bottom - top
    if (length <= 0f) return
    val step = 4.dp.toPx()
    val rough = 0.7.dp.toPx()
    val taperLength = minOf(length, 26.dp.toPx())
    val steps = (length / step).toInt().coerceAtLeast(1)
    fun half(t: Float): Float {
        val y = t * length
        val narrowing = if (taper && y > length - taperLength) (length - y) / taperLength else 1f
        return width / 2 * narrowing
    }
    // Zero at both ends so neighbouring strokes meet cleanly.
    fun wobble(i: Int, side: Int): Float {
        if (i == 0 || i == steps) return 0f
        return rough * noise(seed * 131 + i * 7 + side * 3)
    }
    val path = Path().apply {
        moveTo(x - half(0f), top)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            lineTo(x - half(t) + wobble(i, 0), top + t * length)
        }
        for (i in steps downTo 0) {
            val t = i.toFloat() / steps
            lineTo(x + half(t) + wobble(i, 1), top + t * length)
        }
        close()
    }
    drawPath(path, color)
    if (!dryBrush || length < 12.dp.toPx()) return
    // Dry-brush gaps: two hairlines of paper with uneven dashes, offset from the centre.
    val dashes = floatArrayOf(22.dp.toPx(), 5.dp.toPx(), 14.dp.toPx(), 9.dp.toPx())
    val phase = abs(noise(seed)) * 30.dp.toPx()
    val gapEnd = bottom - if (taper) taperLength else 0f
    if (gapEnd <= top) return
    drawLine(
        paper.copy(alpha = 0.55f), Offset(x - width * 0.2f, top), Offset(x - width * 0.2f, gapEnd),
        strokeWidth = 0.8.dp.toPx(), cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(dashes, phase),
    )
    drawLine(
        paper.copy(alpha = 0.45f), Offset(x + width * 0.25f, top), Offset(x + width * 0.25f, gapEnd),
        strokeWidth = 0.7.dp.toPx(), cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(dashes, phase + 11.dp.toPx()),
    )
}

/** Deterministic noise in -1..1, so the texture doesn't shimmer as the screen recomposes. */
private fun noise(n: Int): Float {
    val v = sin(n * 12.9898) * 43758.5453
    return ((v - kotlin.math.floor(v)) * 2 - 1).toFloat()
}
