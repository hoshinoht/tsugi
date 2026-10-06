package dev.cantabile.tsugi.ui

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
import androidx.compose.material3.toShape
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

/** Corner radii for a connected list: big outer corners, small inner ones. */
fun groupShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape = when {
    count == 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
    index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
    else -> RoundedCornerShape(inner)
}

/** Same idea, horizontally, for a row of tiles. */
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

/** 3/2/1 bars, so crowding reads without relying on colour. */
@Composable
fun LoadBars(load: Load, color: Color, modifier: Modifier = Modifier) {
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
                        if (i < load.bars) color else color.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** The signature countdown: a 9-sided cookie that slowly spins while the bus is arriving. */
@Composable
fun CookieCountdown(value: String, unit: String?, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val colors = MaterialTheme.colorScheme
    val arriving = unit == null
    val rotation = if (arriving) {
        val t = rememberInfiniteTransition(label = "cookie")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "spin").value
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(colors.primary),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                color = colors.onPrimary,
                style = if (arriving) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
            )
            if (unit != null) {
                Text(unit, color = colors.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
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
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .then(if (!bus.monitored) Modifier.dashedOutline(shape, colors.outline) else Modifier)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(etaLabel(bus, now), color = fg, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
