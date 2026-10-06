package dev.cantabile.tsugi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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
}

class FavouritesRepository(
    private val store: DataStore<Preferences>,
    private val json: Json,
) {
    private val key = stringPreferencesKey("favourites")

    val favourites: Flow<List<Favourite>> = store.data.map { prefs -> decode(prefs[key]) }

    suspend fun toggle(favourite: Favourite) {
        store.edit { prefs ->
            val current = decode(prefs[key])
            val next = if (favourite in current) current - favourite else current + favourite
            prefs[key] = json.encodeToString(next)
        }
    }

    private fun decode(raw: String?): List<Favourite> =
        raw?.let { runCatching { json.decodeFromString<List<Favourite>>(it) }.getOrNull() }.orEmpty()
}
