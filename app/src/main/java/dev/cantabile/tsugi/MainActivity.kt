package dev.cantabile.tsugi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.cantabile.tsugi.ui.TsugiRoot
import dev.cantabile.tsugi.ui.theme.TsugiTheme

class MainActivity : ComponentActivity() {
    /** A stop to open, e.g. from tapping a tracking notification. */
    private val requestedStop = mutableStateOf<String?>(null)

    /** A tab to show, e.g. from a launcher shortcut. */
    private val requestedTab = mutableStateOf<String?>(null)

    /** A saved place to open, from its launcher shortcut. */
    private val requestedPlace = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            requestedStop.value = intent.getStringExtra(EXTRA_OPEN_STOP)
            requestedTab.value = intent.getStringExtra(EXTRA_OPEN_TAB)
            requestedPlace.value = intent.getStringExtra(EXTRA_OPEN_PLACE)
        }
        setContent {
            TsugiTheme {
                TsugiRoot(
                    requestedStop = requestedStop.value,
                    requestedTab = requestedTab.value,
                    requestedPlace = requestedPlace.value,
                    onRequestHandled = {
                        requestedStop.value = null
                        requestedTab.value = null
                        requestedPlace.value = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_STOP)?.let { requestedStop.value = it }
        intent.getStringExtra(EXTRA_OPEN_TAB)?.let { requestedTab.value = it }
        intent.getStringExtra(EXTRA_OPEN_PLACE)?.let { requestedPlace.value = it }
    }

    companion object {
        const val EXTRA_OPEN_STOP = "open_stop"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_PLACE = "open_place"
    }
}
