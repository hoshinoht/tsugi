package dev.cantabile.tsugi.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.BusStop
import dev.cantabile.tsugi.data.MapPin
import dev.cantabile.tsugi.data.distanceM
import kotlin.math.roundToInt

private const val SHOW_YOU_WITHIN_M = 300

/**
 * Where the stop is: a OneMap static map with a red pin on the stop and a blue one for you when
 * you're close, so you can tell which side of the road it's on. Tap for walking directions.
 * Also links the stop across the road (same road, within 80 m).
 */
@Composable
fun StopMap(vm: AppViewModel, stop: BusStop, onOpenStop: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val night = isSystemInDarkTheme()
    val here by vm.here.collectAsStateWithLifecycle()
    // Round your position to ~10 m so small GPS jitter doesn't refetch the map.
    val you = here?.let { (it.latitude * 1e4).roundToInt() / 1e4 to (it.longitude * 1e4).roundToInt() / 1e4 }
        ?.takeIf { (lat, lng) -> distanceM(lat, lng, stop.lat, stop.lng) <= SHOW_YOU_WITHIN_M }
    val map by produceState<ImageBitmap?>(null, stop.code, night, you) {
        val pins = listOfNotNull(
            MapPin(stop.lat, stop.lng, "220,40,40", "S"),
            you?.let { (lat, lng) -> MapPin(lat, lng, "30,110,255", "Y") },
        )
        val (lat, lng) = you?.let { (stop.lat + it.first) / 2 to (stop.lng + it.second) / 2 } ?: (stop.lat to stop.lng)
        val zoom = if (you != null && distanceM(you.first, you.second, stop.lat, stop.lng) > 120) 17 else 18
        value = vm.staticMap(lat, lng, zoom, night, pins)
    }
    val across = vm.acrossTheRoad(stop.code)

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClickLabel = "Walking directions") {
                    val uri = "https://www.google.com/maps/dir/?api=1&destination=${stop.lat},${stop.lng}&travelmode=walking".toUri()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val image = map
            if (image != null) {
                Image(image, contentDescription = "Map of ${stop.description}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                LoadingIndicator()
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_place), null, Modifier.size(16.dp))
                    Text(if (you != null) "  You · Stop" else "  Directions", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (across != null) {
            AssistChip(
                onClick = { onOpenStop(across.stop.code) },
                label = { Text("Across the road: ${across.stop.description} · ${across.stop.code}") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(18.dp)) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
