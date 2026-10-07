package dev.cantabile.tsugi.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Bus
import dev.cantabile.tsugi.data.Load
import dev.cantabile.tsugi.data.SINGAPORE
import dev.cantabile.tsugi.data.ServiceArrivals
import dev.cantabile.tsugi.data.timeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant

const val REFRESH_MS = 20_000L // DataMall's BusArrival update frequency

/** Polls arrivals for [codes] every 20 s, only while the screen is started. */
@Composable
fun PollArrivals(vm: AppViewModel, codes: List<String>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(codes) {
        if (codes.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refresh(codes)
                delay(REFRESH_MS)
            }
        }
    }
}

@Composable
fun rememberNow(periodMs: Long = 5_000): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(periodMs)
            value = Instant.now()
        }
    }
    return now
}

/** Whole minutes until [eta]; anything under a minute counts as arriving. */
fun minutesUntil(eta: Instant, now: Instant): Long = Duration.between(now, eta).toMinutes()

fun etaLabel(bus: Bus, now: Instant): String {
    val m = minutesUntil(bus.eta, now)
    return if (m < 1) "Arr" else m.toString()
}

/** How long before the last bus to start warning about it. */
private val LAST_BUS_WARNING: Duration = Duration.ofMinutes(45)

/** "Last bus 11:42 pm" while tonight's last bus is under 45 minutes away, else null. */
fun lastBusNotice(service: ServiceArrivals, now: Instant): String? {
    val last = service.lastBus ?: return null
    val until = Duration.between(now, last)
    if (until.isNegative || until > LAST_BUS_WARNING) return null
    return "Last bus ${timeLabel(last.atZone(SINGAPORE).toLocalTime())}"
}

/** Corner radii for a connected row of tiles: big outer corners, small inner ones. */
fun rowShape(index: Int, count: Int, outer: Dp = 16.dp, inner: Dp = 6.dp): Shape = when {
    count == 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(outer, inner, inner, outer)
    index == count - 1 -> RoundedCornerShape(inner, outer, outer, inner)
    else -> RoundedCornerShape(inner)
}

