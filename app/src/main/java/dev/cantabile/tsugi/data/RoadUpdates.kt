package dev.cantabile.tsugi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

// ---- TrafficIncidents and PlannedBusRoutes wire format (API User Guide v6.10, §2.18 and §2.27) ----

@Serializable
data class TrafficIncidentsResponse(@SerialName("value") val value: List<TrafficIncidentDto> = emptyList())

@Serializable
data class TrafficIncidentDto(
    @SerialName("Type") val type: String = "",
    @SerialName("Latitude") val lat: Double = 0.0,
    @SerialName("Longitude") val lng: Double = 0.0,
    /** e.g. "(7/10)08:31 Heavy Traffic on KJE (towards PIE) before Choa Chu Kang Drive." */
    @SerialName("Message") val message: String = "",
)

@Serializable
data class PlannedBusRoutesResponse(@SerialName("value") val value: List<PlannedBusRouteDto> = emptyList())

@Serializable
data class PlannedBusRouteDto(
    @SerialName("ServiceNo") val serviceNo: String = "",
    /** e.g. "20250302T00:00:00+0800". */
    @SerialName("EffectiveDate") val effectiveDate: String = "",
)

// ---- Domain ----

data class TrafficIncident(val type: String, val lat: Double, val lng: Double, val text: String, val time: String?)

/** An incident on the stretch of road your saved buses use before reaching your stop. */
data class RoadIncident(val incident: TrafficIncident, val services: List<String>)

/** A saved service whose route LTA has changed (published on or after the change takes effect). */
data class RouteChange(val serviceNo: String, val effective: LocalDate)

data class RoadUpdates(val incidents: List<RoadIncident> = emptyList(), val routeChanges: List<RouteChange> = emptyList()) {
    val isEmpty get() = incidents.isEmpty() && routeChanges.isEmpty()
}

private val INCIDENT_PREFIX = Regex("""^\(\d{1,2}/\d{1,2}\)(\d{1,2}:\d{2})\s+(.*)$""")

fun TrafficIncidentDto.toDomain(): TrafficIncident {
    val m = INCIDENT_PREFIX.matchEntire(message.trim())
    return TrafficIncident(type, lat, lng, text = m?.groupValues?.get(2) ?: message.trim(), time = m?.groupValues?.get(1))
}

/** "20250302T00:00:00+0800" → 2025-03-02; null if unparseable. */
fun effectiveDate(raw: String): LocalDate? =
    raw.take(8).takeIf { it.length == 8 && it.all(Char::isDigit) }
        ?.let { runCatching { LocalDate.of(it.take(4).toInt(), it.substring(4, 6).toInt(), it.substring(6, 8).toInt()) }.getOrNull() }

/** How far up the route from your stop an incident still matters. */
const val APPROACH_STOPS = 15

/** An incident this close to a stop on the approach counts as on the route. */
const val INCIDENT_NEAR_ROUTE_M = 200

/**
 * The stops [serviceNo] passes on its way to [stopCode]: up to [APPROACH_STOPS] before it, plus the
 * stop itself, for every direction that serves it. Incidents here delay the bus you're waiting for.
 */
fun RoutesIndex.approach(stopCode: String, serviceNo: String): List<RouteStop> =
    byService[serviceNo].orEmpty().values.flatMap { route ->
        route.indices.filter { route[it].stop == stopCode }
            .flatMap { i -> route.subList((i - APPROACH_STOPS).coerceAtLeast(0), i + 1) }
    }.distinctBy { it.stop }

/**
 * Incidents within [INCIDENT_NEAR_ROUTE_M] of any stop on the approaches in [watched] (service →
 * stops), each with the services it affects. Newest first, as LTA lists them.
 */
fun roadIncidents(
    incidents: List<TrafficIncident>,
    watched: Map<String, List<RouteStop>>,
    stopLatLng: (String) -> Pair<Double, Double>?,
): List<RoadIncident> {
    val points = watched.mapValues { (_, stops) -> stops.mapNotNull { stopLatLng(it.stop) } }
    return incidents.mapNotNull { inc ->
        val services = points.filterValues { pts -> pts.any { (lat, lng) -> distanceM(inc.lat, inc.lng, lat, lng) <= INCIDENT_NEAR_ROUTE_M } }
            .keys.sortedWith(serviceOrder)
        services.takeIf { it.isNotEmpty() }?.let { RoadIncident(inc, it) }
    }
}

/** Route changes for [saved] services: the latest effective date per service. */
fun routeChanges(planned: List<PlannedBusRouteDto>, saved: Set<String>): List<RouteChange> =
    planned.filter { it.serviceNo in saved }
        .mapNotNull { dto -> effectiveDate(dto.effectiveDate)?.let { dto.serviceNo to it } }
        .groupBy({ it.first }, { it.second })
        .map { (service, dates) -> RouteChange(service, dates.max()) }
        .sortedWith(compareBy(serviceOrder) { it.serviceNo })
