package dev.cantabile.tsugi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.AddressHit
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(vm: AppViewModel, onOpenStop: (String) -> Unit, onShowNearby: (AddressHit) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val stops by vm.stops.collectAsStateWithLifecycle()
    val status by vm.stopsStatus.collectAsStateWithLifecycle()
    val results = remember(query, stops) { vm.search(query) }
    // Buildings, addresses and postal codes from OneMap, debounced so typing doesn't spam it.
    var addresses by remember { mutableStateOf<List<AddressHit>>(emptyList()) }
    LaunchedEffect(query) {
        addresses = emptyList()
        if (query.trim().length < 3) return@LaunchedEffect
        delay(350)
        addresses = vm.searchAddresses(query).take(6)
    }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                placeholder = { Text("Stop, road, code, building or postal code") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(30.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHigh,
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        when (val s = status) {
            StopsStatus.Loading -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ContainedLoadingIndicator()
                    Text(
                        "Downloading the bus stop list (refreshed weekly)…",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            is StopsStatus.Failed -> item {
                MessageCard("Couldn't download bus stops: ${s.message}") {
                    Button(onClick = vm::loadStops) { Text("Retry") }
                }
            }
            StopsStatus.Ready -> {
                if (addresses.isNotEmpty()) {
                    item { SearchSection("Places & addresses") }
                    itemsIndexed(addresses, key = { i, a -> "addr-$i-${a.name}" }) { i, hit ->
                        SegmentedListItem(
                            onClick = { onShowNearby(hit) },
                            shapes = ListItemDefaults.segmentedShapes(i, addresses.size),
                            modifier = Modifier.animateItem(),
                            colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
                            leadingContent = { Icon(painterResource(R.drawable.ic_place), null, tint = colors.primary) },
                            supportingContent = { Text(hit.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { Text("Stops near", style = MaterialTheme.typography.labelLarge, color = colors.primary) },
                        ) { Text(hit.name) }
                    }
                    if (results.isNotEmpty()) item { SearchSection("Bus stops") }
                }
                if (query.isNotBlank() && results.isEmpty() && addresses.isEmpty()) {
                    item { Text("No stops match “$query”.", Modifier.padding(8.dp), color = colors.onSurfaceVariant) }
                }
                itemsIndexed(results, key = { _, stop -> stop.code }) { i, stop ->
                    SegmentedListItem(
                        onClick = { onOpenStop(stop.code) },
                        shapes = ListItemDefaults.segmentedShapes(i, results.size),
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
                        leadingContent = {
                            Box(
                                Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stop.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        },
                        supportingContent = { Text(stop.road) },
                    ) { Text(stop.description) }
                }
            }
        }
    }
}

@Composable
private fun SearchSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp),
    )
}
