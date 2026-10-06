package dev.cantabile.tsugi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.AffectedSegment
import dev.cantabile.tsugi.data.TrainLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TRAIN_REFRESH_MS = 120_000L // alerts are published ad hoc; every 2 min is plenty

@Composable
fun PollTrainStatus(vm: AppViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refreshTrains()
                delay(TRAIN_REFRESH_MS)
            }
        }
    }
}

/** One-line "all clear" when trains are normal; a red card with the details when a line is disrupted. */
@Composable
fun TrainStatusCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val status by vm.trainStatus.collectAsStateWithLifecycle()
    val s = status ?: return
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }

    if (!s.disrupted) {
        Row(
            modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(colors.primary, RoundedCornerShape(4.dp)))
            Text("Trains running normally", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
        return
    }

    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (s.segments.size == 1) "${s.segments[0].line?.title ?: s.segments[0].lineCode} disrupted" else "${s.segments.size} lines disrupted",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    contentDescription = if (expanded) "Show less" else "Show details",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.segments.forEach { SegmentChip(it) }
            }
            if (expanded) {
                s.messages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val freeBus = s.segments.any { it.freeBus }
                val shuttle = s.segments.any { it.freeShuttle }
                if (freeBus || shuttle) {
                    Text(
                        listOfNotNull("Free public buses".takeIf { freeBus }, "free MRT shuttle".takeIf { shuttle })
                            .joinToString(" and ") + " at affected stations.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else if (s.messages.isNotEmpty()) {
                Text(s.messages.last(), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SegmentChip(segment: AffectedSegment) {
    val line = segment.line
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LineBadge(line, segment.lineCode)
        val range = when (segment.stations.size) {
            0 -> ""
            1 -> segment.stations[0]
            else -> "${segment.stations.first()}–${segment.stations.last()}"
        }
        val towards = segment.direction.takeIf { it.isNotBlank() && it != "Both" }?.let { "to $it" } ?: "both ways"
        Text(listOf(range, towards).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LineBadge(line: TrainLine?, fallback: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = line?.color ?: MaterialTheme.colorScheme.outline,
        contentColor = line?.onColor ?: MaterialTheme.colorScheme.surface,
    ) {
        Text(
            line?.code ?: fallback,
            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
