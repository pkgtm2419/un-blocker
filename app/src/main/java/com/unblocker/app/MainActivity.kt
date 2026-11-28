package com.unblocker.app

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.unblocker.app.ui.MainScreen
import com.unblocker.app.ui.adaptive.ProvideAdaptiveWindow
import com.unblocker.app.ui.theme.UnblockerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Android entry activity hosting the adaptive Compose application screen and deep-link intents.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    /**
     * Initializes edge-to-edge rendering, captures any incoming `unblocker://` deep link,
     * and installs application Compose content.
     *
     * @param savedInstanceState Previously saved activity state, or null for a new instance.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureDeepLink(intent)

        val app = application as UnblockerApplication

        setContent {
            val deepLinkUri by pendingDeepLink.collectAsState()
            val hideFromRecents by app.preferences.hideFromRecents.collectAsState()
            LaunchedEffect(hideFromRecents) {
                applyHideFromRecents(hideFromRecents)
            }
            UnblockerTheme {
                ProvideAdaptiveWindow {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainScreen(
                            preferences = app.preferences,
                            quickStartManager = app.quickStartManager,
                            pendingDeepLinkUri = deepLinkUri,
                            onConsumeDeepLink = { pendingDeepLink.value = null },
                        )
                    }
                }
            }
        }
    }

    /**
     * Captures internal deep-link intents delivered to an existing singleTop activity instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        val app = application as UnblockerApplication
        applyHideFromRecents(app.preferences.hideFromRecents.value)
    }

    private fun applyHideFromRecents(excluded: Boolean) {
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        runCatching {
            am.appTasks.forEach { task -> task.setExcludeFromRecents(excluded) }
        }
    }

    private fun captureDeepLink(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_VIEW) {
            val data = incoming.dataString
            if (!data.isNullOrBlank()) {
                pendingDeepLink.value = data
            }
        }
    }
}