@Composable
fun ServiceBadge(
    serviceNo: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
        color = if (active) colors.secondaryContainer else colors.surfaceContainerHighest,
        contentColor = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = if (compact) 44.dp else 56.dp, minHeight = if (compact) 30.dp else 44.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                serviceNo,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Crowding as 3/2/1 bars coloured green, amber or red. The bar count carries the meaning on its
 * own, so it still reads for colour-blind users. [contentColor] is the text colour on the
 * surface behind the bars: it picks the light or dark palette and tints the empty bars.
 */
@Composable
fun LoadBars(load: Load, contentColor: Color, modifier: Modifier = Modifier) {
    if (load == Load.Unknown) return
    Row(
        modifier = modifier.semantics { contentDescription = load.label },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(8, 11, 14).forEachIndexed { i, h ->
            Box(
                Modifier
                    .size(width = 4.dp, height = h.dp)
                    .background(
                        if (i < load.bars) loadColor(load, onDark = contentColor.luminance() > 0.5f) else contentColor.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** Fixed (not themed) crowding colours, like MRT line colours; lighter variants for dark surfaces. */
fun loadColor(load: Load, onDark: Boolean): Color = when (load) {
    Load.Seats -> if (onDark) Color(0xFF6FD08C) else Color(0xFF1E8E4A)
    Load.Standing -> if (onDark) Color(0xFFF2C14E) else Color(0xFFB07800)
    Load.Limited -> if (onDark) Color(0xFFFF8A80) else Color(0xFFC5221F)
    Load.Unknown -> Color.Unspecified
}

/**
 * The signature countdown: a 9-sided cookie that morphs into a soft burst and slowly spins once the
 * bus is arriving. The number rolls as it changes.
 */
@Composable
fun CookieCountdown(value: String, unit: String?, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val colors = MaterialTheme.colorScheme
    val arriving = unit == null
    val reducedMotion = rememberReducedMotion()
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val progress by animateFloatAsState(
        targetValue = if (arriving) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "cookieMorph",
    )
    val rotation = if (arriving && !reducedMotion) {
        rememberInfiniteTransition(label = "cookie")
            .animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
            .value
    } else 0f
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = if (arriving) "Arriving" else "$value $unit" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation }
                .clip(MorphShape(morph, progress))
                .background(colors.primary),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(
                value,
                color = colors.onPrimary,
                style = if (arriving) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            AnimatedVisibility(visible = unit != null) {
                Text(unit.orEmpty(), color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Draws a [Morph] between two normalised Material shapes at [progress], scaled to the layout size. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * Text that rolls like an odometer when it changes: falling numbers come up from below,
 * rising ones drop from above. Used for every arrival time so refreshes are visible.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    val slide = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val falling = targetState == "Arr" ||
                (targetState.toIntOrNull() ?: Int.MAX_VALUE) < (initialState.toIntOrNull() ?: Int.MAX_VALUE)
            val dir = if (falling) 1 else -1
            (slideInVertically(slide) { dir * it } + fadeIn(fade)) togetherWith
                (slideOutVertically(slide) { -dir * it } + fadeOut(fade)) using SizeTransform(clip = true)
        },
        label = "rolling",
    ) { Text(it, style = style, color = color, fontWeight = fontWeight) }
}

/** True when the user turned on "Remove animations"; decorative motion should stop. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

fun spokenEta(bus: Bus, now: Instant): String {
    val m = minutesUntil(bus.eta, now)
    return when {
        m < 1 -> "arriving"
        m == 1L -> "1 minute"
        else -> "$m minutes"
    }
}

/** Dashed outline for times that come from the timetable rather than a live bus. */
fun Modifier.dashedOutline(shape: Shape, color: Color, width: Dp = 1.5.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline,
        color,
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
    )
}

/** One of the three arrival tiles on a service card. */
@Composable
fun ArrivalTile(bus: Bus, now: Instant, shape: Shape, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val arriving = minutesUntil(bus.eta, now) < 1
    val bg = if (arriving) colors.primary else colors.surface
    val fg = if (arriving) colors.onPrimary else colors.onSurface
    val description = listOfNotNull(
        spokenEta(bus, now),
        bus.load.label.ifEmpty { null }?.lowercase(),
        bus.type.label.ifEmpty { null }?.let { "${it.lowercase()} deck" },
        "wheelchair accessible".takeIf { bus.wheelchair },
        "scheduled time, not live".takeIf { !bus.monitored },
    ).joinToString(", ")
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .clip(shape)
            .background(bg)
            .then(if (!bus.monitored) Modifier.dashedOutline(shape, colors.outline) else Modifier)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(etaLabel(bus, now), color = fg, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (!arriving) {
                Text(
                    "min",
                    color = fg,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LoadBars(bus.load, fg)
            Text(bus.type.label, color = fg, style = MaterialTheme.typography.labelMedium)
            if (bus.wheelchair) {
                Icon(
                    painterResource(R.drawable.ic_wheelchair),
                    contentDescription = "Wheelchair accessible",
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (!bus.monitored) {
            Text(
                "scheduled",
                color = if (arriving) fg else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Pull-to-refresh with the M3E loading indicator; [onRefresh] runs until the data is back. */
@Composable
fun RefreshableBox(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    onRefresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/** Haptic tick for toggles like starring a stop. */
@Composable
fun rememberToggleHaptic(): (Boolean) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { on -> haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }
    }
}

@Composable
fun LiveChip(fetchedAt: Instant?, now: Instant, offline: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val age = fetchedAt?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
    val label = when {
        age == null && offline -> "Offline"
        age == null -> "Loading"
        offline -> "Offline · ${formatAge(age)} old"
        else -> "Live · ${formatAge(age)} ago"
    }
    Surface(shape = RoundedCornerShape(16.dp), color = if (offline) colors.errorContainer else colors.surfaceContainerHigh) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dot = when {
                offline -> colors.error
                fetchedAt != null -> colors.primary
                else -> colors.outline
            }
            Box(Modifier.size(8.dp).background(dot, RoundedCornerShape(4.dp)))
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (offline) colors.onErrorContainer else colors.onSurfaceVariant)
        }
    }
}

fun formatAge(seconds: Long): String = if (seconds < 60) "${seconds}s" else "${seconds / 60}m"

@Composable
fun MessageCard(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            action?.invoke()
        }
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.size(height))
