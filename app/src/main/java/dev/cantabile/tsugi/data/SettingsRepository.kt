package dev.cantabile.tsugi.data

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String, val nightMode: Int) {
    System("System", UiModeManager.MODE_NIGHT_AUTO),
    Light("Light", UiModeManager.MODE_NIGHT_NO),
    Dark("Dark", UiModeManager.MODE_NIGHT_YES),
}

enum class StopSort(val label: String) { Soonest("Soonest"), Number("Number"), Starred("Starred") }

val NEARBY_RADII = listOf(200, 400, 800)
val ALERT_MINUTES = listOf(1, 2, 3, 5)

/** User preferences, stored next to favourites in DataStore. */
class SettingsRepository(private val context: Context, private val store: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme")
    private val radiusKey = intPreferencesKey("nearby_radius")
    private val stopSortKey = stringPreferencesKey("stop_sort")
    private val alertKey = intPreferencesKey("alert_minutes")

    val theme: Flow<ThemeMode> = enumPref(themeKey, ThemeMode.System)
    val nearbyRadius: Flow<Int> = store.data.map { it[radiusKey] ?: 200 }.distinctUntilChanged()
    val stopSort: Flow<StopSort> = enumPref(stopSortKey, StopSort.Soonest)
    val alertMinutes: Flow<Int> = store.data.map { it[alertKey] ?: 2 }.distinctUntilChanged()

    /**
     * Also applies the theme app-wide through Android 12's per-app night mode, so status bar icons,
     * dialogs and the widget follow it. The system remembers it across restarts.
     */
    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
        context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
    }

    suspend fun setNearbyRadius(metres: Int) = store.edit { it[radiusKey] = metres }
    suspend fun setStopSort(sort: StopSort) = store.edit { it[stopSortKey] = sort.name }
    suspend fun setAlertMinutes(minutes: Int) = store.edit { it[alertKey] = minutes }

    private inline fun <reified E : Enum<E>> enumPref(key: Preferences.Key<String>, default: E): Flow<E> =
        store.data.map { prefs -> enumValues<E>().firstOrNull { it.name == prefs[key] } ?: default }.distinctUntilChanged()
}
