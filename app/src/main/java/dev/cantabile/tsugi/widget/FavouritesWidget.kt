package dev.cantabile.tsugi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.cantabile.tsugi.MainActivity
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.ServiceArrivals
import dev.cantabile.tsugi.data.firstBusLabel
import dev.cantabile.tsugi.data.labelMinutes
import dev.cantabile.tsugi.data.toDomain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_ROWS = 4

private data class WidgetRow(val serviceNo: String, val destination: String, val next: String, val after: String)

/**
 * Up to four rows: pinned buses first, then buses from saved stops and places, soonest first.
 * Times are minutes at fetch time, so the header shows when that was; tap refresh to update.
 */
class FavouritesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as TsugiApplication).container
        val favourites = c.favourites.favourites.first()
        runCatching { c.stops.ensureLoaded() }
        val now = Instant.now()

        val codes = favourites.flatMap { if (it is Favourite.Place) it.stopCodes else listOf(it.stopCode) }.distinct()
        val arrivals: Map<String, List<ServiceArrivals>> = coroutineScope {
            codes.map { code ->
                async { code to runCatching { c.api.busArrival(code)?.services.orEmpty().map { it.toDomain() } }.getOrNull() }
            }.awaitAll()
        }.mapNotNull { (code, services) -> services?.let { code to it } }.toMap()
        val failed = codes.isNotEmpty() && arrivals.isEmpty()

        val pinned = favourites.filterIsInstance<Favourite.Service>().mapNotNull { f ->
            arrivals[f.stopCode]?.firstOrNull { it.serviceNo == f.serviceNo }
        }
        val others = favourites.filterNot { it is Favourite.Service }
            .flatMap { f -> (if (f is Favourite.Place) f.stopCodes else listOf(f.stopCode)).flatMap { arrivals[it].orEmpty() } }
        val rows = (pinned + others.sortedBy { it.buses.firstOrNull()?.eta ?: Instant.MAX })
            .filter { it.buses.isNotEmpty() }
            .distinctBy { it.serviceNo }
            .take(MAX_ROWS)
            .map { s ->
                fun label(eta: Instant) = Duration.between(now, eta).toMinutes().let { if (it < 1) "Arr" else "$it min" }
                WidgetRow(
                    serviceNo = s.serviceNo,
                    destination = c.stops[s.buses.first().destinationCode]?.description ?: "",
                    next = label(s.buses.first().eta),
                    after = s.buses.getOrNull(1)?.let { label(it.eta) } ?: "",
                )
            }
        // Overnight nothing runs: say when the first saved bus starts instead of an empty widget.
        val firstBus = if (rows.isEmpty() && favourites.isNotEmpty()) {
            runCatching { c.routes.ensureLoaded() }
            val routes = c.routes.index.value
            val pinnedPairs = favourites.filterIsInstance<Favourite.Service>().map { it.stopCode to it.serviceNo }.toSet()
            val wholeCodes = favourites.filterNot { it is Favourite.Service }.flatMap { if (it is Favourite.Place) it.stopCodes else listOf(it.stopCode) }.toSet()
            routes?.byStop?.filterKeys { it in codes }?.flatMap { (code, stops) ->
                stops.filter { (code to it.service) in pinnedPairs || code in wholeCodes }
                    .mapNotNull { r -> r.firstBusLabel()?.let { label -> labelMinutes(label)?.let { Triple(it, label, r.service) } } }
            }?.minByOrNull { it.first }?.let { (_, label, service) -> "No buses running · first bus $label ($service)" }
        } else null
        val updated = DateTimeFormatter.ofPattern("H:mm").withZone(ZoneId.systemDefault()).format(now)

        provideContent {
            GlanceTheme {
                Content(rows, if (failed) "Offline · $updated" else "Updated $updated", empty = favourites.isEmpty(), idleMessage = firstBus)
            }
        }
    }

    @Composable
    private fun Content(rows: List<WidgetRow>, status: String, empty: Boolean, idleMessage: String?) {
        val colors = GlanceTheme.colors
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tsugi", style = TextStyle(color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text(status, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                    modifier = GlanceModifier.size(32.dp).padding(6.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            when {
                empty -> Text("Save a stop or bus in Tsugi to see it here.", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
                rows.isEmpty() -> Text(idleMessage ?: "No buses running right now.", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
                else -> rows.forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.height(3.dp))
                    Row(
                        GlanceModifier.fillMaxWidth().background(colors.surfaceVariant).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            r.serviceNo,
                            style = TextStyle(color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            modifier = GlanceModifier.background(colors.secondaryContainer).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        Text(r.destination, style = TextStyle(color = colors.onSurface, fontSize = 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        Text(r.next, style = TextStyle(color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp))
                        if (r.after.isNotEmpty()) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(r.after, style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        FavouritesWidget().update(context, glanceId)
    }
}

class FavouritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavouritesWidget()
}

/** Call after favourites change so the widget doesn't show stale stops. */
suspend fun refreshFavouritesWidget(context: Context) = FavouritesWidget().updateAll(context)
