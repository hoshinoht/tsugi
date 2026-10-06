package dev.cantabile.tsugi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Favourite

/**
 * Everything you can save from a stop, in one sheet: the whole stop, individual buses,
 * and which places the stop belongs to. Changes apply immediately.
 */
@Composable
fun SaveSheet(vm: AppViewModel, code: String, serviceNos: List<String>, onDismiss: () -> Unit) {
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val haptic = rememberToggleHaptic()
    val stopName = vm.stop(code)?.description ?: code
    val places = favourites.filterIsInstance<Favourite.Place>()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(vm.suggestPlaceName(code)) }
    val clearItem = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Save $stopName", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            val stopFav = Favourite.Stop(code)
            val wholeSaved = stopFav in favourites
            ListItem(
                headlineContent = { Text("Whole stop") },
                supportingContent = { Text("All ${serviceNos.size} buses in one card") },
                trailingContent = {
                    Switch(checked = wholeSaved, onCheckedChange = {
                        haptic(it)
                        vm.toggleFavourite(stopFav)
                    })
                },
                colors = clearItem,
            )

            if (serviceNos.isNotEmpty()) {
                SectionLabel("Individual buses")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    serviceNos.forEach { no ->
                        val fav = Favourite.Service(code, no)
                        val on = fav in favourites
                        FilterChip(
                            selected = on,
                            onClick = {
                                haptic(!on)
                                vm.toggleFavourite(fav)
                            },
                            label = { Text(no, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
            }

            SectionLabel("Places")
            places.forEach { place ->
                val inPlace = code in place.stopCodes
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text("${place.stopCodes.size} stop${if (place.stopCodes.size == 1) "" else "s"}") },
                    trailingContent = {
                        Checkbox(checked = inPlace, onCheckedChange = {
                            haptic(it)
                            if (it) vm.addToPlace(place.id, code) else vm.removeFromPlace(place.id, code)
                        })
                    },
                    colors = clearItem,
                )
            }
            if (creating) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Place name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        vm.addToPlace(null, code, newName)
                        haptic(true)
                        creating = false
                    }) { Text("Add") }
                }
            } else {
                TextButton(onClick = { creating = true }) {
                    Icon(painterResource(R.drawable.ic_add), null)
                    Text("New place with this stop", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
