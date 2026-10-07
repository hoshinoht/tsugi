package dev.cantabile.tsugi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.compactFirstBus
import dev.cantabile.tsugi.data.TOMORROW
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.ServiceArrivals
import dev.cantabile.tsugi.data.StopArrivals
import java.time.Instant

/** A departures board for a group of stops: soonest first, or grouped by stop. */
@Composable
fun PlaceScreen(vm: AppViewModel, placeId: String, onBack: () -> Unit, onOpenStop: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle()
    val place = favourites.firstOrNull { it is Favourite.Place && it.id == placeId } as Favourite.Place?
    var bySoonest by rememberSaveable { mutableStateOf(true) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow()

    val loaded by vm.favouritesLoaded.collectAsStateWithLifecycle()
    // Deleted (or its last stop removed): leave. Wait for favourites to load first.
    LaunchedEffect(loaded, place == null) { if (loaded && place == null) onBack() }
    if (place == null) return
    PollArrivals(vm, place.stopCodes)

    if (renaming) RenameDialog(place.name, onDismiss = { renaming = false }) { vm.renamePlace(placeId, it) }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(painterResource(R.drawable.ic_edit), "Rename place") }
                    IconButton(onClick = { vm.deletePlace(placeId) }) { Icon(painterResource(R.drawable.ic_delete), "Delete place") }
                },
            )
        },
    ) { inner ->
        RefreshableBox(onRefresh = { vm.refresh(place.stopCodes, force = true) }, modifier = Modifier.padding(top = inner.calculateTopPadding())) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = inner.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Text(place.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${place.stopCodes.size} stop${if (place.stopCodes.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            ToggleButton(
                                checked = bySoonest,
                                onCheckedChange = { bySoonest = true },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            ) { Text("Soonest") }
                            ToggleButton(
                                checked = !bySoonest,
                                onCheckedChange = { bySoonest = false },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            ) { Text("By stop") }
                        }
                    }
                }

                if (bySoonest) {
                    val board = soonest(place.stopCodes, arrivals)
                    if (board.isEmpty()) item { MessageCard("No buses running at these stops right now.") }
                    items(board.size, key = { "${board[it].first}-${board[it].second.serviceNo}" }) { i ->
                        val (code, service) = board[i]
                        ServiceRow(
                            vm, service.serviceNo, service, now, ListItemDefaults.segmentedShapes(i, board.size),
                            modifier = Modifier.animateItem(),
                            onClick = { onOpenStop(code) },
                            caption = vm.stop(code)?.description ?: code,
                        )
                    }
                } else {
                    items(place.stopCodes, key = { it }) { code ->
                        StopSection(Modifier.animateItem(), vm, code, arrivals[code], now, onOpen = { onOpenStop(code) }, onRemove = { vm.removeFromPlace(placeId, code) })
                    }
                }
            }
        }
    }
}

/** Every running service across the place's stops, soonest bus first. */
fun soonest(codes: List<String>, arrivals: Map<String, StopArrivals>): List<Pair<String, ServiceArrivals>> =
    codes.flatMap { code -> arrivals[code]?.services.orEmpty().filter { it.buses.isNotEmpty() }.map { code to it } }
        .sortedBy { it.second.buses.first().eta }

@Composable
private fun StopSection(modifier: Modifier, vm: AppViewModel, code: String, data: StopArrivals?, now: Instant, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(vm.stop(code)?.description ?: code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(code, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                IconButton(onClick = onOpen) { Icon(painterResource(R.drawable.ic_chevron_right), "Open stop") }
                IconButton(onClick = onRemove) { Icon(painterResource(R.drawable.ic_close), "Remove from place") }
            }
            val running = data?.services.orEmpty().filter { it.buses.isNotEmpty() }
            when {
                data?.fetchedAt == null -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)
                running.isEmpty() -> Text("No buses running right now.", style = MaterialTheme.typography.bodyMedium)
                else -> running.chunked(4).forEach { row ->
                    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { s -> MiniTile(s, now, Modifier.weight(1f)) }
                        repeat(4 - row.size) { androidx.compose.foundation.layout.Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Compact service + minutes tile used in place and whole-stop cards. */
@Composable
fun MiniTile(service: ServiceArrivals, now: Instant, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val first = service.buses.firstOrNull()
    val arriving = first != null && minutesUntil(first.eta, now) < 1
    val fg = when {
        arriving -> colors.onPrimary
        first == null -> colors.outline
        else -> colors.onSurface
    }
    Column(
        modifier
            .clearAndSetSemantics {
                contentDescription = "Bus ${service.serviceNo}, " +
                    (first?.let { spokenEta(it, now) } ?: service.firstBus?.let { "not running, first bus $it" } ?: "not running")
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (arriving) colors.primary else colors.surface)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            service.serviceNo, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.titleSmall.fontSize),
        )
        RollingText(
            first?.let { if (arriving) "Arr" else "${minutesUntil(it.eta, now)}m" }
                ?: service.firstBus?.let { compactFirstBus(it).substringBefore(' ').let { t -> if (it.startsWith(TOMORROW)) "Tmr" else t } }
                ?: "—",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename place") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onRename(name.trim())
                onDismiss()
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
