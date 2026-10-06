package dev.cantabile.tsugi.tracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

data class Tracked(val stopCode: String, val serviceNo: String)

/**
 * Follows one bus at one stop while the app is in the background: a live countdown notification,
 * a heads-up alert when it's the user's chosen number of minutes away and again when it's arriving, then stops itself.
 * One bus at a time; tracking another replaces it.
 */
class BusTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                job?.cancel()
                finish()
            }
            ACTION_START -> {
                val stop = intent.getStringExtra(EXTRA_STOP)
                val service = intent.getStringExtra(EXTRA_SERVICE)
                if (stop == null || service == null) {
                    finish()
                    return START_NOT_STICKY
                }
                val target = Tracked(stop, service)
                TrackingNotifications.ensureChannels(this)
                val first = TrackingNotifications.ongoing(this, target, null)
                // The special-use type exists from Android 14; earlier versions take no type.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(TrackingNotifications.ONGOING_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(TrackingNotifications.ONGOING_ID, first)
                }
                _tracked.value = target
                job?.cancel()
                job = scope.launch { track(target) }
            }
        }
        // If the system kills us, don't restart: stale tracking is worse than none.
        return START_NOT_STICKY
    }

    private suspend fun track(target: Tracked) {
        val c = (application as TsugiApplication).container
        runCatching { c.stops.ensureLoaded() }
        val stopName = c.stops[target.stopCode]?.description ?: target.stopCode
        val approachMinutes = c.settings.alertMinutes.first().toLong()
        val started = Instant.now()
        var startSeconds: Long? = null
        var approachSent = false
        var arrivingAt: Instant? = null
        var noBusSince: Instant? = null

        while (currentCoroutineContext().isActive) {
            val now = Instant.now()
            if (Duration.between(started, now) > MAX_TRACKING) break

            val service = runCatching {
                c.api.busArrival(target.stopCode)?.services?.firstOrNull { it.serviceNo == target.serviceNo }?.toDomain()
            }.getOrNull()
            val bus = service?.buses?.firstOrNull()
            if (bus == null) {
                val since = noBusSince ?: now.also { noBusSince = it }
                if (Duration.between(since, now) > NO_BUS_GIVE_UP) {
                    TrackingNotifications.alert(this, target, "Stopped tracking ${target.serviceNo}", "No live arrival info at $stopName.")
                    break
                }
                delay(30_000)
                continue
            }
            noBusSince = null

            val seconds = Duration.between(now, bus.eta).seconds.coerceAtLeast(0)
            val minutes = seconds / 60
            val total = (startSeconds ?: seconds.coerceAtLeast(60).also { startSeconds = it }).toFloat()
            val state = LiveState(
                minutes = minutes,
                progress = (100 * (1 - seconds / total)).toInt().coerceIn(0, 100),
                destination = c.stops[bus.destinationCode]?.description,
                stopName = stopName,
                loadLabel = bus.load.label,
                nextMinutes = service.buses.getOrNull(1)?.let { Duration.between(now, it.eta).toMinutes() },
            )
            TrackingNotifications.ensureChannels(this)
            getSystemService(NotificationManager::class.java)
                .notify(TrackingNotifications.ONGOING_ID, TrackingNotifications.ongoing(this, target, state))

            if (seconds < 60 && arrivingAt == null) {
                TrackingNotifications.alert(this, target, "${target.serviceNo} is arriving", "At $stopName${state.destination?.let { " · to $it" } ?: ""}")
                arrivingAt = now
                approachSent = true
            } else if (!approachSent && minutes <= approachMinutes) {
                TrackingNotifications.alert(this, target, "${target.serviceNo} in $minutes min", "Head to $stopName now")
                approachSent = true
            }
            // Give the arriving bus a couple of minutes, then stop.
            if (arrivingAt != null && Duration.between(arrivingAt, now) > Duration.ofMinutes(2)) break

            delay(if (minutes > 10) 60_000 else 20_000)
        }
        finish()
    }

    private fun finish() {
        _tracked.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        _tracked.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.cantabile.tsugi.action.TRACK"
        const val ACTION_STOP = "dev.cantabile.tsugi.action.STOP_TRACKING"
        private const val EXTRA_STOP = "stop"
        private const val EXTRA_SERVICE = "service"
        private val MAX_TRACKING: Duration = Duration.ofMinutes(90)
        private val NO_BUS_GIVE_UP: Duration = Duration.ofMinutes(10)

        private val _tracked = MutableStateFlow<Tracked?>(null)
        /** The bus being tracked right now, if any (process-wide, for the UI's bell state). */
        val tracked: StateFlow<Tracked?> = _tracked.asStateFlow()

        fun start(context: Context, stopCode: String, serviceNo: String) {
            context.startForegroundService(
                Intent(context, BusTrackingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STOP, stopCode)
                    .putExtra(EXTRA_SERVICE, serviceNo),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BusTrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
