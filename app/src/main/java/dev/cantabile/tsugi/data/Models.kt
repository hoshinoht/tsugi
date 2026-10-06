package dev.cantabile.tsugi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

// ---- DataMall wire format (API User Guide v6.10) ----

@Serializable
data class BusArrivalResponse(
    @SerialName("BusStopCode") val busStopCode: String = "",
    @SerialName("Services") val services: List<ServiceDto> = emptyList(),
)

@Serializable
data class ServiceDto(
    @SerialName("ServiceNo") val serviceNo: String,
    @SerialName("Operator") val operator: String = "",
    @SerialName("NextBus") val next1: NextBusDto? = null,
    @SerialName("NextBus2") val next2: NextBusDto? = null,
    @SerialName("NextBus3") val next3: NextBusDto? = null,
)

@Serializable
data class NextBusDto(
    @SerialName("DestinationCode") val destinationCode: String = "",
    @SerialName("EstimatedArrival") val estimatedArrival: String = "",
    @SerialName("Monitored") val monitored: Int = 0,
    @SerialName("VisitNumber") val visitNumber: String = "",
    @SerialName("Load") val load: String = "",
    @SerialName("Feature") val feature: String = "",
    @SerialName("Type") val type: String = "",
)

@Serializable
data class BusStopsResponse(@SerialName("value") val value: List<BusStopDto> = emptyList())

@Serializable
data class BusStopDto(
    @SerialName("BusStopCode") val code: String,
    @SerialName("RoadName") val road: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Latitude") val lat: Double = 0.0,
    @SerialName("Longitude") val lng: Double = 0.0,
)

// ---- Domain ----

@Serializable
data class BusStop(
    val code: String,
    val road: String,
    val description: String,
    val lat: Double,
    val lng: Double,
)

enum class Load(val label: String, val bars: Int) {
    Seats("Seats", 3),
    Standing("Standing", 2),
    Limited("Limited standing", 1),
    Unknown("", 0),
}

enum class BusType(val label: String) {
    Single("Single"),
    Double("Double"),
    Bendy("Bendy"),
    Unknown(""),
}

data class Bus(
    val eta: Instant,
    /** false = time is from the operator's schedule, not the bus's live position. */
    val monitored: Boolean,
    val load: Load,
    val type: BusType,
    val wheelchair: Boolean,
    val destinationCode: String,
)

data class ServiceArrivals(
    val serviceNo: String,
    val operator: String,
    /** Empty when the service is not running right now. */
    val buses: List<Bus>,
)

data class StopArrivals(
    val services: List<ServiceArrivals> = emptyList(),
    val fetchedAt: Instant? = null,
    val error: String? = null,
)

fun operatorName(code: String) = when (code) {
    "SBST" -> "SBS Transit"
    "SMRT" -> "SMRT"
    "TTS" -> "Tower Transit"
    "GAS" -> "Go-Ahead"
    else -> code
}

/** Natural order: 2, 7, 12, 12e, 851, NR1 … */
val serviceOrder: Comparator<String> = compareBy<String>(
    { it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE },
    { it },
)

fun ServiceDto.toDomain() = ServiceArrivals(
    serviceNo = serviceNo,
    operator = operatorName(operator),
    buses = listOfNotNull(next1, next2, next3).mapNotNull { it.toBus() },
)

private fun NextBusDto.toBus(): Bus? {
    if (estimatedArrival.isBlank()) return null
    val eta = runCatching { OffsetDateTime.parse(estimatedArrival).toInstant() }.getOrNull() ?: return null
    return Bus(
        eta = eta,
        monitored = monitored == 1,
        load = when (load) {
            "SEA" -> Load.Seats
            "SDA" -> Load.Standing
            "LSD" -> Load.Limited
            else -> Load.Unknown
        },
        type = when (type) {
            "SD" -> BusType.Single
            "DD" -> BusType.Double
            "BD" -> BusType.Bendy
            else -> BusType.Unknown
        },
        wheelchair = feature == "WAB",
        destinationCode = destinationCode,
    )
}

fun BusStopDto.toDomain() = BusStop(code, road, description, lat, lng)
