package dev.cantabile.tsugi.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoadUpdatesTest {
    private fun stop(service: String, seq: Int, code: String) =
        RouteStop(service, "SBST", 1, seq, code, listOf("", ""), listOf("", ""), listOf("", ""))

    @Test
    fun parsesIncidentsAndStripsTheTimestamp() {
        // Captured from the live endpoint on 7 Oct 2026.
        val body = """{"value": [{"Type": "Vehicle breakdown", "Latitude": 1.4183, "Longitude": 103.8338,
            "Message": "(7/10)08:30 Vehicle Breakdown on SLE (towards CTE) at Lentor Ave Exit."}]}"""
        val incident = Json { ignoreUnknownKeys = true }.decodeFromString<TrafficIncidentsResponse>(body).value.single().toDomain()
        assertEquals("08:30", incident.time)
        assertEquals("Vehicle Breakdown on SLE (towards CTE) at Lentor Ave Exit.", incident.text)
        assertEquals("Vehicle breakdown", incident.type)
    }

    @Test
    fun keepsOnlyIncidentsOnTheWayToYourStop() {
        // Stops ~110 m apart northwards; you wait at D. An incident near B is on 12's approach to D;
        // one near F is past your stop; one 2 km away is nowhere near.
        val coords = (0..5).associate { i -> "ABCDEF"[i].toString() to (1.3000 + i * 0.001 to 103.8000) }
        val routes = RoutesIndex("ABCDEF".mapIndexed { i, c -> stop("12", i + 1, c.toString()) })
        val approach = routes.approach("D", "12")
        assertEquals(listOf("A", "B", "C", "D"), approach.map { it.stop })
        val incidents = listOf(
            TrafficIncident("Accident", 1.3011, 103.8001, "Accident near B", "08:00"),
            TrafficIncident("Roadwork", 1.3050, 103.8000, "Roadwork at F", "08:01"),
            TrafficIncident("Heavy Traffic", 1.3200, 103.8000, "Far away", "08:02"),
        )
        val relevant = roadIncidents(incidents, mapOf("12" to approach), coords::get)
        assertEquals(listOf("Accident near B"), relevant.map { it.incident.text })
        assertEquals(listOf("12"), relevant.single().services)
    }

    @Test
    fun routeChangesOnlyForSavedServicesWithTheLatestDate() {
        val planned = listOf(
            PlannedBusRouteDto("12", "20260302T00:00:00+0800"),
            PlannedBusRouteDto("12", "20260310T00:00:00+0800"),
            PlannedBusRouteDto("99", "20260302T00:00:00+0800"),
        )
        val changes = routeChanges(planned, saved = setOf("12", "17"))
        assertEquals(listOf(RouteChange("12", LocalDate.of(2026, 3, 10))), changes)
        assertNull(effectiveDate("soon"))
        assertTrue(RoadUpdates().isEmpty)
    }
}
