package dev.cantabile.tsugi

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.cantabile.tsugi.data.FavouritesRepository
import dev.cantabile.tsugi.data.LocationProvider
import dev.cantabile.tsugi.data.LtaApi
import dev.cantabile.tsugi.data.OneMapApi
import dev.cantabile.tsugi.data.RouteRepository
import dev.cantabile.tsugi.data.ServiceInfoRepository
import dev.cantabile.tsugi.data.SettingsRepository
import dev.cantabile.tsugi.data.StationRepository
import dev.cantabile.tsugi.data.StopRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore("prefs")

class TsugiApplication : Application() {
    val container by lazy { AppContainer(this) }
}

/** Hand-rolled DI: a handful of singletons doesn't need a framework. */
class AppContainer(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val api = LtaApi(http, json, BuildConfig.LTA_ACCOUNT_KEY)
    val oneMap = OneMapApi(http, json)
    val stops = StopRepository(context.filesDir, api, json)
    val routes = RouteRepository(context.filesDir, api, json)
    val serviceInfo = ServiceInfoRepository(context.filesDir, api, json)
    val stations = StationRepository({ context.assets.open("stations.json").bufferedReader().use { it.readText() } }, json)
    val favourites = FavouritesRepository(context.dataStore, json)
    val settings = SettingsRepository(context, context.dataStore)
    val location = LocationProvider(context)
}
