package dev.cantabile.tsugi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.allStopCodes
import dev.cantabile.tsugi.tracking.BusTrackingService
import dev.cantabile.tsugi.tracking.Tracked
import dev.cantabile.tsugi.data.ServiceArrivals
import java.time.Duration
import java.time.Instant

@Composable
fun StopScreen(vm: AppViewModel, code: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val arrivals by vm.arrivals.collectAsStateWithLifecycle()
    vm.stops.collectAsStateWithLifecycle()
    PollArrivals(vm, listOf(code))
    val now = rememberNow(1_000)
    val stop = vm.stop(code)
    val data = arrivals[code]
    val haptic = rememberToggleHaptic()
    val context = LocalContext.current
    val tracked by BusTrackingService.tracked.collectAsStateWithLifecycle()
    // Remember which bus asked, so tracking starts once notification permission is granted.
    var pendingTrack by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val service = pendingTrack
        pendingTrack = null
        if (granted && service != null) {
            BusTrackingService.start(context, code, service)
        } else if (!granted) {
            Toast.makeText(context, "Allow notifications to get bus alerts", Toast.LENGTH_LONG).show()
        }
    }
    val toggleTracking: (String) -> Unit = { service ->
        val on = tracked == Tracked(code, service)
        haptic(!on)
        when {
            on -> BusTrackingService.stop(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                pendingTrack = service
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> BusTrackingService.start(context, code, service)
        }
    }
    var showSave by rememberSaveable { mutableStateOf(false) }
    // Filled star if this stop is saved in any form: whole stop, a pinned bus, or part of a place.
    val saved = favourites.any { it.allStopCodes.contains(code) }

    if (showSave) {
        SaveSheet(vm, code, data?.services.orEmpty().map { it.serviceNo }, onDismiss = { showSave = false })
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") }
                },
                actions = {
                    FilledIconToggleButton(
                        checked = saved,
                        onCheckedChange = { showSave = true },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            painterResource(if (saved) R.drawable.ic_star else R.drawable.ic_star_outline),
                            contentDescription = "Save options",
                        )
                    }
                },
            )
        },
    ) { inner ->
        RefreshableBox(onRefresh = { vm.refresh(listOf(code), force = true) }, modifier = Modifier.padding(top = inner.calculateTopPadding())) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = inner.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stop?.description ?: code, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(stop?.road, code).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        RefreshProgress(data?.fetchedAt, now)
                    }
                }

                when {
                    data?.fetchedAt == null && data?.error != null -> item {
                        MessageCard("Couldn't load arrivals: ${data.error}")
                    }
                    data == null || data.fetchedAt == null -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    data.services.isEmpty() -> item {
                        MessageCard("No buses are running at this stop right now.")
                    }
                    else -> items(data.services, key = { it.serviceNo }) { service ->
                        val fav = Favourite.Service(code, service.serviceNo)
                        ServiceCard(
                            Modifier.animateItem(), vm, service, now,
                            pinned = fav in favourites,
                            onTogglePin = {
                                haptic(fav !in favourites)
                                vm.toggleFavourite(fav)
                            },
                            tracking = tracked == Tracked(code, service.serviceNo),
                            onToggleTracking = { toggleTracking(service.serviceNo) },
                        )
                    }
                }
            }
        }
    }
}

/** Wavy bar that fills up until the next 20 s refresh. */
@Composable
private fun RefreshProgress(fetchedAt: Instant?, now: Instant) {
    val elapsed = fetchedAt?.let { Duration.between(it, now).toMillis() } ?: 0L
    val progress = (elapsed.toFloat() / REFRESH_MS).coerceIn(0f, 1f)
    val remaining = ((REFRESH_MS - elapsed) / 1000).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
        Text(
            if (fetchedAt == null) "…" else "${remaining}s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServiceCard(
    modifier: Modifier,
    vm: AppViewModel,
    service: ServiceArrivals,
    now: Instant,
    pinned: Boolean,
    onTogglePin: () -> Unit,
    tracking: Boolean,
    onToggleTracking: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val running = service.buses.isNotEmpty()
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceBadge(service.serviceNo, active = running)
                Column(Modifier.weight(1f)) {
                    Text(
                        service.buses.firstOrNull()?.let { vm.stop(it.destinationCode)?.description } ?: "Not operating now",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(service.operator, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (running || tracking) {
                    IconToggleButton(checked = tracking, onCheckedChange = { onToggleTracking() }) {
                        Icon(
                            painterResource(if (tracking) R.drawable.ic_bell_filled else R.drawable.ic_bell),
                            contentDescription = if (tracking) "Stop alerts for ${service.serviceNo}" else "Alert me when ${service.serviceNo} is near",
                            tint = if (tracking) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                }
                IconToggleButton(checked = pinned, onCheckedChange = { onTogglePin() }) {
                    Icon(
                        painterResource(if (pinned) R.drawable.ic_star else R.drawable.ic_star_outline),
                        contentDescription = if (pinned) "Unpin ${service.serviceNo}" else "Pin ${service.serviceNo}",
                        tint = if (pinned) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
            if (running) {
                Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    service.buses.forEachIndexed { i, bus ->
                        ArrivalTile(bus, now, rowShape(i, 3), Modifier.weight(1f))
                    }
                    repeat(3 - service.buses.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
