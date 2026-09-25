package com.unblocker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.unblocker.app.ui.MainScreen
import com.unblocker.app.ui.adaptive.ProvideAdaptiveWindow
import com.unblocker.app.ui.theme.UnblockerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as UnblockerApplication

        setContent {
            UnblockerTheme {
                ProvideAdaptiveWindow {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(
                            database = app.database,
                            preferences = app.preferences,
                            quickStartManager = app.quickStartManager
                        )
                    }
                }
            }
        }
    }
}
