package dev.cantabile.tsugi.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cantabile.tsugi.BuildConfig
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.ALERT_MINUTES
import dev.cantabile.tsugi.data.NEARBY_RADII
import dev.cantabile.tsugi.data.StopSort
import dev.cantabile.tsugi.data.ThemeMode

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val theme by vm.theme.collectAsStateWithLifecycle()
    val radius by vm.radiusM.collectAsStateWithLifecycle()
    val sort by vm.stopSort.collectAsStateWithLifecycle()
    val alert by vm.alertMinutes.collectAsStateWithLifecycle()
    val disruptions by vm.disruptionAlerts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setDisruptionAlerts(true)
        else Toast.makeText(context, "Allow notifications to get disruption alerts", Toast.LENGTH_LONG).show()
    }
    val setDisruptions: (Boolean) -> Unit = { on ->
        val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setDisruptionAlerts(on)
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), "Back") } },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp, bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Setting("Theme", "Follows your wallpaper colours in all three modes") {
                    Choices(ThemeMode.entries, theme, { it.label }, vm::setTheme)
                }
            }
            item {
                Setting("Nearby radius", "How far Nearby looks for stops") {
                    Choices(NEARBY_RADII, radius, { "$it m" }, vm::setRadius)
                }
            }
            item {
                Setting("Stop order", "How buses are ordered on a stop's screen") {
                    Choices(StopSort.entries, sort, { it.label }, vm::setStopSort)
                }
            }
            item {
                Setting("Bus alerts", "Heads-up this many minutes before a tracked bus arrives") {
                    Choices(ALERT_MINUTES, alert, { "$it min" }, vm::setAlertMinutes)
                }
            }
            item {
                Setting("Train disruptions", "Notify me when an MRT or LRT line near my saved stops is disrupted. Checked every 15 minutes.") {
                    Choices(listOf(false, true), disruptions, { if (it) "On" else "Off" }, setDisruptions)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tsugi ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Bus and train data from LTA DataMall. Address search and maps by OneMap. Station locations from SG Rail Data. MIT licensed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Setting(title: String, description: String, control: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            control()
        }
    }
}

/** An M3E connected button group acting as a single-choice selector. */
@Composable
fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label(option), maxLines = 1) }
        }
    }
}
