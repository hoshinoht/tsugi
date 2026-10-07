package dev.cantabile.tsugi.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime

class StationsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** The real bundled file, so a bad regeneration fails here rather than on a phone. */
    private val index: StationIndex by lazy {
        // Unit tests run from the app module's directory.
        StationIndex.parse(json, File("src/main/assets/stations.json").readText())
    }

    @Test
    fun bundledStationsLoadAndEveryCodeHasALine() {
        assertTrue(index.all.size > 150)
        val unknown = index.all.flatMap { it.codes }.filter { lineOfCode(it) == null }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun interchangesAndAliases() {
        val dhoby = index["NE6"]!!
        assertEquals("Dhoby Ghaut", dhoby.name)
        assertEquals(listOf(TrainLine.NSL, TrainLine.NEL, TrainLine.CCL), dhoby.lines)
        assertEquals("Dhoby Ghaut MRT", dhoby.title)
        assertEquals("Bayfront", index["CE1"]!!.name)
        assertEquals("Bukit Panjang", index["bp6"]!!.name)
    }

    @Test
    fun lrtStationsAreTitledLrt() {
        assertEquals("Fajar LRT", index["BP10"]!!.title)
        assertEquals("Punggol MRT", index["PTC"]!!.title)
    }

    @Test
    fun searchByNameAndCode() {
        assertEquals("Bishan", index.search("bishan").first().name)
        assertEquals("City Hall", index.search("ns25").first().name)
        assertTrue(index.search("hall").any { it.name == "City Hall" })
        assertTrue(index.search("").isEmpty())
    }

    @Test
    fun nearbyUsesExits() {
        val bugis = index["EW12"]!!
        val near = index.nearby(bugis.lat, bugis.lng, 100)
        assertEquals("Bugis", near.first().station.name)
        assertEquals(0, near.first().distanceM)
    }

    @Test
    fun crowdLines() {
        assertEquals("NSL", crowdLineOf("NS1"))
        assertEquals("CGL", crowdLineOf("CG2"))
        assertEquals("CCL", crowdLineOf("CE1"))
        assertEquals("SLRT", crowdLineOf("SE3"))
        assertEquals("PLRT", crowdLineOf("PTC"))
        assertEquals("BPL", crowdLineOf("BP6"))
        assertNull(crowdLineOf("XX1"))
        assertEquals(TrainLine.STL, TrainLine.of("SLRT"))
    }

    @Test
    fun parsesCrowding() {
        val realtime = json.decodeFromString<CrowdRealTimeResponse>(
            """{"value": [{"Station": "EW13", "StartTime": "2026-10-07T09:40:00+08:00", "EndTime": "2026-10-07T09:50:00+08:00", "CrowdLevel": "h"},
                          {"Station": "CE1", "StartTime": "", "EndTime": "", "CrowdLevel": "NA"}]}""",
        ).value.byStation()
        assertEquals(CrowdLevel.High, realtime["EW13"])
        assertEquals(CrowdLevel.Unknown, realtime["CC34"])

        val forecast = json.decodeFromString<CrowdForecastResponse>(
            """{"value": [{"Date": "2026-10-07T00:00:00+08:00", "Stations": [{"Station": "EW13", "Interval": [
                {"Start": "2026-10-07T08:30:00+08:00", "CrowdLevel": "h"},
                {"Start": "2026-10-07T09:30:00+08:00", "CrowdLevel": "m"},
                {"Start": "2026-10-07T10:00:00+08:00", "CrowdLevel": "l"}]}]}]}""",
        ).value.forecastByStation(ZonedDateTime.parse("2026-10-07T09:45:00+08:00[Asia/Singapore]"))
        assertEquals(
            listOf(Instant.parse("2026-10-07T01:30:00Z") to CrowdLevel.Moderate, Instant.parse("2026-10-07T02:00:00Z") to CrowdLevel.Low),
            forecast["EW13"],
        )
    }

    @Test
    fun parsesLiftMaintenance() {
        val lifts = json.decodeFromString<LiftMaintenanceResponse>(
            """{"value": [{"Line": "NEL", "StationCode": "NE12", "StationName": "Serangoon", "LiftID": "B1L01", "LiftDesc": "Exit B Street level - Concourse"}]}""",
        ).value
        assertEquals("Exit B Street level - Concourse", lifts.single().liftDesc)
    }
}
