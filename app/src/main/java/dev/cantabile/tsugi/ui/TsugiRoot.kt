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
import androidx.compose.foundation.layout.BoxWithConstraints
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

/**
 * A full-screen layer over the tabs, saved as a string: "settings", "stop:<code>", "service:<no>",
 * "place:<id>" or "station:<code>". Layers stack, so a stop can open its station and the station
 * another stop; back pops one.
 */
private object Layer {
    const val SETTINGS = "settings"
    fun stop(code: String) = "stop:$code"
    /** [from] is the stop it was opened from, so the route can show buses heading there. */
    fun service(no: String, from: String? = null) = "service:$no" + (from?.let { "@$it" } ?: "")
    fun place(id: String) = "place:$id"
    fun station(code: String) = "station:$code"
}

enum class Tab(val label: String, val icon: Int, val iconSelected: Int) {
    Saved("Saved", R.drawable.ic_star_outline, R.drawable.ic_star),
    Nearby("Nearby", R.drawable.ic_place, R.drawable.ic_place),
    Search("Search", R.drawable.ic_search, R.drawable.ic_search),
}

/** Tabs plus a stack of full-screen layers. A few screens don't need a navigation library. */
@Composable
fun TsugiRoot(
    requestedStop: String? = null,
    requestedTab: String? = null,
    requestedPlace: String? = null,
    onRequestHandled: () -> Unit = {},
    vm: AppViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Saved) }
    var layers by rememberSaveable { mutableStateOf(listOf<String>()) }
    // Opening what's already on top does nothing, so double taps don't stack copies.
    val push: (String) -> Unit = { layer -> if (layers.lastOrNull() != layer) layers = layers + layer }
    val pop: () -> Unit = { layers = layers.dropLast(1) }
    LaunchedEffect(requestedStop, requestedTab, requestedPlace) {
        if (requestedStop != null || requestedTab != null || requestedPlace != null) {
            Tab.entries.firstOrNull { it.name == requestedTab }?.let {
                tab = it
                layers = emptyList()
            }
            requestedPlace?.let { push(Layer.place(it)) }
            requestedStop?.let { push(Layer.stop(it)) }
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
    val overlayOpen = layers.isNotEmpty()
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = overlayOpen) { events ->
        try {
            events.collect { backProgress = it.progress }
            pop()
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
    val openStop: (String) -> Unit = { push(Layer.stop(it)) }
    val openStation: (String) -> Unit = { push(Layer.station(it)) }

    @Composable
    fun LayerContent(layer: String) {
        val arg = layer.substringAfter(':', "")
        Box(peek) {
            when (layer.substringBefore(':')) {
                "settings" -> SettingsScreen(vm, onBack = pop)
                "stop" -> StopScreen(vm, arg, onBack = pop, onOpenStop = openStop, onOpenStation = openStation, onOpenService = { push(Layer.service(it, from = arg)) })
                "service" -> ServiceScreen(vm, arg.substringBefore('@'), onBack = pop, onOpenStop = openStop, fromStop = arg.substringAfter('@', "").ifEmpty { null })
                "place" -> PlaceScreen(vm, arg, onBack = pop, onOpenStop = openStop)
                "station" -> StationScreen(vm, arg, onBack = pop, onOpenStop = openStop)
            }
        }
    }

    /** [open] shows a layer from the tabs: stacked on one pane, or replacing the detail pane on two. */
    @Composable
    fun Tabs(open: (String) -> Unit) {
        // The toolbar slides away while scrolling down and comes back on scroll up.
        val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)
        Box(Modifier.fillMaxSize().nestedScroll(toolbarScroll)) {
            val openSettings = { open(Layer.SETTINGS) }
            val fromTabStop: (String) -> Unit = { open(Layer.stop(it)) }
            val fromTabStation: (String) -> Unit = { open(Layer.station(it)) }
            tabStates.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Saved -> FavouritesScreen(vm, fromTabStop, onOpenPlace = { open(Layer.place(it)) }, onOpenSettings = openSettings, onOpenStation = fromTabStation)
                    Tab.Nearby -> NearbyScreen(vm, fromTabStop, requestLocation, onOpenSettings = openSettings, onOpenStation = fromTabStation)
                    Tab.Search -> SearchScreen(
                        vm, fromTabStop,
                        onOpenService = { open(Layer.service(it)) },
                        onOpenStation = fromTabStation,
                        onShowNearby = {
                            vm.showNearbyAt(it)
                            tab = Tab.Nearby
                        },
                        onOpenSettings = openSettings,
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints {
            if (maxWidth >= TWO_PANE_MIN_WIDTH) {
                // Wide screens: the tabs stay on the left and whatever you open shows on the right.
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) { Tabs(open = { layers = listOf(it) }) }
                    Surface(
                        Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp, end = 8.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        AnimatedContent(
                            targetState = layers.lastOrNull(),
                            transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                            label = "detail",
                        ) { layer ->
                            if (layer != null) {
                                LayerContent(layer)
                            } else {
                                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Open a stop, station or place to see it here.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = layers.lastOrNull(),
                    transitionSpec = { fadeIn(enterSpec) togetherWith fadeOut(exitSpec) },
                    label = "layer",
                ) { layer -> if (layer != null) LayerContent(layer) else Tabs(open = push) }
            }
        }
    }
}

/** From this width (Material's "expanded" window class) the tabs and the open screen sit side by side. */
private val TWO_PANE_MIN_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 400.dp

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
