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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedStop.value = intent.getStringExtra(EXTRA_OPEN_STOP)
        setContent {
            TsugiTheme {
                TsugiRoot(requestedStop = requestedStop.value, onRequestHandled = { requestedStop.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_STOP)?.let { requestedStop.value = it }
    }

    companion object {
        const val EXTRA_OPEN_STOP = "open_stop"
    }
}
