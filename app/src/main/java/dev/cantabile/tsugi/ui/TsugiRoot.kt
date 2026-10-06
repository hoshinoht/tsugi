package dev.cantabile.tsugi.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.width
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cantabile.tsugi.R

/** Full-screen layers over the tabs; the first set one shows (settings, then stop, service, place). */
private data class Overlay(val stop: String?, val place: String?, val service: String?, val settings: Boolean)

enum class Tab(val label: String, val icon: Int, val iconSelected: Int) {
    Saved("Saved", R.drawable.ic_star_outline, R.drawable.ic_star),
    Nearby("Nearby", R.drawable.ic_place, R.drawable.ic_place),
    Search("Search", R.drawable.ic_search, R.drawable.ic_search),
}

/** Tab + optional stop overlay. Three screens don't need a navigation library. */
@Composable
fun TsugiRoot(
    requestedStop: String? = null,
    requestedTab: String? = null,
    onRequestHandled: () -> Unit = {},
    vm: AppViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Saved) }
    var openStop by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlace by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var openService by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedStop, requestedTab) {
        if (requestedStop != null || requestedTab != null) {
            requestedStop?.let { openStop = it }
            Tab.entries.firstOrNull { it.name == requestedTab }?.let {
                tab = it
                openStop = null
                openPlace = null
                openService = null
                settingsOpen = false
            }
            onRequestHandled()
        }
    }
    // Keeps each tab's remembered state (scroll position, collapsed cards) while it's off screen.
    val tabStates = rememberSaveableStateHolder()

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

    // Predictive back for the full-screen layers: the top one shrinks as you swipe, then closes.
    // Order: settings, then stop, then service, then place.
    val overlayOpen = settingsOpen || openStop != null || openService != null || openPlace != null
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = overlayOpen) { events ->
        try {
            events.collect { backProgress = it.progress }
            when {
                settingsOpen -> settingsOpen = false
                openStop != null -> openStop = null
                openService != null -> openService = null
                else -> openPlace = null
            }
        } finally {
            backProgress = 0f
        }
    }
    BackHandler(enabled = !overlayOpen && tab != Tab.Saved) { tab = Tab.Saved }
    val reducedMotion = rememberReducedMotion()
    val peek = Modifier.graphicsLayer {
        val p = if (reducedMotion) 0f else backProgress
        scaleX = 1f - 0.08f * p
        scaleY = 1f - 0.08f * p
        translationX = 24.dp.toPx() * p
        shape = RoundedCornerShape(32.dp * p)
        clip = p > 0f
    }

    val enterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = Overlay(openStop, openPlace, openService, settingsOpen),
            transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
            label = "stop",
        ) { (stopCode, placeId, serviceNo, settings) ->
            if (settings) {
                Box(peek) { SettingsScreen(vm, onBack = { settingsOpen = false }) }
            } else if (stopCode != null) {
                Box(peek) { StopScreen(vm, stopCode, onBack = { openStop = null }, onOpenStop = { openStop = it }) }
            } else if (serviceNo != null) {
                Box(peek) { ServiceScreen(vm, serviceNo, onBack = { openService = null }, onOpenStop = { openStop = it }) }
            } else if (placeId != null) {
                Box(peek) { PlaceScreen(vm, placeId, onBack = { openPlace = null }, onOpenStop = { openStop = it }) }
            } else {
                // The toolbar slides away while scrolling down and comes back on scroll up.
                val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
                Box(Modifier.fillMaxSize().nestedScroll(toolbarScroll)) {
                    val open: (String) -> Unit = { openStop = it }
                    tabStates.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Saved -> FavouritesScreen(vm, open, onOpenPlace = { openPlace = it }, onOpenSettings = { settingsOpen = true })
                            Tab.Nearby -> NearbyScreen(vm, open, requestLocation, onOpenSettings = { settingsOpen = true })
                            Tab.Search -> SearchScreen(
                                vm, open,
                                onOpenService = { openService = it },
                                onShowNearby = {
                                    vm.showNearbyAt(it)
                                    tab = Tab.Nearby
                                },
                                onOpenSettings = { settingsOpen = true },
                            )
                        }
                    }
                    // Fade the list into the background behind the toolbar so cards don't run into it.
                    val surface = MaterialTheme.colorScheme.surface
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Brush.verticalGradient(0f to surface.copy(alpha = 0f), 0.55f to surface.copy(alpha = 0.92f), 1f to surface)),
                    )
                    MainToolbar(
                        scrollBehavior = toolbarScroll,
                        current = tab,
                        onSelect = { tab = it },
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
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        scrollBehavior = scrollBehavior,
        // Vibrant (primary-container) colours so the toolbar stands apart from the cards behind it.
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        TabSlots(current, onSelect)
    }
}

/**
 * Three fixed, equal slots with icon and label, and one highlight pill that slides between them on
 * M3E's spatial spring. Nothing is resized or reflowed, so the toolbar and every label stay put.
 */
@Composable
private fun TabSlots(current: Tab, onSelect: (Tab) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val offset by animateDpAsState(TAB_SLOT_WIDTH * current.ordinal, motion.defaultSpatialSpec(), label = "tabHighlight")
    Box(Modifier.width(TAB_SLOT_WIDTH * Tab.entries.size).height(48.dp)) {
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(TAB_SLOT_WIDTH)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Row {
            Tab.entries.forEach { t -> TabSlot(t, selected = t == current, onClick = { onSelect(t) }) }
        }
    }
}

@Composable
private fun TabSlot(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val idle = LocalContentColor.current
    val content by animateColorAsState(if (selected) colors.onPrimary else idle, motion.defaultEffectsSpec(), label = "tabContent")
    Row(
        Modifier
            .width(TAB_SLOT_WIDTH)
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(selected, animationSpec = motion.fastEffectsSpec(), label = "tabIcon") { on ->
            Icon(painterResource(if (on) tab.iconSelected else tab.icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(
            tab.label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            // Shrinks rather than clips at large system font sizes.
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private val TAB_SLOT_WIDTH = 104.dp
