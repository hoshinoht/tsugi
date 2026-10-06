package dev.cantabile.tsugi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.cantabile.tsugi.ui.TsugiRoot
import dev.cantabile.tsugi.ui.theme.TsugiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TsugiTheme {
                TsugiRoot()
            }
        }
    }
}
