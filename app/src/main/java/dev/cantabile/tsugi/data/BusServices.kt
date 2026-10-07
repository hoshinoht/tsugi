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
import java.io.File
import java.time.LocalTime

// ---- BusServices wire format (API User Guide v6.10, §2.2) ----

@Serializable
data class BusServicesResponse(@SerialName("value") val value: List<BusServiceDto> = emptyList())

@Serializable
data class BusServiceDto(
    @SerialName("ServiceNo") val serviceNo: String,
    @SerialName("Operator") val operator: String = "",
    @SerialName("Direction") val direction: Int = 1,
    @SerialName("Category") val category: String = "",
    @SerialName("OriginCode") val originCode: String = "",
    @SerialName("DestinationCode") val destinationCode: String = "",
    @SerialName("AM_Peak_Freq") val amPeak: String = "",
    @SerialName("AM_Offpeak_Freq") val amOffpeak: String = "",
    @SerialName("PM_Peak_Freq") val pmPeak: String = "",
    @SerialName("PM_Offpeak_Freq") val pmOffpeak: String = "",
    @SerialName("LoopDesc") val loopDesc: String = "",
)

// ---- Domain ----

/** One direction of one service: its category, where it loops, and how often it runs. */
@Serializable
data class ServiceInfo(
    val service: String,
    val direction: Int,
    val category: String,
    val loop: String,
    /** Minutes between buses as LTA writes them ("5-08"), for AM peak, AM off-peak, PM peak, PM off-peak. */
    val freq: List<String>,
)

fun BusServiceDto.toInfo() = ServiceInfo(
    service = serviceNo,
    direction = direction,
    category = category.trim(),
    loop = loopDesc.trim(),
    freq = listOf(amPeak, amOffpeak, pmPeak, pmOffpeak),
)

/**
 * LTA's frequency bands: AM peak 6:30–8:30, AM off-peak 8:31–4:59 pm, PM peak 5–7 pm, PM off-peak
 * after 7 pm. Early mornings before 6:30 use the AM peak figure, the closest band LTA gives.
 */
fun frequencyBand(time: LocalTime): Int = when {
    time.hour < SERVICE_DAY_START_HOUR -> 3
    time < LocalTime.of(8, 31) -> 0
    time < LocalTime.of(17, 0) -> 1
    time < LocalTime.of(19, 1) -> 2
    else -> 3
}

/** "5-08" → "every 5–8 min"; "10" → "every 10 min"; blank, "-" or zeros → null. */
fun frequencyLabel(raw: String): String? {
    val parts = raw.split('-').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
    return when {
        parts.isEmpty() -> null
        parts.size == 1 || parts.first() == parts.last() -> "every ${parts.first()} min"
        else -> "every ${parts.min()}–${parts.max()} min"
    }
}

/** LTA publishes weekday frequencies only, so other days are labelled as such. */
fun ServiceInfo.frequencyAt(time: LocalTime, day: DayType = DayType.Weekday): String? =
    freq.getOrNull(frequencyBand(time))?.let(::frequencyLabel)?.let { if (day == DayType.Weekday) it else "$it on weekdays" }

/** "TRUNK" → "Trunk"; "FLAT FEE $1.10" stays as written apart from case. */
fun categoryLabel(category: String): String? =
    category.takeIf { it.isNotBlank() }?.lowercase()?.replaceFirstChar { it.uppercase() }

class ServiceInfoIndex(rows: List<ServiceInfo>) {
    private val byKey = rows.associateBy { it.service to it.direction }
    operator fun get(service: String, direction: Int): ServiceInfo? = byKey[service to direction] ?: byKey[service to 1]
}

/** Every bus service's category and frequency, cached as one JSON file and refreshed weekly. */
class ServiceInfoRepository(
    dir: File,
    private val api: LtaApi,
    private val json: Json,
) {
    private val file = File(dir, "bus_services.json")
    private val mutex = Mutex()
    private val _index = MutableStateFlow<ServiceInfoIndex?>(null)
    val index: StateFlow<ServiceInfoIndex?> = _index.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock {
        if (_index.value != null) return@withLock
        val cached = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<List<ServiceInfo>>(file.readText()) }.getOrNull()
        }
        cached?.let { _index.value = ServiceInfoIndex(it) }
        val stale = System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (cached == null || stale) {
            runCatching { download() }
                .onSuccess { fresh ->
                    _index.value = ServiceInfoIndex(fresh)
                    withContext(Dispatchers.IO) { file.writeText(json.encodeToString(fresh)) }
                }
                .onFailure { if (cached == null) throw it }
        }
    }

    private suspend fun download(): List<ServiceInfo> {
        val all = mutableListOf<ServiceInfo>()
        var skip = 0
        while (true) {
            val page = api.busServices(skip)
            page.mapTo(all) { it.toInfo() }
            if (page.size < PAGE_SIZE) break
            skip += PAGE_SIZE
        }
        return all
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
