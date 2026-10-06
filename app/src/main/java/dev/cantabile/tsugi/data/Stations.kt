package dev.cantabile.tsugi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

// ---- Bundled station data (assets/stations.json, built by scripts/build_stations.py) ----

@Serializable
data class StationExit(val name: String, val lat: Double, val lng: Double)

/** An MRT or LRT station. Interchanges have one code per line, e.g. NS24, NE6, CC1. */
@Serializable
data class Station(
    val name: String,
    val codes: List<String>,
    val lat: Double,
    val lng: Double,
    val exits: List<StationExit> = emptyList(),
) {
    /** The lines through this station, in code order, without repeats (EW4 and CG both mean East West). */
    val lines: List<TrainLine> get() = codes.mapNotNull(::lineOfCode).distinct()

    /** "Dhoby Ghaut MRT", "Bukit Panjang LRT"; interchanges with MRT lines are MRT. */
    val title: String get() = "$name ${if (lines.any { !it.lrt }) "MRT" else "LRT"}"
}

/** Station codes renamed since LTA's feeds were written: Bayfront and Marina Bay joined the Circle Line. */
private val CODE_ALIASES = mapOf("CE1" to "CC34", "CE2" to "CC33")

fun canonicalCode(code: String): String = code.trim().uppercase().let { CODE_ALIASES[it] ?: it }

/** "NS24" → North South Line; LRT loop codes ("SE3", "PTC") → their LRT. */
fun lineOfCode(code: String): TrainLine? = when (canonicalCode(code).takeWhile(Char::isLetter)) {
    "NS" -> TrainLine.NSL
    "EW", "CG" -> TrainLine.EWL
    "NE" -> TrainLine.NEL
    "CC", "CE" -> TrainLine.CCL
    "DT" -> TrainLine.DTL
    "TE" -> TrainLine.TEL
    "BP" -> TrainLine.BPL
    "SE", "SW", "STC" -> TrainLine.STL
    "PE", "PW", "PTC" -> TrainLine.PTL
    else -> null
}

/**
 * The TrainLine parameter LTA's crowd endpoints expect for a station code. The Changi Airport branch
 * (CG) has its own code; the LRTs use SLRT and PLRT.
 */
fun crowdLineOf(code: String): String? {
    val c = canonicalCode(code)
    return when (c.takeWhile(Char::isLetter)) {
        "CG" -> "CGL"
        "SE", "SW", "STC" -> "SLRT"
        "PE", "PW", "PTC" -> "PLRT"
        else -> lineOfCode(c)?.code
    }
}

data class NearbyStation(val station: Station, val distanceM: Int)

class StationIndex(val all: List<Station>) {
    private val byCode: Map<String, Station> = all.flatMap { s -> s.codes.map { canonicalCode(it) to s } }.toMap()

    operator fun get(code: String): Station? = byCode[canonicalCode(code)]

    /** Distance to a station's nearest exit (or its centre when exits aren't known). */
    fun distanceTo(station: Station, lat: Double, lng: Double): Int =
        (station.exits.map { distanceM(lat, lng, it.lat, it.lng) } + distanceM(lat, lng, station.lat, station.lng)).min()

    fun nearby(lat: Double, lng: Double, radiusM: Int): List<NearbyStation> =
        all.map { NearbyStation(it, distanceTo(it, lat, lng)) }.filter { it.distanceM <= radiusM }.sortedBy { it.distanceM }

    /** Stations whose name contains [query], or whose code starts with it, best matches first. */
    fun search(query: String, limit: Int = 6): List<Station> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.mapNotNull { s ->
            val name = s.name.lowercase()
            val rank = when {
                s.codes.any { it.equals(q, ignoreCase = true) } -> 0
                name.startsWith(q) -> 1
                name.split(' ').any { it.startsWith(q) } -> 2
                name.contains(q) -> 3
                q.length >= 2 && s.codes.any { it.lowercase().startsWith(q) } -> 4
                else -> null
            }
            rank?.let { it to s }
        }.sortedWith(compareBy({ it.first }, { it.second.name })).take(limit).map { it.second }
    }

    companion object {
        fun parse(json: Json, text: String) = StationIndex(json.decodeFromString<List<Station>>(text))
    }
}

