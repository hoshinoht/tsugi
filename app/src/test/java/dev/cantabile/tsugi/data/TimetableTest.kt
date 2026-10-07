package dev.cantabile.tsugi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class TimetableTest {
    private fun at(dateTime: String) = ZonedDateTime.parse("${dateTime}+08:00[Asia/Singapore]")
    private val day = RouteStop(
        service = "10", operator = "SBST", direction = 1, seq = 3, stop = "A",
        wd = listOf("0530", "2342"), sat = listOf("0530", "0015"), sun = listOf("0600", "2330"),
    )
    private val night = RouteStop(
        service = "12e", operator = "SBST", direction = 1, seq = 5, stop = "A",
        wd = listOf("1719", "2306"), sat = listOf("-", "-"), sun = listOf("0005", "0121"),
    )

    @Test
    fun publicHolidaysUseTheSundayTimetable() {
        assertEquals(DayType.Sunday, dayType(LocalDate.parse("2026-12-25"))) // Friday, Christmas
        assertEquals(DayType.Sunday, dayType(LocalDate.parse("2026-08-10"))) // Monday in lieu of National Day
        assertEquals(DayType.Weekday, dayType(LocalDate.parse("2026-10-07")))
        assertEquals(DayType.Saturday, dayType(LocalDate.parse("2026-10-10")))
        assertEquals("6:00 am", day.firstBusLabel(at("2026-12-25T03:00:00")))
    }

    @Test
    fun lastBus_isTonightsOnWeekdays() {
        assertEquals(at("2026-10-07T23:42:00"), day.lastBusAt(at("2026-10-07T22:00:00")))
    }

    @Test
    fun lastBus_afterMidnightBelongsToThePreviousDay() {
        // Saturday's last bus is at 12:15 am on Sunday; at 12:05 am it's still Saturday's bus day.
        assertEquals(at("2026-10-11T00:15:00"), day.lastBusAt(at("2026-10-10T21:00:00")))
        assertEquals(at("2026-10-11T00:15:00"), day.lastBusAt(at("2026-10-11T00:05:00")))
    }

    @Test
    fun lastBus_nightServicesUseTheCalendarDay() {
        assertEquals(at("2026-10-11T01:21:00"), night.lastBusAt(at("2026-10-11T00:30:00")))
        assertNull(night.lastBusAt(at("2026-10-10T12:00:00")))
    }

    @Test
    fun parseHhmm_rejectsJunk() {
        assertEquals(LocalTime.of(0, 0), parseHhmm("2400"))
        assertNull(parseHhmm("-"))
        assertNull(parseHhmm("1275"))
    }

    @Test
    fun frequency_labelsAndBands() {
        assertEquals("every 5–8 min", frequencyLabel("5-08"))
        assertEquals("every 10 min", frequencyLabel("10"))
        assertEquals("every 10 min", frequencyLabel("10-10"))
        assertNull(frequencyLabel("-"))
        assertNull(frequencyLabel("00-00"))
        assertEquals(0, frequencyBand(LocalTime.of(7, 0)))
        assertEquals(1, frequencyBand(LocalTime.of(12, 0)))
        assertEquals(2, frequencyBand(LocalTime.of(18, 0)))
        assertEquals(3, frequencyBand(LocalTime.of(22, 0)))
        assertEquals(3, frequencyBand(LocalTime.of(1, 0)))
    }

    @Test
    fun withTimetable_addsMissingServicesAndDetails() {
        val info = ServiceInfoIndex(listOf(ServiceInfo("10", 1, "TRUNK", "", listOf("3-5", "8-12", "4-6", "10-15"))))
        val now = at("2026-10-07T12:00:00")
        val live = listOf(ServiceArrivals("10", "SBS Transit", listOf(Bus(now.toInstant().plusSeconds(120), true, Load.Seats, BusType.Single, true, "X"))))
        val result = withTimetable(live, listOf(day, night), info, now)
        assertEquals(listOf("10", "12e"), result.map { it.serviceNo })
        assertEquals("every 8–12 min", result[0].frequency)
        assertEquals(Instant.parse("2026-10-07T15:42:00Z"), result[0].lastBus)
        assertNull(result[0].firstBus)
        assertEquals("5:19 pm", result[1].firstBus)
        assertEquals("SBS Transit", result[1].operator)
    }

    @Test
    fun categoryLabel_isSentenceCase() {
        assertEquals("Trunk", categoryLabel("TRUNK"))
        assertNull(categoryLabel(" "))
    }
}

class NextFirstBusTest {
    // A peak-only express: 8:42 am on weekdays, no weekend service.
    private val express = RouteStop("14e", "SBST", 1, 5, "09023", listOf("0842", "0915"), listOf("-", "-"), listOf("-", "-"))
    private fun at(s: String) = java.time.ZonedDateTime.parse("${s}+08:00[Asia/Singapore]")

    @Test
    fun todaysFirstBusWhileItIsStillToCome() {
        assertEquals("8:42 am", express.nextFirstBusLabel(at("2026-10-07T07:30:00"))) // Wednesday
    }

    @Test
    fun tomorrowsOnceTodaysHasGone() {
        assertEquals("tomorrow 8:42 am", express.nextFirstBusLabel(at("2026-10-07T08:50:00")))
        assertEquals("Tmr 8:42", compactFirstBus("tomorrow 8:42 am"))
        // Friday evening: no Saturday service, so nothing to show.
        org.junit.Assert.assertNull(express.nextFirstBusLabel(at("2026-10-09T09:00:00")))
    }

    @Test
    fun tomorrowSortsAfterToday() {
        assertEquals(24 * 60 + 8 * 60 + 42, labelMinutes("tomorrow 8:42 am"))
    }
}
