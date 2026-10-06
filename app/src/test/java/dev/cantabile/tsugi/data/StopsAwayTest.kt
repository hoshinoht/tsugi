package dev.cantabile.tsugi.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StopsAwayTest {
    private fun stop(seq: Int, code: String) = RouteStop("10", "SBST", 1, seq, code, listOf("", ""), listOf("", ""), listOf("", ""))

    // Stops ~110 m apart along a line of latitude; "A" is visited twice (a loop).
    private val coords = mapOf(
        "A" to (1.3000 to 103.8000), "B" to (1.3010 to 103.8000), "C" to (1.3020 to 103.8000),
        "D" to (1.3030 to 103.8000), "E" to (1.3040 to 103.8000),
    )
    private val routes = RoutesIndex(listOf("A", "B", "C", "D", "E", "A").mapIndexed { i, c -> stop(i + 1, c) })
    private fun bus(lat: Double?, visit: Int = 1) =
        Bus(Instant.EPOCH, true, Load.Seats, BusType.Single, true, "A", lat = lat, lng = lat?.let { 103.8000 }, visit = visit)

    @Test
    fun countsStopsBetweenTheBusAndYours() {
        assertEquals(3, routes.stopsAway("D", "10", bus(1.30001), coords::get))
        assertEquals(0, routes.stopsAway("D", "10", bus(1.30302), coords::get))
    }

    @Test
    fun loopsUseTheVisitNumber() {
        assertEquals(1, routes.stopsAway("A", "10", bus(1.30401, visit = 2), coords::get))
    }

    @Test
    fun unknownWhenNoPositionOrFarOffRoute() {
        assertNull(routes.stopsAway("D", "10", bus(null), coords::get))
        assertNull(routes.stopsAway("D", "10", bus(1.3200), coords::get))
        assertNull(routes.stopsAway("Z", "10", bus(1.3000), coords::get))
    }

    @Test
    fun parsesPositionAndVisitFromBusArrival() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val dto = json.decodeFromString<NextBusDto>(
            """{"EstimatedArrival": "2024-08-14T16:41:48+08:00", "Latitude": "1.3154918", "Longitude": "103.9059125", "VisitNumber": "2"}""",
        )
        val bus = BusArrivalResponse(services = listOf(ServiceDto("15", next1 = dto))).services.single().toDomain().buses.single()
        assertEquals(1.3154918, bus.lat!!, 1e-9)
        assertEquals(2, bus.visit)
        val scheduled = json.decodeFromString<NextBusDto>("""{"EstimatedArrival": "2024-08-14T16:41:48+08:00", "Latitude": "0.0", "Longitude": "0.0"}""")
        assertNull(ServiceDto("15", next1 = scheduled).toDomain().buses.single().lat)
    }

    @Test
    fun labels() {
        assertEquals("Almost here", stopsAwayLabel(0))
        assertEquals("1 stop away", stopsAwayLabel(1))
        assertEquals("4 stops away", stopsAwayLabel(4))
    }
}

class LeaveNowTest {
    @Test
    fun walkMinutesRoundUp() {
        assertEquals(0, walkMinutes(null))
        assertEquals(0, walkMinutes(40))
        assertEquals(1, walkMinutes(80))
        assertEquals(4, walkMinutes(250))
    }

    @Test
    fun atTheStopAlertsAtTheLeadTime() {
        assertNull(approachAlert("12", "Stop", minutes = 3, walk = 0, leadMinutes = 2, nextMinutes = null))
        assertEquals("12 in 2 min", approachAlert("12", "Stop", 2, 0, 2, null)!!.title)
    }

    @Test
    fun walkingMovesTheAlertEarlier() {
        assertNull(approachAlert("12", "Stop", minutes = 8, walk = 5, leadMinutes = 2, nextMinutes = null))
        val heads = approachAlert("12", "Stop", minutes = 7, walk = 5, leadMinutes = 2, nextMinutes = null)!!
        assertEquals("Leave now for 12", heads.title)
        assertEquals("5 min walk to Stop · bus in 7 min", heads.text)
    }

    @Test
    fun warnsWhenTheBusIsTooClose() {
        val heads = approachAlert("12", "Stop", minutes = 3, walk = 5, leadMinutes = 2, nextMinutes = 14)!!
        assertEquals("It's a 5 min walk to Stop, so you may miss it · next in 14 min", heads.text)
    }
}
