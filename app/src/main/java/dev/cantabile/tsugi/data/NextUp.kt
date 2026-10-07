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

/** Another bus has to be at least this much sooner to replace the current Next up. */
private val STICKY: Duration = Duration.ofMinutes(2)

/**
 * The best buses to show on the Next up card, best first (at most [limit], one per stop and service):
 * 1. If [here] is known, the nearest saved stop within [NEAR_STOP_M] (in 100 m buckets, so stops at
 *    the same junction tie), preferring pinned buses, then the soonest catchable bus; then other stops.
 * 2. Otherwise, pinned buses first, then the soonest catchable bus across all saved stops.
 * A bus is catchable if it arrives no earlier than the walk to its stop (~80 m/min); only stops you
 * could walk to (within [NEAR_STOP_M]) count the walk. The first entry is sticky: [previous] keeps its
 * place while still coming, unless another bus is at least [STICKY] sooner.
 */
fun rankNextUp(
    candidates: List<NextUpCandidate>,
    stopLatLng: (String) -> Pair<Double, Double>?,
    here: Pair<Double, Double>?,
    now: Instant,
    /** The last first pick as (stop code, service number). */
    previous: Pair<String, String>? = null,
    limit: Int = 3,
): List<NextUp> {
    val options = candidates.mapNotNull { c ->
        val distance = here?.let { h -> stopLatLng(c.stopCode)?.let { s -> distanceM(h.first, h.second, s.first, s.second) } }
        // Only stops you could walk to count the walk; from further away (e.g. across town) any
        // upcoming bus is fair game, otherwise every bus looks uncatchable.
        val walkable = distance?.takeIf { it <= NEAR_STOP_M } ?: 0
        val walk = Duration.ofSeconds(walkable * 60L / WALK_M_PER_MIN)
        val bus = c.service.buses.firstOrNull { !it.eta.isBefore(now.plus(walk)) } ?: return@mapNotNull null
        Option(c, bus, distance?.takeIf { it <= NEAR_STOP_M })
    }.distinctBy { it.candidate.stopCode to it.candidate.service.serviceNo }
    val nearFirst = options.any { it.nearDistance != null }
    val ranked = options.sortedWith(
        compareBy<Option>(
            { if (nearFirst) (it.nearDistance?.div(DISTANCE_BUCKET_M) ?: Int.MAX_VALUE) else 0 },
            { !it.candidate.pinned },
            { it.bus.eta },
        ),
    )
    val best = ranked.firstOrNull() ?: return emptyList()
    // Arrival estimates jitter by tens of seconds each refresh, so two buses a minute apart would keep
    // swapping places. Stay with the previous pick (same stop group) unless another is clearly sooner.
    val kept = previous?.let { (stop, service) ->
        ranked.firstOrNull { it.candidate.stopCode == stop && it.candidate.service.serviceNo == service }
            ?.takeIf { prev ->
                prev.nearDistance?.div(DISTANCE_BUCKET_M) == best.nearDistance?.div(DISTANCE_BUCKET_M) &&
                    prev.bus.eta.isBefore(best.bus.eta.plus(STICKY))
            }
    }
    val first = kept ?: best
    return (listOf(first) + (ranked - first)).take(limit).map {
        NextUp(it.candidate.stopCode, it.candidate.service, it.bus, it.nearDistance.takeIf { _ -> nearFirst })
    }
}

/** The single best bus; see [rankNextUp]. */
fun pickNextUp(
    candidates: List<NextUpCandidate>,
    stopLatLng: (String) -> Pair<Double, Double>?,
    here: Pair<Double, Double>?,
    now: Instant,
    previous: Pair<String, String>? = null,
): NextUp? = rankNextUp(candidates, stopLatLng, here, now, previous, limit = 1).firstOrNull()

private data class Option(val candidate: NextUpCandidate, val bus: Bus, val nearDistance: Int?)
