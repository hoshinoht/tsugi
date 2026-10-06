package dev.cantabile.tsugi.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cantabile.tsugi.R

enum class Tab(val label: String, val icon: Int, val iconSelected: Int) {
    Saved("Saved", R.drawable.ic_star_outline, R.drawable.ic_star),
    Nearby("Nearby", R.drawable.ic_place, R.drawable.ic_place),
    Search("Search", R.drawable.ic_search, R.drawable.ic_search),
}

/** Tab + optional stop overlay. Three screens don't need a navigation library. */
@Composable
fun TsugiRoot(requestedStop: String? = null, onRequestHandled: () -> Unit = {}, vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Saved) }
    var openStop by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedStop) {
        if (requestedStop != null) {
            openStop = requestedStop
            onRequestHandled()
        }
    }
    var openPlace by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locate() else vm.onPermissionDenied()
    }
    val requestLocation = {
        if (vm.hasLocationPermission()) {
            vm.locate()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    BackHandler(enabled = openPlace != null && openStop == null) { openPlace = null }
    BackHandler(enabled = openStop != null) { openStop = null }
    BackHandler(enabled = openStop == null && openPlace == null && tab != Tab.Saved) { tab = Tab.Saved }

    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = openStop to openPlace,
            transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
            label = "stop",
        ) { (stopCode, placeId) ->
            if (stopCode != null) {
                StopScreen(vm, stopCode, onBack = { openStop = null })
            } else if (placeId != null) {
                PlaceScreen(vm, placeId, onBack = { openPlace = null }, onOpenStop = { openStop = it })
            } else {
                // The toolbar slides away while scrolling down and comes back on scroll up.
                val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
                Box(Modifier.fillMaxSize().nestedScroll(toolbarScroll)) {
                    val open: (String) -> Unit = { openStop = it }
                    when (tab) {
                        Tab.Saved -> FavouritesScreen(vm, open, onOpenPlace = { openPlace = it })
                        Tab.Nearby -> NearbyScreen(vm, open, requestLocation)
                        Tab.Search -> SearchScreen(vm, open, onShowNearby = {
                            vm.showNearbyAt(it)
                            tab = Tab.Nearby
                        })
                    }
                    MainToolbar(
                        scrollBehavior = toolbarScroll,
                        current = tab,
                        onSelect = { tab = it },
                        onLocate = {
                            tab = Tab.Nearby
                            requestLocation()
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                    )
                    if (!vm.hasApiKey) {
                        Surface(
                            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text("No LTA AccountKey. Add LTA_ACCOUNT_KEY to local.properties and rebuild.", Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainToolbar(
    scrollBehavior: FloatingToolbarScrollBehavior,
    current: Tab,
    onSelect: (Tab) -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        floatingActionButton = {
            FloatingToolbarDefaults.StandardFloatingActionButton(onClick = onLocate) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = "Find stops near me")
            }
        },
    ) {
        Tab.entries.forEach { t ->
            if (t == current) {
                Button(onClick = {}) {
                    Icon(painterResource(t.iconSelected), contentDescription = null)
                    Text(t.label, Modifier.padding(start = 8.dp))
                }
            } else {
                IconButton(onClick = { onSelect(t) }) {
                    Icon(painterResource(t.icon), contentDescription = t.label)
                }
            }
        }
    }
}
