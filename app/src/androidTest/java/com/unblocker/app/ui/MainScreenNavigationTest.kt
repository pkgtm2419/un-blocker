package com.unblocker.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.theme.UnblockerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI instrumentation test verifying bottom tab switching via Jetpack Navigation Compose (WP-11).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tabSwitchingPreservesNavigationStateWithoutCrash() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = FilteringPreferences.getInstance(context)
        val qsm = QuickStartManager.getInstance(context)

        composeRule.setContent {
            UnblockerTheme {
                MainScreen(
                    preferences = prefs,
                    quickStartManager = qsm,
                )
            }
        }

        // Bottom pill navigation renders all 4 top-level tabs
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Learn").assertIsDisplayed()
        composeRule.onNodeWithText("Logs").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        // Navigate to Learn tab and back to Home
        composeRule.onNodeWithText("Learn").performClick()
        composeRule.onNodeWithText("Learn an App").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }
}
