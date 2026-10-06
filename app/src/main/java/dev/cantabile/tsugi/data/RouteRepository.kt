package dev.cantabile.tsugi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// ---- BusRoutes wire format (API User Guide v6.10, §2.3) ----

@Serializable
data class BusRoutesResponse(@SerialName("value") val value: List<BusRouteDto> = emptyList())

@Serializable
data class BusRouteDto(
    @SerialName("ServiceNo") val serviceNo: String,
    @SerialName("Operator") val operator: String = "",
    @SerialName("Direction") val direction: Int = 1,
    @SerialName("StopSequence") val stopSequence: Int = 0,
    @SerialName("BusStopCode") val stopCode: String,
    @SerialName("WD_FirstBus") val wdFirst: String = "",
    @SerialName("WD_LastBus") val wdLast: String = "",
    @SerialName("SAT_FirstBus") val satFirst: String = "",
    @SerialName("SAT_LastBus") val satLast: String = "",
    @SerialName("SUN_FirstBus") val sunFirst: String = "",
    @SerialName("SUN_LastBus") val sunLast: String = "",
)

/** One stop on one route, kept compact for the on-device cache. First/last bus as "HHMM". */
@Serializable
data class RouteStop(
    val service: String,
    val operator: String,
    val direction: Int,
    val seq: Int,
    val stop: String,
    val wd: List<String>,
    val sat: List<String>,
    val sun: List<String>,
)

fun BusRouteDto.toRouteStop() = RouteStop(
    service = serviceNo,
    operator = operator,
    direction = direction,
    seq = stopSequence,
    stop = stopCode,
    wd = listOf(wdFirst, wdLast),
    sat = listOf(satFirst, satLast),
    sun = listOf(sunFirst, sunLast),
)

class RoutesIndex(rows: List<RouteStop>) {
    /** Every route passing each stop. */
    val byStop: Map<String, List<RouteStop>> = rows.groupBy { it.stop }

    /** Service → direction → stops in order. */
    val byService: Map<String, Map<Int, List<RouteStop>>> =
        rows.groupBy { it.service }.mapValues { (_, r) -> r.groupBy { it.direction }.mapValues { (_, d) -> d.sortedBy { it.seq } } }

    fun servicesAt(stop: String): List<String> = byStop[stop].orEmpty().map { it.service }.distinct()
}

/** The scheduled first bus for [stop] on today's timetable, e.g. "6:53 am"; public holidays run Sunday's. */
fun RouteStop.firstBusLabel(now: ZonedDateTime = ZonedDateTime.now(SINGAPORE)): String? =
    times(dayType(now.toLocalDate())).getOrNull(0)?.let(::hhmmLabel)

/** Today's scheduled last bus at [stop], e.g. "11:42 pm". */
fun RouteStop.lastBusLabel(now: ZonedDateTime = ZonedDateTime.now(SINGAPORE)): String? =
    lastBusAt(now)?.toLocalTime()?.let(::timeLabel)

/** "0653" → "6:53 am"; anything that isn't four digits → null. */
fun hhmmLabel(hhmm: String): String? = parseHhmm(hhmm)?.let(::timeLabel)

/** 18:05 → "6:05 pm". */
fun timeLabel(time: LocalTime): String {
    val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
    return "%d:%02d %s".format(hour, time.minute, if (time.hour < 12) "am" else "pm")
}

/** "5:29 am" → minutes after midnight, for comparing first-bus times; null if unparseable. */
fun labelMinutes(label: String): Int? {
    val m = Regex("""^(\d{1,2}):(\d{2}) (am|pm)$""").matchEntire(label) ?: return null
    val (h, min, half) = m.destructured
    return (h.toInt() % 12 + if (half == "pm") 12 else 0) * 60 + min.toInt()
}

val SINGAPORE: ZoneId = ZoneId.of("Asia/Singapore")

/**
 * All ~26,000 route stops, cached as one JSON file and refreshed weekly. Used to list services
 * that aren't running right now (LTA's arrivals feed omits them) and to search by bus number.
 */
class RouteRepository(
    dir: File,
    private val api: LtaApi,
    private val json: Json,
) {
    private val file = File(dir, "bus_routes.json")
    private val mutex = Mutex()
    private val _index = MutableStateFlow<RoutesIndex?>(null)
    val index: StateFlow<RoutesIndex?> = _index.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock {
        if (_index.value != null) return@withLock
        val cached = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<List<RouteStop>>(file.readText()) }.getOrNull()
        }
        cached?.let { _index.value = RoutesIndex(it) }
        val stale = System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (cached == null || stale) {
            runCatching { download() }
                .onSuccess { fresh ->
                    _index.value = RoutesIndex(fresh)
                    withContext(Dispatchers.IO) { file.writeText(json.encodeToString(fresh)) }
                }
                .onFailure { if (cached == null) throw it }
        }
    }

    /** A few pages at a time with retries: firing all ~54 at once gets throttled. */
    private suspend fun download(): List<RouteStop> = coroutineScope {
        val all = mutableListOf<RouteStop>()
        var page = 0
        while (true) {
            val batch = (page until page + PARALLEL_PAGES)
                .map { p -> async { fetchPage(p * PAGE_SIZE) } }
                .awaitAll()
            batch.forEach { rows -> rows.mapTo(all) { it.toRouteStop() } }
            if (batch.any { it.size < PAGE_SIZE }) break
            page += PARALLEL_PAGES
        }
        all
    }

    private suspend fun fetchPage(skip: Int): List<BusRouteDto> {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            runCatching { return api.busRoutes(skip) }.onFailure { lastError = it }
            delay(1_000L * (attempt + 1))
        }
        throw lastError ?: IllegalStateException("BusRoutes page $skip failed")
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val PARALLEL_PAGES = 4
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
