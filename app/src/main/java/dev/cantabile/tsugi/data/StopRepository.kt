package dev.cantabile.tsugi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class NearbyStop(val stop: BusStop, val distanceM: Int)

/**
 * All ~5,000 bus stops, cached as one JSON file and refreshed weekly.
 * LTA updates this dataset ad hoc, so a week-old copy is fine.
 */
class StopRepository(
    dir: File,
    private val api: LtaApi,
    private val json: Json,
) {
    private val file = File(dir, "bus_stops.json")
    private val mutex = Mutex()
    private val _stops = MutableStateFlow<List<BusStop>>(emptyList())
    val stops: StateFlow<List<BusStop>> = _stops.asStateFlow()
    private var byCode: Map<String, BusStop> = emptyMap()

    operator fun get(code: String): BusStop? = byCode[code]

    suspend fun ensureLoaded() = mutex.withLock {
        if (_stops.value.isNotEmpty()) return@withLock
        val cached = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<List<BusStop>>(file.readText()) }.getOrNull()
        }
        cached?.let(::publish)
        val stale = System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (cached == null || stale) {
            runCatching { download() }
                .onSuccess { fresh ->
                    publish(fresh)
                    withContext(Dispatchers.IO) { file.writeText(json.encodeToString(fresh)) }
                }
                .onFailure { if (cached == null) throw it }
        }
    }

    fun nearby(lat: Double, lng: Double, radiusM: Int, limit: Int = 25): List<NearbyStop> =
        nearbyStops(_stops.value, lat, lng, radiusM, limit)

    /** See [searchStops]. */
    fun search(query: String, limit: Int = 50): List<BusStop> = searchStops(_stops.value, query, limit)

    private fun publish(list: List<BusStop>) {
        byCode = list.associateBy { it.code }
        _stops.value = list
    }

    private suspend fun download(): List<BusStop> {
        val all = mutableListOf<BusStop>()
        var skip = 0
        while (true) {
            val page = api.busStops(skip)
            if (page.isEmpty()) break
            page.mapTo(all) { it.toDomain() }
            skip += PAGE_SIZE
        }
        return all
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** The stops within [radiusM] of a point, nearest first. */
fun nearbyStops(stops: List<BusStop>, lat: Double, lng: Double, radiusM: Int, limit: Int = 25): List<NearbyStop> {
    // A cheap box check first, so only the handful of stops that could be in range are measured.
    // The margin keeps it a superset: distances round down to whole metres, so 400.9 m is "400".
    val dLat = (radiusM * 1.01 + 2) / METRES_PER_DEGREE
    val dLng = dLat / cos(Math.toRadians(lat))
    return stops.asSequence()
        .filter { abs(it.lat - lat) <= dLat && abs(it.lng - lng) <= dLng }
        .map { NearbyStop(it, distanceM(lat, lng, it.lat, it.lng)) }
        .filter { it.distanceM <= radiusM }
        .sortedBy { it.distanceM }
        .take(limit)
        .toList()
}

/** Metres in a degree of latitude (and of longitude at the equator, which Singapore nearly is). */
private const val METRES_PER_DEGREE = 6_371_000 * Math.PI / 180

/** Equirectangular approximation; accurate to well under 1% at Singapore's scale. */
fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
    val x = Math.toRadians(lng2 - lng1) * cos(Math.toRadians((lat1 + lat2) / 2))
    val y = Math.toRadians(lat2 - lat1)
    return (sqrt(x * x + y * y) * 6_371_000).toInt()
}
