package dev.cantabile.tsugi.tracking

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.cantabile.tsugi.MainActivity
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.NextUpCandidate
import dev.cantabile.tsugi.data.allStopCodes
import dev.cantabile.tsugi.data.pickNextUp
import dev.cantabile.tsugi.data.toDomain
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

/**
 * Quick Settings tile showing the "Next up" bus (same rules as the Favourites card): updated each
 * time the shade opens. Tap to open the app.
 */
class NextBusTileService : TileService() {
    private val scope = MainScope()

    override fun onStartListening() {
        scope.launch { update() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun update() {
        val tile = qsTile ?: return
        val c = (application as TsugiApplication).container
        runCatching { c.stops.ensureLoaded() }
        val favourites = c.favourites.favourites.first()
        if (favourites.isEmpty()) {
            show(tile, "Next bus", "Save a stop in Tsugi", Tile.STATE_INACTIVE)
            return
        }
        val codes = favourites.flatMap { it.allStopCodes }.distinct()
        val arrivals = coroutineScope {
            codes.map { code -> async { code to runCatching { c.api.busArrival(code)?.services.orEmpty().map { it.toDomain() } }.getOrNull() } }
                .awaitAll()
        }.mapNotNull { (code, services) -> services?.let { code to it } }.toMap()
        val pinned = favourites.filterIsInstance<Favourite.Service>()
        val candidates = buildList {
            pinned.forEach { f -> arrivals[f.stopCode]?.firstOrNull { it.serviceNo == f.serviceNo }?.let { add(NextUpCandidate(f.stopCode, it, pinned = true)) } }
            favourites.filterNot { it is Favourite.Service }.flatMap { it.allStopCodes }.distinct().forEach { code ->
                arrivals[code].orEmpty().forEach { add(NextUpCandidate(code, it, pinned = false)) }
            }
        }
        val here = withTimeoutOrNull(3_000) { c.location.current() }
        val now = Instant.now()
        val next = pickNextUp(candidates, { code -> c.stops[code]?.let { it.lat to it.lng } }, here?.let { it.latitude to it.longitude }, now)
        if (next == null) {
            show(tile, "Next bus", if (arrivals.isEmpty()) "Offline" else "No buses running", Tile.STATE_INACTIVE)
            return
        }
        val minutes = Duration.between(now, next.bus.eta).toMinutes()
        show(
            tile,
            "${next.service.serviceNo} · ${if (minutes < 1) "Arr" else "$minutes min"}",
            c.stops[next.stopCode]?.description ?: next.stopCode,
            Tile.STATE_ACTIVE,
        )
    }

    private fun show(tile: Tile, label: String, subtitle: String, state: Int) {
        tile.label = label
        tile.subtitle = subtitle
        tile.state = state
        tile.updateTile()
    }
}
