package dev.cantabile.tsugi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.cardId
import dev.cantabile.tsugi.data.inCardOrder
import dev.cantabile.tsugi.ui.theme.TsugiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One choice for a widget: what it shows, and the [WIDGET_TARGET] value that means it. */
private data class WidgetChoice(val title: String, val subtitle: String, val target: String)

/**
 * Chooses what a home-screen widget shows: all favourites (the default, so setup can be skipped),
 * one saved stop, or one place. Opens when a widget is added and from the widget's reconfigure option.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val c = (application as TsugiApplication).container

        setContent {
            TsugiTheme {
                val scope = rememberCoroutineScope()
                var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
                LaunchedEffect(Unit) {
                    runCatching { c.stops.ensureLoaded() }
                    val favourites = inCardOrder(c.favourites.favourites.first(), c.settings.cardOrder.first())
                    val singles = favourites.map { f -> if (f is Favourite.Service) Favourite.Stop(f.stopCode) else f }.distinct().map { f ->
                        when (f) {
                            is Favourite.Place -> WidgetChoice(f.name, "Place · ${f.stopCodes.size} stops", f.cardId)
                            else -> WidgetChoice(c.stops[f.stopCode]?.description ?: f.stopCode, "Stop ${f.stopCode}", "stop:${f.stopCode}")
                        }
                    }
                    choices = listOf(WidgetChoice("All favourites", "Pinned buses first, then the soonest", "")) + singles
                }
                ConfigScreen(choices, onCancel = ::finish) { choice ->
                    scope.launch {
                        // Only configure our own widgets; anything else (this activity is exported) just closes.
                        val ours = AppWidgetManager.getInstance(this@WidgetConfigActivity).getAppWidgetInfo(widgetId)?.provider?.className ==
                            FavouritesWidgetReceiver::class.java.name
                        val glanceId = if (ours) runCatching { GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId) }.getOrNull() else null
                        if (glanceId != null) {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[WIDGET_TARGET] = choice.target }
                            FavouritesWidget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, result)
                        }
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(choices: List<WidgetChoice>?, onCancel: () -> Unit, onPick: (WidgetChoice) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = { Text("Widget shows") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = { IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_close), "Cancel") } },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val list = choices.orEmpty()
            itemsIndexed(list, key = { _, c -> c.target }) { i, choice ->
                SegmentedListItem(
                    onClick = { onPick(choice) },
                    shapes = ListItemDefaults.segmentedShapes(i, list.size),
                    colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainer),
                    supportingContent = { Text(choice.subtitle) },
                ) { Text(choice.title) }
            }
            if (choices != null && list.size == 1) {
                item {
                    Text(
                        "Save stops or places in Tsugi to show just one of them here.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
