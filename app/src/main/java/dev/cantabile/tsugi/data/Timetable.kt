package dev.cantabile.tsugi.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Which of LTA's three bus timetables applies on a day. */
enum class DayType { Weekday, Saturday, Sunday }

/**
 * Singapore's gazetted public holidays (MOM), including the Monday off when one falls on a Sunday.
 * Buses run the Sunday timetable on these days. Add the next year's list when MOM publishes it
 * (usually in June); unlisted years fall back to the day of the week.
 */
val PUBLIC_HOLIDAYS: Set<LocalDate> = listOf(
    // 2026
    "2026-01-01", "2026-02-17", "2026-02-18", "2026-03-21", "2026-04-03", "2026-05-01",
    "2026-05-27", "2026-05-31", "2026-06-01", "2026-08-09", "2026-08-10", "2026-11-08",
    "2026-11-09", "2026-12-25",
    // 2027
    "2027-01-01", "2027-02-06", "2027-02-07", "2027-02-08", "2027-03-10", "2027-03-26",
    "2027-05-01", "2027-05-17", "2027-05-20", "2027-08-09", "2027-10-28", "2027-12-25",
).map(LocalDate::parse).toSet()

fun dayType(date: LocalDate): DayType = when {
    date in PUBLIC_HOLIDAYS -> DayType.Sunday
    date.dayOfWeek == DayOfWeek.SATURDAY -> DayType.Saturday
    date.dayOfWeek == DayOfWeek.SUNDAY -> DayType.Sunday
    else -> DayType.Weekday
}

/**
 * A bus day runs from 4 am to 4 am: a last bus at 12:30 am belongs to the day before, and LTA
 * writes it as "0030" in that day's timetable.
 */
/** Night services' last buses run until about 4:30 am, so the service day turns over at 5. */
const val SERVICE_DAY_START_HOUR = 5

/** "0653" → 06:53; "2400" → 00:00; anything else ("-", blank) → null. */
fun parseHhmm(hhmm: String): LocalTime? {
    if (hhmm.length != 4 || !hhmm.all(Char::isDigit)) return null
    val minute = hhmm.takeLast(2).toInt()
    if (minute > 59) return null
    return LocalTime.of(hhmm.take(2).toInt() % 24, minute)
}

/** [first, last] as "HHMM" for a route stop on [type]'s timetable. */
fun RouteStop.times(type: DayType): List<String> = when (type) {
    DayType.Weekday -> wd
    DayType.Saturday -> sat
    DayType.Sunday -> sun
}

/**
 * When the last bus of the current bus day leaves this stop, or null if the timetable has none.
 * May be in the past: the last bus has gone and the next one is tomorrow's first.
 */
fun RouteStop.lastBusAt(now: ZonedDateTime): ZonedDateTime? {
    // Night services (first bus after midnight) are listed under the calendar day they run on.
    val today = now.toLocalDate()
    val tonight = times(dayType(today))
    val nightFirst = tonight.getOrNull(0)?.let(::parseHhmm)
    if (nightFirst != null && nightFirst.hour < SERVICE_DAY_START_HOUR) {
        return tonight.getOrNull(1)?.let(::parseHhmm)?.let { today.atTime(it).atZone(now.zone) }
    }
    val day = if (now.hour < SERVICE_DAY_START_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
    val time = times(dayType(day)).getOrNull(1)?.let(::parseHhmm) ?: return null
    val date = if (time.hour < SERVICE_DAY_START_HOUR) day.plusDays(1) else day
    return date.atTime(time).atZone(now.zone)
}

/**
 * Completes a stop's live services from the timetable: adds services that serve the stop but aren't
 * in LTA's live feed (not running now) with today's first-bus time, gives every service its last
 * bus and current frequency, and sorts everything by service number.
 */
fun withTimetable(
    live: List<ServiceArrivals>,
    routesAtStop: List<RouteStop>,
    info: ServiceInfoIndex?,
    now: ZonedDateTime,
): List<ServiceArrivals> {
    val routeOf = routesAtStop.groupBy { it.service }
    fun complete(s: ServiceArrivals): ServiceArrivals {
        val route = routeOf[s.serviceNo]?.firstOrNull() ?: return s
        return s.copy(
            firstBus = s.firstBus ?: route.firstBusLabel(now).takeIf { s.buses.isEmpty() },
            lastBus = route.lastBusAt(now)?.toInstant(),
            frequency = info?.get(s.serviceNo, route.direction)?.frequencyAt(now.toLocalTime(), dayType(now.toLocalDate())),
        )
    }
    val present = live.map { it.serviceNo }.toSet()
    val missing = routeOf.filterKeys { it !in present }.map { (service, routes) ->
        ServiceArrivals(service, operatorName(routes.first().operator), emptyList())
    }
    return (live + missing).map(::complete).sortedWith(compareBy(serviceOrder) { it.serviceNo })
}
