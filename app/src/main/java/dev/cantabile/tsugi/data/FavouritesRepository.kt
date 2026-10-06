package dev.cantabile.tsugi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed interface Favourite {
    val stopCode: String

    /** Every service at a stop, shown together in one card. */
    @Serializable
    @SerialName("stop")
    data class Stop(override val stopCode: String) : Favourite

    /** One service at one stop, shown as its own row. */
    @Serializable
    @SerialName("service")
    data class Service(override val stopCode: String, val serviceNo: String) : Favourite

    /** A named group of stops, e.g. an interchange plus the stops at the MRT exits. */
    @Serializable
    @SerialName("place")
    data class Place(val id: String, val name: String, val stopCodes: List<String>) : Favourite {
        override val stopCode: String get() = stopCodes.firstOrNull().orEmpty()
    }
}

/** "Opp Bugis Stn Exit C" → "Bugis Stn": drops position prefixes and exit suffixes. */
fun placeNameFrom(stopDescription: String): String = stopDescription
    .replace(Regex("^(Opp|Aft|Bef)\\s+", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+Exit\\s+\\w+$", RegexOption.IGNORE_CASE), "")
    .trim()

/** Every stop a favourite needs arrivals for. */
val Favourite.allStopCodes: List<String>
    get() = if (this is Favourite.Place) stopCodes else listOf(stopCode)

class FavouritesRepository(
    private val store: DataStore<Preferences>,
    private val json: Json,
) {
    private val key = stringPreferencesKey("favourites")

    val favourites: Flow<List<Favourite>> = store.data.map { prefs -> decode(prefs[key]) }.distinctUntilChanged()


    suspend fun toggle(favourite: Favourite) = update { if (favourite in it) it - favourite else it + favourite }

    suspend fun update(transform: (List<Favourite>) -> List<Favourite>) {
        store.edit { prefs -> prefs[key] = json.encodeToString(transform(decode(prefs[key]))) }
    }

    private fun decode(raw: String?): List<Favourite> =
        raw?.let { runCatching { json.decodeFromString<List<Favourite>>(it) }.getOrNull() }.orEmpty()
}
