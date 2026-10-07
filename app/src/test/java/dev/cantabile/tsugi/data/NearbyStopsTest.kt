package dev.cantabile.tsugi.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class NearbyStopsTest {
    /** A grid of fake stops over Singapore, about 110 m apart. */
    private val stops = (0 until 300).flatMap { i ->
        (0 until 60).map { j -> BusStop("%05d".format(i * 60 + j), "Road $i", "Stop $i/$j", 1.24 + i * 0.001, 103.62 + j * 0.0066) }
    }

    /** The old full scan: measure every stop. */
    private fun bruteForce(lat: Double, lng: Double, radiusM: Int, limit: Int) =
        stops.map { NearbyStop(it, distanceM(lat, lng, it.lat, it.lng)) }
            .filter { it.distanceM <= radiusM }
            .sortedBy { it.distanceM }
            .take(limit)

    @Test
    fun boxCheckFindsExactlyWhatAFullScanFinds() {
        val random = Random(7)
        repeat(200) {
            val lat = 1.25 + random.nextDouble() * 0.2
            val lng = 103.65 + random.nextDouble() * 0.3
            for (radius in listOf(80, 200, 400, 800)) {
                assertEquals(bruteForce(lat, lng, radius, 25), nearbyStops(stops, lat, lng, radius, 25))
            }
        }
    }
}
