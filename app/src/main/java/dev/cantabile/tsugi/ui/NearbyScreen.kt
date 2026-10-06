package dev.cantabile.tsugi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.NearbyStop
import java.time.Instant

private val RADII = listOf(200, 400, 800)

@Composable
fun NearbyScreen(vm: AppViewModel, onOpenStop: (String) -> Unit, onRequestLocation: () -> Unit) {
    val state by vm.nearby.collectAsStateWithLifecycle()
    val radius by vm.radiusM.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val now = rememberNow()

    LaunchedEffect(Unit) {
        if (state == NearbyState.Idle) onRequestLocation()
    }
    val ready = state as? NearbyState.Ready
    // null = default to the closest stop; "" = the user collapsed everything. Only the open stop is polled.
    val open = if (expanded == null) ready?.stops?.firstOrNull()?.stop?.code else expanded?.ifEmpty { null }
    PollArrivals(vm, listOfNotNull(open))

    RefreshableBox(
        onRefresh = {
            vm.locate()
            open?.let { vm.refresh(listOf(it), force = true) }
        },
        modifier = Modifier.statusBarsPadding(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenTitle(
                    "Nearby",
                    subtitle = ready?.let {
                    listOfNotNull(
                        it.label?.let { l -> "Near $l" },
                        "${it.stops.size} stops within $radius m",
                        "approximate location".takeIf { _ -> !it.precise },
                    ).joinToString(" · ")
                },
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    RADII.forEachIndexed { i, r ->
                        ToggleButton(
                            checked = r == radius,
                            onCheckedChange = { vm.setRadius(r) },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                RADII.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text("$r m") }
                    }
                }
            }

            when (val s = state) {
                NearbyState.Idle, NearbyState.Locating -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
                }
                NearbyState.NeedsPermission -> item {
                    MessageCard("Allow location to see bus stops around you.") {
                        Button(onClick = onRequestLocation) { Text("Allow location") }
                    }
                }
                is NearbyState.Failed -> item {
                    MessageCard(s.message) { FilledTonalButton(onClick = onRequestLocation) { Text("Try again") } }
                }
                is NearbyState.Ready -> {
                    if (s.stops.isEmpty()) {
                        item { MessageCard("No bus stops within $radius m. Try a wider radius.") }
                    }
                    items(s.stops, key = { it.stop.code }) { nearby ->
                        NearbyStopCard(
                            nearby = nearby,
                            isOpen = nearby.stop.code == open,
                            services = arrivals[nearby.stop.code]?.services,
                            now = now,
                            onToggle = { expanded = if (nearby.stop.code == open) "" else nearby.stop.code },
                            onOpen = { onOpenStop(nearby.stop.code) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyStopCard(
    nearby: NearbyStop,
    isOpen: Boolean,
    services: List<dev.cantabile.tsugi.data.ServiceArrivals>?,
    now: Instant,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val walkMin = (nearby.distanceM / 80).coerceAtLeast(1) // ~4.8 km/h
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(if (isOpen) 28.dp else 22.dp),
        color = if (isOpen) colors.surfaceContainerHigh else colors.surfaceContainer,
        modifier = Modifier.animateContentSize(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nearby.stop.description,
                        style = if (isOpen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${nearby.stop.road} · ${nearby.stop.code}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (isOpen) colors.primary else colors.surface,
                    contentColor = if (isOpen) colors.onPrimary else colors.onSurfaceVariant,
                ) {
                    Text(
                        "${nearby.distanceM} m · $walkMin min",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (isOpen) {
                when {
                    services == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
                    services.isEmpty() -> Text("No buses running right now.", style = MaterialTheme.typography.bodyMedium)
                    else -> services.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { s ->
                                val first = s.buses.firstOrNull()
                                val arriving = first != null && minutesUntil(first.eta, now) < 1
                                Surface(
                                    onClick = onOpen,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (arriving) colors.primaryContainer else colors.surface,
                                    contentColor = if (arriving) colors.onPrimaryContainer else colors.onSurface,
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                        Text(s.serviceNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            when {
                                                first == null -> "Not running"
                                                arriving -> "Arriving"
                                                else -> "${minutesUntil(first.eta, now)} min"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (arriving) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                FilledTonalButton(onClick = onOpen) {
                    Text("Open stop")
                    Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
