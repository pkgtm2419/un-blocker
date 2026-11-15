package com.unblocker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.unblocker.app.ui.MainScreen
import com.unblocker.app.ui.adaptive.ProvideAdaptiveWindow
import com.unblocker.app.ui.theme.UnblockerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Android entry activity hosting the adaptive Compose application screen.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Initializes edge-to-edge rendering and installs application Compose content.
     *
     * @param savedInstanceState Previously saved activity state, or null for a new instance.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as UnblockerApplication

        setContent {
            UnblockerTheme {
                ProvideAdaptiveWindow {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(
                            preferences = app.preferences,
                            quickStartManager = app.quickStartManager
                        )
                    }
                }
            }
        }
    }
}
