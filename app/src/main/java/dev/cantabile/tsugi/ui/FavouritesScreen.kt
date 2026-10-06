package dev.cantabile.tsugi.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.ServiceArrivals
import java.time.Instant

@Composable
fun FavouritesScreen(vm: AppViewModel, onOpenStop: (String) -> Unit) {
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle() // recompose once stop names are available
    val codes = remember(favourites) { favourites.map { it.stopCode }.distinct() }
    PollArrivals(vm, codes)
    val now = rememberNow()

    fun service(f: Favourite.Service): ServiceArrivals? =
        arrivals[f.stopCode]?.services?.firstOrNull { it.serviceNo == f.serviceNo }

    val pinned = favourites.filterIsInstance<Favourite.Service>()
    val hero = pinned
        .mapNotNull { f -> service(f)?.buses?.firstOrNull()?.let { f to it } }
        .minByOrNull { it.second.eta }
        ?.first
    val wholeStops = favourites.filterIsInstance<Favourite.Stop>()
    val groups = pinned.filter { it != hero }.groupBy { it.stopCode }
    val latest = codes.mapNotNull { arrivals[it]?.fetchedAt }.maxOrNull()
    val offline = codes.any { arrivals[it]?.error != null }

    RefreshableBox(onRefresh = { vm.refresh(codes, force = true) }, modifier = Modifier.statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenTitle("Favourites", trailing = { if (codes.isNotEmpty()) LiveChip(latest, now, offline) }) }

            if (favourites.isEmpty()) {
                item {
                    MessageCard("Nothing saved yet. Open a stop from Nearby or Search, then tap the star to save the whole stop, or the star on a bus to save just that one.")
                }
            }

            if (hero != null) {
                item(key = "hero") {
                    HeroCard(vm, hero, service(hero)!!, now, onClick = { onOpenStop(hero.stopCode) })
                }
            }

            items(wholeStops, key = { "stop-${it.stopCode}" }) { fav ->
                WholeStopCard(vm, fav.stopCode, arrivals[fav.stopCode]?.services.orEmpty(), now, onClick = { onOpenStop(fav.stopCode) })
            }

            groups.forEach { (code, favs) ->
                item(key = "group-$code") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val stop = vm.stop(code)
                        Text(
                            stop?.description ?: code,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            favs.forEachIndexed { i, f ->
                                ServiceRow(vm, f.serviceNo, service(f), now, groupShape(i, favs.size), onClick = { onOpenStop(code) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(vm: AppViewModel, fav: Favourite.Service, service: ServiceArrivals, now: Instant, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val next = service.buses.first()
    val minutes = minutesUntil(next.eta, now)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("NEXT UP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(fav.serviceNo, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "to ${vm.stop(next.destinationCode)?.description ?: "—"}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${vm.stop(fav.stopCode)?.description ?: ""} · ${fav.stopCode}",
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                val later = service.buses.drop(1).map { minutesUntil(it.eta, now) }
                if (later.isNotEmpty()) {
                    Text("then ${later.joinToString(" · ")} min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WholeStopCard(vm: AppViewModel, code: String, services: List<ServiceArrivals>, now: Instant, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(vm.stop(code)?.description ?: code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Whole stop · $code · ${services.size} services",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            services.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { s ->
                        val first = s.buses.firstOrNull()
                        val arriving = first != null && minutesUntil(first.eta, now) < 1
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (arriving) colors.primary else colors.surface)
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val fg = when {
                                arriving -> colors.onPrimary
                                first == null -> colors.outline
                                else -> colors.onSurface
                            }
                            Text(s.serviceNo, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                first?.let { if (arriving) "Arr" else "${minutesUntil(it.eta, now)}m" } ?: "—",
                                color = fg,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    repeat(5 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun ServiceRow(
    vm: AppViewModel,
    serviceNo: String,
    service: ServiceArrivals?,
    now: Instant,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val first = service?.buses?.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ServiceBadge(serviceNo, active = first != null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                first?.let { vm.stop(it.destinationCode)?.description } ?: if (service == null) "Loading…" else "Not operating now",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (first != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadBars(first.load, colors.onSurface)
                    Text(
                        first.load.label + if (!first.monitored) " · scheduled" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (first != null) {
            Column(horizontalAlignment = Alignment.End) {
                val m = minutesUntil(first.eta, now)
                if (m < 1) {
                    Surface(shape = RoundedCornerShape(14.dp), color = colors.primary, contentColor = colors.onPrimary) {
                        Text("Arr", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$m", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("min", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                    }
                }
                val later = service?.buses.orEmpty().drop(1).map { minutesUntil(it.eta, now) }
                if (later.isNotEmpty()) {
                    Text(later.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
