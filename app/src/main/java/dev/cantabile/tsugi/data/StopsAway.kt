package dev.cantabile.tsugi.data

/** Further than this from every stop before yours, a bus's position is too unreliable to place. */
private const val MAX_SNAP_M = 600

/**
 * How many stops [bus] is from [stopCode] on [serviceNo]'s route, by snapping its reported position
 * to the nearest stop it hasn't passed yet. Null when the bus has no position, the route isn't
 * known, or the position is too far from the route to trust.
 */
fun RoutesIndex.stopsAway(stopCode: String, serviceNo: String, bus: Bus, stopLatLng: (String) -> Pair<Double, Double>?): Int? {
    val lat = bus.lat ?: return null
    val lng = bus.lng ?: return null
    val route = byService[serviceNo]?.values?.firstOrNull { r -> r.any { it.stop == stopCode } } ?: return null
    // Loop services pass some stops twice; the bus's visit number says which pass it's on.
    val passes = route.indices.filter { route[it].stop == stopCode }
    val target = passes.getOrNull(bus.visit - 1) ?: passes.firstOrNull() ?: return null
    val nearest = (0..target).mapNotNull { i ->
        stopLatLng(route[i].stop)?.let { (sLat, sLng) -> i to distanceM(lat, lng, sLat, sLng) }
    }.minByOrNull { it.second } ?: return null
    if (nearest.second > MAX_SNAP_M) return null
    return target - nearest.first
}

/** "Almost here", "1 stop away", "4 stops away". */
fun stopsAwayLabel(stops: Int): String = when (stops) {
    0 -> "Almost here"
    1 -> "1 stop away"
    else -> "$stops stops away"
}
