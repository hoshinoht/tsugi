package dev.cantabile.tsugi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.ui.theme.TsugiTheme
import androidx.compose.ui.unit.sp
import dev.cantabile.tsugi.data.SINGAPORE
import dev.cantabile.tsugi.data.categoryLabel
import dev.cantabile.tsugi.data.firstBusLabel
import dev.cantabile.tsugi.data.dayType
import dev.cantabile.tsugi.data.frequencyAt
import dev.cantabile.tsugi.data.lastBusLabel
import dev.cantabile.tsugi.data.operatorName
import java.time.ZonedDateTime

/**
 * A bus service's route: its stops in order for one direction; tap a stop to open it. Opened from
 * a stop ([fromStop]), it starts on that stop's direction, highlights it, and marks where the next
 * buses to it are.
 */
@Composable
fun ServiceScreen(vm: AppViewModel, serviceNo: String, onBack: () -> Unit, onOpenStop: (String) -> Unit, fromStop: String? = null) {
    val colors = MaterialTheme.colorScheme
    val routes by vm.routes.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle()
    val directions = routes?.byService?.get(serviceNo).orEmpty()
    val fromDirection = fromStop?.let { s -> directions.entries.firstOrNull { (_, r) -> r.any { it.stop == s } }?.key }
    var direction by rememberSaveable { mutableIntStateOf(fromDirection ?: directions.keys.minOrNull() ?: 1) }
    // Start on the stop's direction once routes load; after that (and after rotation) keep the user's pick.
    var startedOnStop by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fromDirection) {
        if (!startedOnStop && fromDirection != null) {
            direction = fromDirection
            startedOnStop = true
        }
    }
    PollArrivals(vm, listOfNotNull(fromStop))
    val clock = rememberNow()
    // Route index → the buses near that stop, in the direction shown.
    val busesAt = fromStop?.let { from ->
        arrivals[from]?.services?.firstOrNull { it.serviceNo == serviceNo }?.buses.orEmpty()
            .mapNotNull { bus -> vm.locateBus(from, serviceNo, bus)?.takeIf { it.direction == direction }?.let { it.nearIndex to bus } }
            .groupBy({ it.first }, { it.second })
    }.orEmpty()
    val route = directions[direction].orEmpty()
    val operator = route.firstOrNull()?.operator?.let(::operatorName)
    val infoIndex by vm.serviceInfo.collectAsStateWithLifecycle()
    val info = infoIndex?.get(serviceNo, direction)
    val now = ZonedDateTime.now(SINGAPORE)
    val ink = TsugiTheme.isInk
    val frequency = info?.frequencyAt(now.toLocalTime(), dayType(now.toLocalDate()))

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") } },
                actions = {
                    if (ink && frequency != null) {
                        Text(
                            frequency.uppercase(),
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        if (ink) {
                            val towards = route.lastOrNull()?.let { vm.stop(it.stop)?.description }
                            InkServiceHeader(serviceNo, towards, Modifier.padding(bottom = 8.dp))
                        } else {
                            Text("Bus $serviceNo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            listOfNotNull(operator, info?.category?.let(::categoryLabel), "${route.size} stops").joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        val running = listOfNotNull(
                            frequency?.takeIf { !ink }?.replaceFirstChar { it.uppercase() },
                            info?.loop?.takeIf { it.isNotBlank() }?.let { "loops at $it" },
                        )
                        if (running.isNotEmpty()) {
                            Text(running.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    if (directions.size > 1) {
                        Choices(directions.keys.sorted(), direction, { d -> "to ${directions[d]?.lastOrNull()?.let { vm.stop(it.stop)?.description } ?: "direction $d"}" }) { direction = it }
                    }
                }
            }
            if (route.isEmpty()) item { MessageCard("Route data is still downloading, or this service isn't in LTA's route list.") }
            if (ink && route.isNotEmpty()) {
                // One bordered card holding the whole line, so the brush runs unbroken from stop to stop.
                item(key = "route-$direction") {
                    val fromIndex = route.indexOfFirst { it.stop == fromStop }.takeIf { it >= 0 }
                    // The ink reaches the nearest bus still on its way to your stop. Opened without a
                    // stop there's no "your bus", so the whole line is ink and nothing reads as passed.
                    val busIndex = if (fromIndex == null) route.size else busesAt.keys.filter { it <= fromIndex }.maxOrNull() ?: -1
                    Surface(
                        Modifier.padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = colors.surfaceContainer,
                        border = cardBorder(),
                    ) {
                        Column(Modifier.padding(vertical = 12.dp)) {
                            route.forEachIndexed { i, r ->
                                val stop = vm.stop(r.stop)
                                InkRouteStop(
                                    index = i,
                                    count = route.size,
                                    name = stop?.description ?: r.stop,
                                    // One line: the code and the day's span; the road is on the stop's own screen.
                                    meta = listOfNotNull(r.stop, r.firstBusLabel(now)?.let { "first $it" }, r.lastBusLabel(now)?.let { "last $it" }).joinToString(" · "),
                                    mark = when {
                                        i == fromIndex -> RouteMark.You
                                        busesAt[i] != null -> RouteMark.Bus
                                        fromIndex != null && i < busIndex -> RouteMark.Passed
                                        else -> RouteMark.Ahead
                                    },
                                    travelledTo = busIndex,
                                    buses = busesAt[i].orEmpty(),
                                    now = clock,
                                    onClick = { onOpenStop(r.stop) },
                                )
                            }
                        }
                    }
                }
            } else itemsIndexed(route, key = { _, r -> "${r.direction}-${r.seq}-${r.stop}" }) { i, r ->
                val stop = vm.stop(r.stop)
                val buses = busesAt[i].orEmpty()
                SegmentedListItem(
                    onClick = { onOpenStop(r.stop) },
                    shapes = ListItemDefaults.segmentedShapes(i, route.size),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = if (r.stop == fromStop) colors.secondaryContainer else colors.surfaceContainer,
                    ),
                    trailingContent = if (buses.isEmpty()) null else {
                        {
                            Surface(shape = RoundedCornerShape(14.dp), color = colors.primary, contentColor = colors.onPrimary) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_bus), null, Modifier.size(16.dp))
                                    Text(
                                        buses.joinToString(" · ") { etaLabel(it, clock) + if (minutesUntil(it.eta, clock) < 1) "" else " min" },
                                        Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    },
                    leadingContent = {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    },
                    supportingContent = {
                        Text(listOfNotNull(stop?.road, r.stop, r.firstBusLabel(now)?.let { "first $it" }, r.lastBusLabel(now)?.let { "last $it" }).joinToString(" · "))
                    },
                ) { Text(stop?.description ?: r.stop) }
            }
        }
    }
}
