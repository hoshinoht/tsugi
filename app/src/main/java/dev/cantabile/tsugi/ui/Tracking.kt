package dev.cantabile.tsugi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.tracking.BusTrackingService
import dev.cantabile.tsugi.tracking.Tracked

/** The bus being tracked (if any) and a toggle that starts or stops alerts for a bus. */
class TrackingControl(val tracked: Tracked?, val toggle: (stopCode: String, serviceNo: String) -> Unit)

/**
 * Bus-alert toggling shared by the stop screen and the Next up card: asks for notification
 * permission the first time (Android 13+), then starts tracking once it's granted.
 */
@Composable
fun rememberTrackingControl(walkMetres: (stopCode: String) -> Int? = { null }): TrackingControl {
    val context = LocalContext.current
    val haptic = rememberToggleHaptic()
    val tracked by BusTrackingService.tracked.collectAsStateWithLifecycle()
    // Remember which bus asked, so tracking starts once permission is granted.
    var pending by rememberSaveable { mutableStateOf<List<String>?>(null) }
    // Measured here, while the app is open: the tracking service doesn't use location itself.
    fun start(stop: String, service: String) = BusTrackingService.start(context, stop, service, walkMetres(stop))
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pending
        pending = null
        if (granted && target != null) {
            start(target[0], target[1])
        } else if (!granted) {
            Toast.makeText(context, "Allow notifications to get bus alerts", Toast.LENGTH_LONG).show()
        }
    }
    return TrackingControl(tracked) { stop, service ->
        val on = tracked == Tracked(stop, service)
        haptic(!on)
        when {
            on -> BusTrackingService.stop(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> {
                pending = listOf(stop, service)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> start(stop, service)
        }
    }
}
