package dev.cantabile.tsugi.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.CrowdLevel
import dev.cantabile.tsugi.data.Crowding
import dev.cantabile.tsugi.data.Load
import dev.cantabile.tsugi.data.SINGAPORE
import dev.cantabile.tsugi.data.Station
import dev.cantabile.tsugi.data.StopArrivals
import dev.cantabile.tsugi.data.TrainStatus
import dev.cantabile.tsugi.data.canonicalCode
import dev.cantabile.tsugi.data.crowdLineOf
import dev.cantabile.tsugi.data.lineOfCode
import dev.cantabile.tsugi.data.timeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

private const val CROWD_REFRESH_MS = 10 * 60_000L // LTA's real-time crowd update rate

/**
 * An MRT or LRT station: whether its lines are running, how crowded it is now and later, lifts
 * under maintenance, and the bus stops at its exits with live arrivals.
 */
@Composable
fun StationScreen(vm: AppViewModel, code: String, onBack: () -> Unit, onOpenStop: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val index by vm.stations.collectAsStateWithLifecycle()
    val trainStatus by vm.trainStatus.collectAsStateWithLifecycle()
    val crowding by vm.crowding.collectAsStateWithLifecycle()
    val lifts by vm.lifts.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    val allStops by vm.stops.collectAsStateWithLifecycle()
    val now = rememberNow()
    val station = index?.get(code)
    val stops = remember(station, allStops) { station?.let { vm.stopsNearStation(it) }.orEmpty() }
    PollArrivals(vm, stops.take(MAX_POLLED_STOPS).map { it.stop.code })

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(station) {
        val s = station ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refreshTrains()
                vm.refreshLifts()
                vm.refreshCrowding(s.codes.mapNotNull(::crowdLineOf))
                delay(CROWD_REFRESH_MS)
            }
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") } },
            )
        },
    ) { inner ->
        if (station == null) {
            Box(Modifier.padding(inner).padding(16.dp)) { MessageCard(if (index == null) "Loading stations…" else "Unknown station $code.") }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(station.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        station.codes.forEach { c -> LineBadge(lineOfCode(c), c, label = c) }
                    }
                    Text(
                        listOfNotNull(
                            station.lines.joinToString(" · ") { it.title },
                            station.exits.size.takeIf { it > 0 }?.let { "$it exits" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            item { LineStatus(station, trainStatus) }
            item { CrowdingCard(station, crowding, now) }
            lifts?.let { all ->
                val here = all.filter { canonicalCode(it.stationCode) in station.codes.map(::canonicalCode) }
                item { LiftsCard(here.map { listOf(it.liftId, it.liftDesc).filter(String::isNotBlank).joinToString(" · ") }) }
            }
            item {
                Row(Modifier.padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bus stops at the exits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (stops.isNotEmpty()) {
                        TextButton(onClick = {
                            vm.createPlace(station.title, stops.map { it.stop.code })
                            Toast.makeText(context, "Saved ${station.title} as a place", Toast.LENGTH_SHORT).show()
                        }) { Text("Save as place") }
                    }
                }
            }
            if (stops.isEmpty()) item { MessageCard("No bus stops within a short walk of this station's exits.") }
            items(stops, key = { it.stop.code }) { s ->
                StationStopCard(s, arrivals[s.stop.code], now, onClick = { onOpenStop(s.stop.code) })
            }
        }
    }
}

/** Arrivals are polled for this many of the closest stops, to keep requests in check. */
private const val MAX_POLLED_STOPS = 8

@Composable
private fun LineStatus(station: Station, status: TrainStatus?) {
    val colors = MaterialTheme.colorScheme
    val codes = station.codes.map(::canonicalCode).toSet()
    val here = status?.segments.orEmpty().filter { seg -> seg.stations.any { canonicalCode(it) in codes } }
    val elsewhere = status?.segments.orEmpty().filter { it !in here && it.line in station.lines }
    when {
        status == null -> Unit
        here.isNotEmpty() -> Surface(shape = RoundedCornerShape(24.dp), color = colors.errorContainer, contentColor = colors.onErrorContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Train service disrupted here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                here.forEach { seg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineBadge(seg.line, seg.lineCode)
                        Text(
                            seg.direction.takeIf { it.isNotBlank() && it != "Both" }?.let { "Towards $it" } ?: "Both directions",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                status.messages.lastOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val extras = listOfNotNull(
                    "free public buses".takeIf { here.any { it.freeBus } },
                    "a free MRT shuttle".takeIf { here.any { it.freeShuttle } },
                )
                if (extras.isNotEmpty()) {
                    Text("There are ${extras.joinToString(" and ")}. The bus stops below may help too.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        elsewhere.isNotEmpty() -> Surface(shape = RoundedCornerShape(24.dp), color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                elsewhere.mapNotNull { it.line }.distinct().forEach { LineBadge(it, it.code) }
                Text("Disrupted elsewhere on the line", style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(colors.primary, RoundedCornerShape(4.dp)))
            Text("Trains running normally", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

/** Crowding uses the bus load colours: green when there's room, red when it's crowded. */
private fun CrowdLevel.asLoad() = when (this) {
    CrowdLevel.Low -> Load.Seats
    CrowdLevel.Moderate -> Load.Standing
    CrowdLevel.High -> Load.Limited
    CrowdLevel.Unknown -> Load.Unknown
}

@Composable
private fun CrowdingCard(station: Station, crowding: Map<String, Crowding>, now: Instant) {
    val colors = MaterialTheme.colorScheme
    // One row per line platform: an interchange can be quiet on one line and packed on another.
    val rows = station.codes.mapNotNull { c -> crowding[canonicalCode(c)]?.let { c to it } }
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Platform crowding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty()) {
                Text("Not available right now.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            rows.forEach { (c, crowd) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineBadge(lineOfCode(c), c, label = c)
                        CrowdBars(crowd.now)
                        Text(
                            crowd.now.label.ifEmpty { "No reading" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val later = crowd.forecast.filter { it.first.isAfter(now.minusSeconds(30 * 60)) }.take(6)
                    if (later.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            later.forEach { (start, level) ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clearAndSetSemantics { contentDescription = "From ${timeLabel(start.atZone(SINGAPORE).toLocalTime())}: ${level.label}" },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    CrowdBars(level)
                                    Text(
                                        timeLabel(start.atZone(SINGAPORE).toLocalTime()).substringBefore(' '),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text("Forecast in half-hours, from LTA.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CrowdBars(level: CrowdLevel) {
    Box(Modifier.clearAndSetSemantics { contentDescription = level.label }) {
        LoadBars(level.asLoad(), MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LiftsCard(outages: List<String>) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (outages.isEmpty()) colors.surfaceContainer else colors.tertiaryContainer,
        contentColor = if (outages.isEmpty()) colors.onSurface else colors.onTertiaryContainer,
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_wheelchair), null, Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (outages.isEmpty()) {
                    Text("All lifts in service", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        if (outages.size == 1) "1 lift under maintenance" else "${outages.size} lifts under maintenance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    outages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun StationStopCard(s: AppViewModel.StopAtStation, data: StopArrivals?, now: Instant, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.stop.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(s.exit?.let { "Exit $it" }, "${s.distanceM} m", s.stop.road, s.stop.code).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.padding(end = 8.dp))
            }
            val running = data?.services.orEmpty().filter { it.buses.isNotEmpty() }.sortedBy { it.buses.first().eta }
            when {
                data?.fetchedAt == null -> Unit
                running.isEmpty() -> Text("No buses running right now.", style = MaterialTheme.typography.bodyMedium)
                else -> running.take(8).chunked(4).forEach { row ->
                    Row(Modifier.padding(end = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { MiniTile(it, now, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
