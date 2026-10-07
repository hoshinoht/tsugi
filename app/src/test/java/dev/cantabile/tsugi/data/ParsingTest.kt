package dev.cantabile.tsugi.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Samples are taken from the LTA DataMall API User Guide v6.10. */
class ParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun busArrival_parsesThreeBusesAndSkipsBlankOnes() {
        val body = """
            {
              "odata.metadata": "https://datamall2.mytransport.sg/ltaodataservice/v3/BusArrival",
              "BusStopCode": "83139",
              "Services": [
                {
                  "ServiceNo": "15", "Operator": "GAS",
                  "NextBus": {"OriginCode": "77009", "DestinationCode": "77009", "EstimatedArrival": "2024-08-14T16:41:48+08:00",
                    "Monitored": 1, "Latitude": "1.3154918333333334", "Longitude": "103.9059125", "VisitNumber": "1",
                    "Load": "SEA", "Feature": "WAB", "Type": "SD"},
                  "NextBus2": {"OriginCode": "77009", "DestinationCode": "77009", "EstimatedArrival": "2024-08-14T16:55:22+08:00",
                    "Monitored": 0, "Latitude": "0.0", "Longitude": "0.0", "VisitNumber": "1",
                    "Load": "LSD", "Feature": "", "Type": "DD"},
                  "NextBus3": {"OriginCode": "", "DestinationCode": "", "EstimatedArrival": "", "Monitored": 0,
                    "Latitude": "", "Longitude": "", "VisitNumber": "", "Load": "", "Feature": "", "Type": ""}
                }
              ]
            }
        """.trimIndent()

        val service = json.decodeFromString<BusArrivalResponse>(body).services.single().toDomain()

        assertEquals("15", service.serviceNo)
        assertEquals("Go-Ahead", service.operator)
        assertEquals(2, service.buses.size)
        val (first, second) = service.buses
        assertEquals(Instant.parse("2024-08-14T08:41:48Z"), first.eta)
        assertTrue(first.monitored)
        assertEquals(Load.Seats, first.load)
        assertEquals(BusType.Single, first.type)
        assertTrue(first.wheelchair)
        assertFalse(second.monitored)
        assertEquals(Load.Limited, second.load)
        assertEquals(BusType.Double, second.type)
        assertFalse(second.wheelchair)
    }

    @Test
    fun busArrival_emptyServicesMeansNothingRunning() {
        val response = json.decodeFromString<BusArrivalResponse>("""{"BusStopCode": "01012", "Services": []}""")
        assertTrue(response.services.isEmpty())
    }

    @Test
    fun trainAlerts_normal() {
        val body = """{"odata.metadata": "x", "value": {"Status": 1, "AffectedSegments": [], "Message": []}}"""
        val status = json.decodeFromString<TrainAlertsResponse>(body).value.toDomain(Instant.EPOCH)
        assertFalse(status.disrupted)
        assertTrue(status.segments.isEmpty())
    }

    @Test
    fun trainAlerts_disruptedSegmentWithFreeBusAndMessage() {
        val body = """
            {"odata.metadata": "x", "value": {
              "Status": 2,
              "AffectedSegments": [{"Line": "NEL", "Direction": "HarbourFront", "Stations": "NE9,NE8,NE7,NE6",
                "FreePublicBus": "NE9,NE8,NE7,NE6", "FreeMRTShuttle": "", "MRTShuttleDirection": ""}],
              "Message": [{"Content": "1657hrs : NEL - Additional travelling time of 20 minutes.", "CreatedDate": "2017-12-11 16:57:25"}]
            }}
        """.trimIndent()
        val status = json.decodeFromString<TrainAlertsResponse>(body).value.toDomain(Instant.EPOCH)
        assertTrue(status.disrupted)
        val segment = status.segments.single()
        assertEquals(TrainLine.NEL, segment.line)
        assertEquals(listOf("NE9", "NE8", "NE7", "NE6"), segment.stations)
        assertTrue(segment.freeBus)
        assertFalse(segment.freeShuttle)
        assertEquals(1, status.messages.size)
    }

    @Test
    fun trainLine_unknownCodeIsNull() {
        assertNull(TrainLine.of("XYZ"))
    }

    @Test
    fun serviceOrder_isNatural() {
        val sorted = listOf("851", "12e", "2", "NR1", "12", "7").sortedWith(serviceOrder)
        assertEquals(listOf("2", "7", "12", "12e", "851", "NR1"), sorted)
    }

    @Test
    fun placeName_dropsPrefixesAndExits() {
        assertEquals("Bugis Stn", placeNameFrom("Opp Bugis Stn Exit C"))
        assertEquals("Bedok Int", placeNameFrom("Bedok Int"))
        assertEquals("Raffles Hosp", placeNameFrom("Aft Raffles Hosp"))
        assertEquals("Blk 208", placeNameFrom("Blk 208"))
    }

    @Test
    fun distance_isRoughlyRight() {
        // Bugis MRT to City Hall MRT is about 1.1 km in a straight line.
        val d = distanceM(1.3006, 103.8559, 1.2931, 103.8520)
        assertTrue("got $d", d in 850..1050)
    }
}

class OneMapParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun search_mapsAndTitleCasesResults() {
        // Captured from the live endpoint on 7 Oct 2026.
        val body = """
            {"found": 1, "totalNumPages": 1, "pageNum": 1, "results": [{
              "SEARCHVAL": "ION ORCHARD", "BLK_NO": "2", "ROAD_NAME": "ORCHARD TURN", "BUILDING": "ION ORCHARD",
              "ADDRESS": "2 ORCHARD TURN ION ORCHARD SINGAPORE 238801", "POSTAL": "238801",
              "X": "27856.85205532983", "Y": "31812.97287132777",
              "LATITUDE": "1.303979741445055", "LONGITUDE": "103.832032328465"}]}
        """.trimIndent()
        val hit = json.decodeFromString<OneMapSearchResponse>(body).toHits().single()
        assertEquals("Ion Orchard", hit.name)
        assertEquals("2 Orchard Turn Ion Orchard Singapore 238801", hit.address)
        assertEquals(1.303979741445055, hit.lat, 1e-9)
    }

    @Test
    fun search_dropsResultsWithoutCoordinates() {
        val body = """{"found": 1, "results": [{"SEARCHVAL": "X", "ADDRESS": "Y", "LATITUDE": "NIL", "LONGITUDE": ""}]}"""
        assertTrue(json.decodeFromString<OneMapSearchResponse>(body).toHits().isEmpty())
    }
}

class RoutesTest {
    private val row = RouteStop(
        service = "12e", operator = "SBST", direction = 1, seq = 5, stop = "78189",
        wd = listOf("1719", "2306"), sat = listOf("-", "-"), sun = listOf("0005", "0121"),
    )

    @Test
    fun hhmmLabel_formats12Hour() {
        assertEquals("6:53 am", hhmmLabel("0653"))
        assertEquals("12:05 am", hhmmLabel("0005"))
        assertEquals("12:30 pm", hhmmLabel("1230"))
        assertEquals("11:06 pm", hhmmLabel("2306"))
        assertNull(hhmmLabel("-"))
    }

    @Test
    fun firstBus_usesTodaysTimetable() {
        // 2026-10-07 is a Wednesday, 2026-10-10 a Saturday, 2026-10-11 a Sunday.
        fun at(date: String) = java.time.ZonedDateTime.parse("${date}T12:00:00+08:00[Asia/Singapore]")
        assertEquals("5:19 pm", row.firstBusLabel(at("2026-10-07")))
        assertNull(row.firstBusLabel(at("2026-10-10")))
        assertEquals("12:05 am", row.firstBusLabel(at("2026-10-11")))
    }

    @Test
    fun index_groupsByStopAndOrdersRoutes() {
        val index = RoutesIndex(
            listOf(row.copy(seq = 2, stop = "B"), row.copy(seq = 1, stop = "A"), row.copy(service = "17", stop = "A")),
        )
        assertEquals(listOf("12e", "17"), index.servicesAt("A"))
        assertEquals(listOf("A", "B"), index.byService.getValue("12e").getValue(1).map { it.stop })
    }
}

class NextUpTest {
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private fun bus(minutes: Long) = Bus(now.plusSeconds(minutes * 60), true, Load.Seats, BusType.Single, true, "X")
    private fun svc(no: String, vararg mins: Long) = ServiceArrivals(no, "SBS Transit", mins.map { bus(it) })

    // Stop A is ~110 m from "here", stop B ~1.1 km away.
    private val here = 1.3000 to 103.8000
    private val stops = mapOf("A" to (1.3010 to 103.8000), "B" to (1.3100 to 103.8000))

    @Test
    fun prefersNearestSavedStopOverSoonerBusFarAway() {
        val pick = pickNextUp(
            listOf(NextUpCandidate("B", svc("10", 1), pinned = true), NextUpCandidate("A", svc("20", 6), pinned = true)),
            stops::get, here, now,
        )
        assertEquals("A", pick!!.stopCode)
        assertTrue(pick.distanceM!! in 100..130)
    }

    @Test
    fun skipsBusesYouCantReachInTime() {
        // ~110 m is ~1.4 min of walking, so the bus in 1 min is skipped for the one in 9.
        val pick = pickNextUp(listOf(NextUpCandidate("A", svc("20", 1, 9), pinned = true)), stops::get, here, now)
        assertEquals(now.plusSeconds(9 * 60), pick!!.bus.eta)
    }

    @Test
    fun fromAcrossTownTheWalkDoesntRuleBusesOut() {
        // ~1.1 km to stop B and no saved stop nearby: B's buses are still candidates, soonest first.
        val pick = pickNextUp(listOf(NextUpCandidate("B", svc("10", 3, 12), pinned = true)), stops::get, here, now)
        assertEquals(now.plusSeconds(3 * 60), pick!!.bus.eta)
        assertNull(pick.distanceM)
    }

    @Test
    fun withoutLocationFallsBackToSoonest() {
        val pick = pickNextUp(
            listOf(NextUpCandidate("B", svc("10", 3), pinned = true), NextUpCandidate("A", svc("20", 6), pinned = true)),
            stops::get, null, now,
        )
        assertEquals("10", pick!!.service.serviceNo)
        assertNull(pick.distanceM)
    }

    @Test
    fun atTheSameStopPinnedBusesComeFirst() {
        val pick = pickNextUp(
            listOf(NextUpCandidate("A", svc("99", 3), pinned = false), NextUpCandidate("A", svc("20", 7), pinned = true)),
            stops::get, here, now,
        )
        assertEquals("20", pick!!.service.serviceNo)
    }
}

class LabelMinutesTest {
    @Test
    fun parsesLabelsBackToMinutes() {
        assertEquals(5 * 60 + 29, labelMinutes("5:29 am"))
        assertEquals(0 * 60 + 5, labelMinutes("12:05 am"))
        assertEquals(12 * 60 + 30, labelMinutes("12:30 pm"))
        assertEquals(23 * 60 + 6, labelMinutes(hhmmLabel("2306")!!))
        assertNull(labelMinutes("soon"))
    }
}
