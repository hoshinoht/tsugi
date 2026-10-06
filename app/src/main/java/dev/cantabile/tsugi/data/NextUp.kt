package dev.cantabile.tsugi.data

import java.time.Duration
import java.time.Instant

/** A service at a saved stop that could become the "Next up" card. */
data class NextUpCandidate(val stopCode: String, val service: ServiceArrivals, val pinned: Boolean)

data class NextUp(
    val stopCode: String,
    val service: ServiceArrivals,
    /** The first bus you can still catch: may be the second bus if the first leaves before you'd get there. */
    val bus: Bus,
    /** Distance from you to the stop, when chosen by proximity. */
    val distanceM: Int?,
)

const val NEAR_STOP_M = 400
const val WALK_M_PER_MIN = 80
private const val DISTANCE_BUCKET_M = 100

/**
 * Picks the "Next up" bus:
 * 1. If [here] is known, the nearest saved stop within [NEAR_STOP_M] (in 100 m buckets, so stops at
 *    the same junction tie), preferring pinned buses, then the soonest catchable bus.
 * 2. Otherwise, the soonest catchable bus across all saved stops.
 * A bus is catchable if it arrives no earlier than the walk to its stop (~80 m/min).
 */
fun pickNextUp(
    candidates: List<NextUpCandidate>,
    stopLatLng: (String) -> Pair<Double, Double>?,
    here: Pair<Double, Double>?,
    now: Instant,
): NextUp? {
    val options = candidates.mapNotNull { c ->
        val distance = here?.let { h -> stopLatLng(c.stopCode)?.let { s -> distanceM(h.first, h.second, s.first, s.second) } }
        val walk = Duration.ofSeconds((distance ?: 0) * 60L / WALK_M_PER_MIN)
        val bus = c.service.buses.firstOrNull { !it.eta.isBefore(now.plus(walk)) } ?: return@mapNotNull null
        Triple(c, bus, distance)
    }
    val near = options.filter { (_, _, d) -> d != null && d <= NEAR_STOP_M }
    val best = if (near.isNotEmpty()) {
        near.minWith(compareBy<Triple<NextUpCandidate, Bus, Int?>>({ it.third!! / DISTANCE_BUCKET_M }, { !it.first.pinned }, { it.second.eta }))
    } else {
        options.minWithOrNull(compareBy({ !it.first.pinned }, { it.second.eta }))
    } ?: return null
    val (candidate, bus, distance) = best
    return NextUp(candidate.stopCode, candidate.service, bus, distance?.takeIf { near.isNotEmpty() })
}
