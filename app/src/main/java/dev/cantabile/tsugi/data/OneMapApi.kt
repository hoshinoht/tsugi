package dev.cantabile.tsugi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class OneMapSearchResponse(
    val found: Int = 0,
    val results: List<OneMapResult> = emptyList(),
)

@Serializable
data class OneMapResult(
    @SerialName("SEARCHVAL") val name: String = "",
    @SerialName("ADDRESS") val address: String = "",
    @SerialName("POSTAL") val postal: String = "",
    @SerialName("LATITUDE") val lat: String = "",
    @SerialName("LONGITUDE") val lng: String = "",
)

/** A building, address or postal code from OneMap. */
data class AddressHit(val name: String, val address: String, val lat: Double, val lng: Double)

/** SLA OneMap search. The search endpoint works without an access token. */
class OneMapApi(private val http: OkHttpClient, private val json: Json) {
    suspend fun search(query: String): List<AddressHit> = withContext(Dispatchers.IO) {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("searchVal", query.trim())
            .addQueryParameter("returnGeom", "Y")
            .addQueryParameter("getAddrDetails", "Y")
            .addQueryParameter("pageNum", "1")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OneMap returned HTTP ${response.code}")
            json.decodeFromString<OneMapSearchResponse>(response.body.string()).toHits()
        }
    }

    private companion object {
        const val SEARCH_URL = "https://www.onemap.gov.sg/api/common/elastic/search"
    }
}

fun OneMapSearchResponse.toHits(): List<AddressHit> = results
    .mapNotNull { r ->
        val lat = r.lat.toDoubleOrNull() ?: return@mapNotNull null
        val lng = r.lng.toDoubleOrNull() ?: return@mapNotNull null
        AddressHit(name = r.name.toTitleCase(), address = r.address.toTitleCase(), lat = lat, lng = lng)
    }
    .distinctBy { it.name to it.address }

/** OneMap returns SHOUTING CASE; "ION ORCHARD" → "Ion Orchard". */
private fun String.toTitleCase(): String =
    lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
