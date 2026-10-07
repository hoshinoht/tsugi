package dev.cantabile.tsugi.data

/** Saved stops this close to a station make its lines "yours" for disruption alerts. */
const val MY_STATION_M = 400

/** The lines through stations near any of [stopCodes]: the lines you're likely to use. */
fun linesNear(stopCodes: Collection<String>, stopLatLng: (String) -> Pair<Double, Double>?, stations: StationIndex): Set<TrainLine> =
    stopCodes.mapNotNull(stopLatLng)
        .flatMap { (lat, lng) -> stations.nearby(lat, lng, MY_STATION_M) }
        .flatMap { it.station.lines }
        .toSet()

/** What a disruption notification says, and a key that changes only when the disruption does. */
data class DisruptionNotice(val key: String, val title: String, val text: String)

/**
 * The notice for disruptions on [myLines], or null when none of them is disrupted. Station codes
 * are named through [stationName].
 */
fun disruptionNotice(status: TrainStatus, myLines: Set<TrainLine>, stationName: (String) -> String?): DisruptionNotice? {
    val mine = status.segments.filter { it.line in myLines }
    if (mine.isEmpty()) return null
    val key = mine.map { "${it.lineCode}:${it.direction}:${it.stations.joinToString(",")}" }.sorted().joinToString("|")
    val lines = mine.mapNotNull { it.line }.distinct()
    val title = if (lines.size == 1) "${lines.single().title} disrupted" else "${lines.size} of your lines disrupted"
    fun name(code: String) = stationName(code) ?: code
    val where = mine.joinToString("; ") { seg ->
        val range = when (seg.stations.size) {
            0 -> seg.lineCode
            1 -> name(seg.stations.single())
            else -> "${name(seg.stations.first())}–${name(seg.stations.last())}"
        }
        if (lines.size > 1) "${seg.lineCode} $range" else range
    }
    val help = listOfNotNull(
        "free buses".takeIf { mine.any { it.freeBus } },
        "free shuttle".takeIf { mine.any { it.freeShuttle } },
    ).joinToString(" and ")
    return DisruptionNotice(key, title, where + if (help.isNotEmpty()) " · $help" else "")
}
