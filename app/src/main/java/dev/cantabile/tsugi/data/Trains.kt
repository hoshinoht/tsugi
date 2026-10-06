package dev.cantabile.tsugi.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

// ---- TrainServiceAlerts wire format (API User Guide v6.10, §2.11 and Annex C) ----

@Serializable
data class TrainAlertsResponse(@SerialName("value") val value: TrainAlertsDto = TrainAlertsDto())

@Serializable
data class TrainAlertsDto(
    /** 1 = normal or minor delays, 2 = disrupted or major delays. */
    @SerialName("Status") val status: Int = 1,
    @SerialName("AffectedSegments") val segments: List<AffectedSegmentDto> = emptyList(),
    @SerialName("Message") val messages: List<TrainMessageDto> = emptyList(),
)

@Serializable
data class AffectedSegmentDto(
    @SerialName("Line") val line: String = "",
    @SerialName("Direction") val direction: String = "",
    @SerialName("Stations") val stations: String = "",
    @SerialName("FreePublicBus") val freePublicBus: String = "",
    @SerialName("FreeMRTShuttle") val freeMrtShuttle: String = "",
)

@Serializable
data class TrainMessageDto(
    @SerialName("Content") val content: String = "",
    @SerialName("CreatedDate") val createdDate: String = "",
)

// ---- Domain ----

/**
 * Line colours are fixed rather than themed: people recognise lines by colour.
 * Slightly darkened from the network map so white text stays readable (Circle uses dark text).
 */
enum class TrainLine(val code: String, val title: String, val color: Color, val onColor: Color = Color.White, val lrt: Boolean = false) {
    NSL("NSL", "North South Line", Color(0xFFC62B10)),
    EWL("EWL", "East West Line", Color(0xFF00843D)),
    NEL("NEL", "North East Line", Color(0xFF8A1E9C)),
    CCL("CCL", "Circle Line", Color(0xFFF7A11A), Color(0xFF231A00)),
    DTL("DTL", "Downtown Line", Color(0xFF005EC4)),
    TEL("TEL", "Thomson–East Coast Line", Color(0xFF8A5022)),
    BPL("BPL", "Bukit Panjang LRT", Color(0xFF5E6B5F), lrt = true),
    STL("STL", "Sengkang LRT", Color(0xFF5E6B5F), lrt = true),
    PTL("PTL", "Punggol LRT", Color(0xFF5E6B5F), lrt = true);

    companion object {
        /** Also accepts the codes LTA's other feeds use for the branches and LRTs. */
        private val ALIASES = mapOf("CGL" to "EWL", "CEL" to "CCL", "SLRT" to "STL", "PLRT" to "PTL")

        fun of(code: String) = (ALIASES[code] ?: code).let { c -> entries.firstOrNull { it.code == c } }
    }
}

data class AffectedSegment(
    val line: TrainLine?,
    val lineCode: String,
    val direction: String,
    val stations: List<String>,
    val freeBus: Boolean,
    val freeShuttle: Boolean,
)

data class TrainStatus(
    val disrupted: Boolean,
    val segments: List<AffectedSegment>,
    val messages: List<String>,
    val fetchedAt: Instant,
)

fun TrainAlertsDto.toDomain(now: Instant) = TrainStatus(
    disrupted = status == 2 || segments.isNotEmpty(),
    segments = segments.map { s ->
        AffectedSegment(
            line = TrainLine.of(s.line),
            lineCode = s.line,
            direction = s.direction,
            stations = s.stations.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            freeBus = s.freePublicBus.isNotBlank(),
            freeShuttle = s.freeMrtShuttle.isNotBlank(),
        )
    },
    messages = messages.map { it.content.trim() }.filter { it.isNotEmpty() },
    fetchedAt = now,
)
