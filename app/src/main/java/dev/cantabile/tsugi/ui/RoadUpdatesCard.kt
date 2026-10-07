package dev.cantabile.tsugi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.R
import java.time.format.DateTimeFormatter

private val CHANGE_DATE = DateTimeFormatter.ofPattern("d MMM")

/**
 * Road incidents on the way to your saved stops (accidents, breakdowns, diversions…) and route
 * changes to your saved buses. Hidden when there's nothing relevant; collapsed to a summary by default.
 */
@Composable
fun RoadUpdatesCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val updates by vm.roadUpdates.collectAsStateWithLifecycle()
    if (updates.isEmpty) return
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = listOfNotNull(
        updates.incidents.size.takeIf { it > 0 }?.let { "$it road incident${if (it == 1) "" else "s"} on your routes" },
        updates.routeChanges.size.takeIf { it > 0 }?.let { "$it route change${if (it == 1) "" else "s"}" },
    ).joinToString(" · ")

    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
        border = cardBorder(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_chevron_down),
                    contentDescription = if (expanded) "Show less" else "Show details",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            val shown = if (expanded) updates.incidents else updates.incidents.take(1)
            shown.forEach { road ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listOfNotNull(road.incident.time, road.incident.type, "affects ${road.services.joinToString(", ")}").joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        road.incident.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expanded) {
                updates.routeChanges.forEach { change ->
                    Text(
                        "Bus ${change.serviceNo}'s route changed on ${change.effective.format(CHANGE_DATE)}; routes and stops have been updated.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
