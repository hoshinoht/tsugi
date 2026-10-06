package dev.cantabile.tsugi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.SINGAPORE
import dev.cantabile.tsugi.data.categoryLabel
import dev.cantabile.tsugi.data.firstBusLabel
import dev.cantabile.tsugi.data.frequencyAt
import dev.cantabile.tsugi.data.lastBusLabel
import dev.cantabile.tsugi.data.operatorName
import java.time.ZonedDateTime

/** A bus service's route: its stops in order for one direction; tap a stop to open it. */
@Composable
fun ServiceScreen(vm: AppViewModel, serviceNo: String, onBack: () -> Unit, onOpenStop: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val routes by vm.routes.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle()
    val directions = routes?.byService?.get(serviceNo).orEmpty()
    var direction by rememberSaveable { mutableIntStateOf(directions.keys.minOrNull() ?: 1) }
    val route = directions[direction].orEmpty()
    val operator = route.firstOrNull()?.operator?.let(::operatorName)
    val infoIndex by vm.serviceInfo.collectAsStateWithLifecycle()
    val info = infoIndex?.get(serviceNo, direction)
    val now = ZonedDateTime.now(SINGAPORE)

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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Bus $serviceNo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(operator, info?.category?.let(::categoryLabel), "${route.size} stops").joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        val running = listOfNotNull(
                            info?.frequencyAt(now.toLocalTime())?.replaceFirstChar { it.uppercase() },
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
            itemsIndexed(route, key = { _, r -> "${r.direction}-${r.seq}-${r.stop}" }) { i, r ->
                val stop = vm.stop(r.stop)
                SegmentedListItem(
                    onClick = { onOpenStop(r.stop) },
                    shapes = ListItemDefaults.segmentedShapes(i, route.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
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
