package dev.cantabile.tsugi.data

/** Closer than this, you're at the stop already. */
private const val AT_STOP_M = 60

/** Streets wind: walking routes are about 30% longer than the straight line. */
private const val DETOUR = 1.3

/**
 * Whole minutes to walk [metres] (straight line) at ~80 m/min allowing for [DETOUR], rounded up;
 * 0 when unknown or you're at the stop.
 */
fun walkMinutes(metres: Int?): Int =
    if (metres == null || metres < AT_STOP_M) 0 else ((metres * DETOUR).toInt() + WALK_M_PER_MIN - 1) / WALK_M_PER_MIN

/** A heads-up for a tracked bus: when to set off, or that it's close. */
data class Heads(val title: String, val text: String)

/**
 * The "approaching" alert for a tracked bus [minutes] away, once it's due: [leadMinutes] (the
 * user's setting) before you need to leave, counting [walk] minutes to the stop. Null if not due yet.
 */
fun approachAlert(serviceNo: String, stopName: String, minutes: Long, walk: Int, leadMinutes: Long, nextMinutes: Long?): Heads? {
    if (minutes > leadMinutes + walk) return null
    return when {
        walk == 0 -> Heads("$serviceNo in $minutes min", "Head to $stopName now")
        minutes >= walk -> Heads("Leave now for $serviceNo", "$walk min walk to $stopName · bus in $minutes min")
        else -> Heads(
            "$serviceNo in $minutes min",
            "It's a $walk min walk to $stopName, so you may miss it" + (nextMinutes?.let { " · next in $it min" } ?: ""),
        )
    }
}