/** The bundled station list, read once from the APK's assets on first use. */
class StationRepository(private val read: () -> String, private val json: Json) {
    private val mutex = Mutex()
    private val _index = MutableStateFlow<StationIndex?>(null)
    val index: StateFlow<StationIndex?> = _index.asStateFlow()

    suspend fun ensureLoaded(): StationIndex = mutex.withLock {
        _index.value ?: withContext(Dispatchers.IO) { StationIndex.parse(json, read()) }.also { _index.value = it }
    }
}

/** Stations within this distance of a bus stop are "at" it, for line badges. */
const val STATION_NEAR_STOP_M = 200

/** Bus stops within this distance of a station's exits are listed on its screen. */
const val STOPS_NEAR_STATION_M = 250

// ---- Station crowd density (API User Guide §2.13, §2.14) ----

enum class CrowdLevel(val label: String, val bars: Int) {
    Low("Not crowded", 3),
    Moderate("Moderately crowded", 2),
    High("Crowded", 1),
    Unknown("", 0);

    companion object {
        fun of(raw: String) = when (raw.trim().lowercase()) {
            "l" -> Low
            "m" -> Moderate
            "h" -> High
            else -> Unknown
        }
    }
}

@Serializable
data class CrowdRealTimeResponse(@SerialName("value") val value: List<CrowdRealTimeDto> = emptyList())

@Serializable
data class CrowdRealTimeDto(
    @SerialName("Station") val station: String = "",
    @SerialName("StartTime") val startTime: String = "",
    @SerialName("EndTime") val endTime: String = "",
    @SerialName("CrowdLevel") val crowdLevel: String = "",
)

@Serializable
data class CrowdForecastResponse(@SerialName("value") val value: List<CrowdForecastDayDto> = emptyList())

@Serializable
data class CrowdForecastDayDto(
    @SerialName("Date") val date: String = "",
    @SerialName("Stations") val stations: List<CrowdForecastStationDto> = emptyList(),
)

@Serializable
data class CrowdForecastStationDto(
    @SerialName("Station") val station: String = "",
    @SerialName("Interval") val intervals: List<CrowdIntervalDto> = emptyList(),
)

@Serializable
data class CrowdIntervalDto(
    @SerialName("Start") val start: String = "",
    @SerialName("CrowdLevel") val crowdLevel: String = "",
)

/** One station's crowding: now, and forecast half-hours from now on. */
data class Crowding(val now: CrowdLevel, val forecast: List<Pair<Instant, CrowdLevel>>)

/** LTA's times come with an offset ("2026-10-07T09:40:00+08:00"); tolerate ones without. */
fun parseLtaTime(raw: String): Instant? =
    runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw).atZone(SINGAPORE).toInstant() }.getOrNull()

/** Real-time levels by canonical station code. */
fun List<CrowdRealTimeDto>.byStation(): Map<String, CrowdLevel> =
    associate { canonicalCode(it.station) to CrowdLevel.of(it.crowdLevel) }

/** Forecast half-hours by canonical station code, keeping only those that haven't ended before [now]. */
fun List<CrowdForecastDayDto>.forecastByStation(now: ZonedDateTime): Map<String, List<Pair<Instant, CrowdLevel>>> {
    val cutoff = now.toInstant().minusSeconds(30 * 60)
    return flatMap { it.stations }
        .groupBy { canonicalCode(it.station) }
        .mapValues { (_, days) ->
            days.flatMap { it.intervals }
                .mapNotNull { i -> parseLtaTime(i.start)?.let { it to CrowdLevel.of(i.crowdLevel) } }
                .filter { it.first.isAfter(cutoff) && it.second != CrowdLevel.Unknown }
                .sortedBy { it.first }
        }
}

// ---- Lift maintenance (API User Guide §2.12, FacilitiesMaintenance v2) ----

@Serializable
data class LiftMaintenanceResponse(@SerialName("value") val value: List<LiftMaintenanceDto> = emptyList())

@Serializable
data class LiftMaintenanceDto(
    @SerialName("Line") val line: String = "",
    @SerialName("StationCode") val stationCode: String = "",
    @SerialName("StationName") val stationName: String = "",
    @SerialName("LiftID") val liftId: String = "",
    @SerialName("LiftDesc") val liftDesc: String = "",
)
