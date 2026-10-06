package dev.cantabile.tsugi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.shadow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.stopsAwayLabel
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.tracking.Tracked
import dev.cantabile.tsugi.data.NextUp
import dev.cantabile.tsugi.data.NextUpCandidate
import dev.cantabile.tsugi.data.labelMinutes
import dev.cantabile.tsugi.data.pickNextUp
import dev.cantabile.tsugi.data.allStopCodes
import dev.cantabile.tsugi.data.ServiceArrivals
import java.time.Instant

@Composable
fun FavouritesScreen(
    vm: AppViewModel,
    onOpenStop: (String) -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStation: (String) -> Unit = {},
) {
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val loaded by vm.favouritesLoaded.collectAsStateWithLifecycle()
    val here by vm.here.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshHere() }
    // Cards start expanded on every launch; collapsing lasts for the session (and rotation).
    var collapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val setCollapsed = { id: String, collapse: Boolean -> collapsed = if (collapse) collapsed + id else collapsed - id }
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle() // recompose once stop names are available
    val codes = remember(favourites) { favourites.flatMap { it.allStopCodes }.distinct() }
    PollArrivals(vm, codes)
    PollTrainStatus(vm)
    val now = rememberNow()

    fun service(f: Favourite.Service): ServiceArrivals? =
        arrivals[f.stopCode]?.services?.firstOrNull { it.serviceNo == f.serviceNo }

    val pinned = favourites.filterIsInstance<Favourite.Service>()
    val wholeStops = favourites.filterIsInstance<Favourite.Stop>()
    val places = favourites.filterIsInstance<Favourite.Place>()
    // "Next up": pinned buses, plus every service at saved whole stops and places.
    val candidates = buildList {
        pinned.forEach { f -> service(f)?.let { add(NextUpCandidate(f.stopCode, it, pinned = true)) } }
        (wholeStops.map { it.stopCode } + places.flatMap { it.stopCodes }).distinct().forEach { code ->
            arrivals[code]?.services.orEmpty().forEach { add(NextUpCandidate(code, it, pinned = false)) }
        }
    }
    val hero = pickNextUp(
        candidates,
        stopLatLng = { code -> vm.stop(code)?.let { it.lat to it.lng } },
        here = here?.let { it.latitude to it.longitude },
        now = now,
    )
    // Don't repeat the hero's bus in its stop's pinned group.
    val groups = pinned.filterNot { hero != null && it.stopCode == hero.stopCode && it.serviceNo == hero.service.serviceNo }.groupBy { it.stopCode }
    // Cards in the user's saved order; new cards go after, in the default order.
    val savedOrder by vm.cardOrder.collectAsStateWithLifecycle()
    var dragOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(savedOrder) { if (dragOrder == savedOrder) dragOrder = null }
    val cards = buildList {
        places.forEach { add(HomeCard.PlaceCard(it)) }
        wholeStops.forEach { add(HomeCard.StopCard(it.stopCode)) }
        groups.forEach { (code, favs) -> add(HomeCard.GroupCard(code, favs)) }
    }
    val order = dragOrder ?: savedOrder
    val ordered = cards.sortedBy { c -> order.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } }
    val currentIds by rememberUpdatedState(ordered.map { it.id })
    val haptic = rememberToggleHaptic()
    val tracking = rememberTrackingControl(walkMetres = vm::walkMetres)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val ids = (dragOrder ?: currentIds).toMutableList()
        val f = ids.indexOf(from.key)
        val t = ids.indexOf(to.key)
        if (f >= 0 && t >= 0) {
            ids.add(t, ids.removeAt(f))
            dragOrder = ids
        }
    }
    val colors = MaterialTheme.colorScheme
    val latest = codes.mapNotNull { arrivals[it]?.fetchedAt }.maxOrNull()
    val offline = codes.any { arrivals[it]?.error != null }

    RefreshableBox(onRefresh = {
        vm.refreshTrains()
        vm.refresh(codes, force = true)
    }, modifier = Modifier.statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenTitle("Favourites", trailing = {
                    if (codes.isNotEmpty()) LiveChip(latest, now, offline)
                    SettingsButton(onOpenSettings)
                })
            }
            item(key = "trains") { TrainStatusCard(vm, onOpenStation = onOpenStation) }

            if (loaded && favourites.isEmpty()) {
                item {
                    MessageCard("Nothing saved yet. Open a stop from Nearby or Search, then tap the star to save the whole stop, or the star on a bus to save just that one.")
                }
            }

            // Overnight nothing is running: show when the first saved bus starts instead of a gap.
            val firstBus = if (hero == null) {
                candidates.mapNotNull { c -> c.service.firstBus?.let { label -> labelMinutes(label)?.let { Triple(it, label, c) } } }
                    .minByOrNull { it.first }
            } else null
            if (firstBus != null) {
                item(key = "first-bus") {
                    val (_, label, c) = firstBus
                    Surface(
                        onClick = { onOpenStop(c.stopCode) },
                        modifier = Modifier.animateItem(),
                        shape = RoundedCornerShape(32.dp),
                        color = colors.surfaceContainerHigh,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
                            Text("NO BUSES RUNNING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                            Text("First bus $label", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${c.service.serviceNo} at ${vm.stop(c.stopCode)?.description ?: c.stopCode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (hero != null) {
                item(key = "hero") {
                    HeroCard(
                        vm, hero, now,
                        onClick = { onOpenStop(hero.stopCode) },
                        tracking = tracking.tracked == Tracked(hero.stopCode, hero.service.serviceNo),
                        onToggleTracking = { tracking.toggle(hero.stopCode, hero.service.serviceNo) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Places, whole stops and pinned-bus groups, in the user's order; drag the grip to move.
            items(ordered, key = { it.id }) { card ->
                ReorderableItem(reorderState, key = card.id) { dragging ->
                    val handle = Modifier.draggableHandle(
                        onDragStarted = { haptic(true) },
                        onDragStopped = { dragOrder?.let(vm::saveCardOrder) },
                    )
                    val lift by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "lift")
                    Box(Modifier.shadow(lift, RoundedCornerShape(28.dp))) {
                        when (card) {
                            is HomeCard.PlaceCard -> PlaceCard(card.place, soonest(card.place.stopCodes, arrivals), now, onClick = { onOpenPlace(card.place.id) }, handleModifier = handle)
                            is HomeCard.StopCard -> WholeStopCard(
                                vm, card.code, arrivals[card.code]?.services.orEmpty(), now,
                                expanded = card.id !in collapsed,
                                onToggle = { setCollapsed(card.id, it) },
                                onClick = { onOpenStop(card.code) },
                                handleModifier = handle,
                            )
                            is HomeCard.GroupCard -> {
                                val expanded = card.id !in collapsed
                                Column(Modifier.background(colors.surface)) {
                                    val soonest = card.favs.mapNotNull { f -> service(f)?.buses?.firstOrNull()?.let { f.serviceNo to it } }.minByOrNull { it.second.eta }
                                    CollapsibleHeader(
                                        title = vm.stop(card.code)?.description ?: card.code,
                                        summary = listOfNotNull(
                                            "${card.favs.size} bus${if (card.favs.size == 1) "" else "es"}",
                                            soonest?.let { (no, bus) -> "$no ${etaLabel(bus, now).let { if (it == "Arr") "arriving" else "in $it min" }}" },
                                        ).joinToString(" · "),
                                        expanded = expanded,
                                        onToggle = { setCollapsed(card.id, expanded) },
                                        onOpen = { onOpenStop(card.code) },
                                        handleModifier = handle,
                                    )
                                    CollapsibleBody(expanded) {
                                        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                            card.favs.forEachIndexed { i, f ->
                                                ServiceRow(vm, f.serviceNo, service(f), now, ListItemDefaults.segmentedShapes(i, card.favs.size), onClick = { onOpenStop(card.code) })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    vm: AppViewModel,
    nextUp: NextUp,
    now: Instant,
    onClick: () -> Unit,
    tracking: Boolean,
    onToggleTracking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val service = nextUp.service
    val next = nextUp.bus
    val minutes = minutesUntil(next.eta, now)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        nextUp.distanceM?.let { "NEAREST SAVED STOP · $it M" } ?: "NEXT UP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(service.serviceNo, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "to ${vm.stop(next.destinationCode)?.description ?: "—"}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            vm.stop(nextUp.stopCode)?.description,
                            nextUp.stopCode,
                            vm.stopsAway(nextUp.stopCode, service.serviceNo, next)?.let(::stopsAwayLabel),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    lastBusNotice(service, now)?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                }
                CookieCountdown(
                    value = if (minutes < 1) "Arr" else minutes.toString(),
                    unit = if (minutes < 1) null else "min",
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadBars(next.load, colors.onPrimaryContainer)
                Text(listOf(next.load.label, next.type.label.let { if (it.isEmpty()) it else "$it deck" }).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                val later = service.buses.dropWhile { it != next }.drop(1).map { minutesUntil(it.eta, now) }
                if (later.isNotEmpty()) {
                    Text("then ${later.joinToString(" · ")} min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                FilledIconToggleButton(
                    checked = tracking,
                    onCheckedChange = { onToggleTracking() },
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        painterResource(if (tracking) R.drawable.ic_bell_filled else R.drawable.ic_bell),
                        contentDescription = if (tracking) "Stop alerts for ${service.serviceNo}" else "Alert me when ${service.serviceNo} is near",
                    )
                }
            }
        }
    }
}

// The extra Modifier is the drag handle from the reorderable list, not the layout modifier.
@Suppress("ModifierParameter")
@Composable
private fun WholeStopCard(
    vm: AppViewModel,
    code: String,
    services: List<ServiceArrivals>,
    now: Instant,
    expanded: Boolean,
    onToggle: (collapse: Boolean) -> Unit,
    onClick: () -> Unit,
    handleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val running = services.filter { it.buses.isNotEmpty() }
    val soonest = running.minByOrNull { it.buses.first().eta }
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(vm.stop(code)?.description ?: code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (expanded) {
                            "Whole stop · $code · ${services.size} services"
                        } else {
                            listOfNotNull(
                                "${running.size} of ${services.size} running",
                                soonest?.let { s -> "${s.serviceNo} ${etaLabel(s.buses.first(), now).let { if (it == "Arr") "arriving" else "in $it min" }}" },
                            ).joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                DragHandle(handleModifier)
                ExpandChevron(expanded, onToggle = { onToggle(expanded) })
            }
            CollapsibleBody(expanded) {
                Column(Modifier.padding(top = 10.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Running services first (soonest first), then idle ones with their first-bus times.
                    val ordered = running.sortedBy { it.buses.first().eta } + services.filter { it.buses.isEmpty() }
                    ordered.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { s -> MiniTile(s, now, Modifier.weight(1f)) }
                            repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Header for a group of pinned buses: tap anywhere on it to collapse or expand. */
// The extra Modifier is the drag handle from the reorderable list, not the layout modifier.
@Suppress("ModifierParameter")
@Composable
private fun CollapsibleHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    handleModifier: Modifier,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Open stop", onClick = onOpen)
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AnimatedVisibility(!expanded) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DragHandle(handleModifier)
        ExpandChevron(expanded, onToggle)
    }
}

/** Grip for drag-to-reorder; [handle] carries the drag gesture from the reorderable list. */
// The extra Modifier is the drag handle from the reorderable list, not the layout modifier.
@Suppress("ModifierParameter")
@Composable
private fun DragHandle(handleModifier: Modifier) {
    Icon(
        painterResource(R.drawable.ic_drag),
        contentDescription = "Drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = handleModifier.padding(12.dp).size(20.dp),
    )
}

/** A reorderable card on Favourites. Ids double as collapse keys and the saved order. */
private sealed interface HomeCard {
    val id: String

    data class PlaceCard(val place: Favourite.Place) : HomeCard {
        override val id get() = "place:${place.id}"
    }

    data class StopCard(val code: String) : HomeCard {
        override val id get() = "stop:$code"
    }

    data class GroupCard(val code: String, val favs: List<Favourite.Service>) : HomeCard {
        override val id get() = "group:$code"
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun CollapsibleBody(expanded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) { content() }
}

@Composable
fun ServiceRow(
    vm: AppViewModel,
    serviceNo: String,
    service: ServiceArrivals?,
    now: Instant,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val first = service?.buses?.firstOrNull()
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.heightIn(min = 72.dp),
        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
        leadingContent = { ServiceBadge(serviceNo, active = first != null) },
        supportingContent = if (first == null && service?.firstBus != null) {
            { Text("First bus ${service.firstBus}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        } else first?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadBars(it.load, colors.onSurface)
                    Text(
                        listOfNotNull(service?.let { s -> lastBusNotice(s, now) }, caption, it.load.label.ifEmpty { null }, "scheduled".takeIf { _ -> !it.monitored }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = first?.let {
            {
                Column(horizontalAlignment = Alignment.End) {
                    val m = minutesUntil(it.eta, now)
                    if (m < 1) {
                        Surface(shape = RoundedCornerShape(14.dp), color = colors.primary, contentColor = colors.onPrimary) {
                            Text("Arr", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingText("$m", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("min", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                        }
                    }
                    val later = service.buses.drop(1).map { b -> minutesUntil(b.eta, now) }
                    if (later.isNotEmpty()) {
                        Text(later.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        },
    ) {
        Text(
            first?.let { vm.stop(it.destinationCode)?.description } ?: if (service == null) "Loading…" else "Not running now",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// The extra Modifier is the drag handle from the reorderable list, not the layout modifier.
@Suppress("ModifierParameter")
@Composable
private fun PlaceCard(place: Favourite.Place, board: List<Pair<String, ServiceArrivals>>, now: Instant, onClick: () -> Unit, handleModifier: Modifier, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Place · ${place.stopCodes.size} stop${if (place.stopCodes.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DragHandle(handleModifier)
                Icon(painterResource(R.drawable.ic_chevron_right), null)
            }
            val next = board.take(4)
            if (next.isEmpty()) {
                Text("No buses running right now", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    next.forEach { (_, s) -> MiniTile(s, now, Modifier.weight(1f)) }
                    repeat(4 - next.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
