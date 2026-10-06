package dev.cantabile.tsugi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DisruptionAlertsTest {
    private val stations = StationIndex(
        listOf(
            Station("Jurong East", listOf("NS1", "EW24"), 1.3333, 103.7422),
            Station("Bukit Batok", listOf("NS2"), 1.3490, 103.7496),
            Station("Bishan", listOf("NS17", "CC15"), 1.3510, 103.8485),
        ),
    )

    private fun segment(line: TrainLine, vararg codes: String, freeBus: Boolean = false) =
        AffectedSegment(line, line.code, "Jurong East", codes.toList(), freeBus, false)

    private fun status(vararg segments: AffectedSegment) = TrainStatus(segments.isNotEmpty(), segments.toList(), emptyList(), Instant.EPOCH)

    @Test
    fun linesNearSavedStops() {
        val stops = mapOf("A" to (1.3334 to 103.7423), "B" to (1.2000 to 103.6000))
        assertEquals(setOf(TrainLine.NSL, TrainLine.EWL), linesNear(listOf("A", "B"), stops::get, stations))
    }

    @Test
    fun onlyYourLinesAndNamedStations() {
        val s = status(segment(TrainLine.NSL, "NS1", "NS2", freeBus = true), segment(TrainLine.CCL, "CC15"))
        val notice = disruptionNotice(s, setOf(TrainLine.NSL)) { stations[it]?.name }!!
        assertEquals("North South Line disrupted", notice.title)
        assertEquals("Jurong East–Bukit Batok · free buses", notice.text)
        assertNull(disruptionNotice(s, setOf(TrainLine.DTL)) { null })
    }

    @Test
    fun keyChangesWithTheDisruption() {
        val a = disruptionNotice(status(segment(TrainLine.NSL, "NS1", "NS2")), setOf(TrainLine.NSL)) { null }!!
        val b = disruptionNotice(status(segment(TrainLine.NSL, "NS1", "NS2", "NS3")), setOf(TrainLine.NSL)) { null }!!
        assertNotEquals(a.key, b.key)
        assertEquals(a.key, disruptionNotice(status(segment(TrainLine.NSL, "NS1", "NS2")), setOf(TrainLine.NSL)) { null }!!.key)
    }
}
