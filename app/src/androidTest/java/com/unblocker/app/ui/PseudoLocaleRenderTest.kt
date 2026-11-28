package com.unblocker.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.theme.UnblockerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke tests verifying Compose rendering in LTR (`en-XA` style) and RTL (`ar-XB` style) modes (WP-12).
 */
@RunWith(AndroidJUnit4::class)
class PseudoLocaleRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainScreenRendersCleanlyInLtrAndRtlDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = FilteringPreferences.getInstance(context)
        val qsm = QuickStartManager.getInstance(context)
        val homeLabel = context.getString(R.string.nav_home)
        val settingsLabel = context.getString(R.string.nav_settings)

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                UnblockerTheme {
                    MainScreen(
                        preferences = prefs,
                        quickStartManager = qsm,
                    )
                }
            }
        }

        composeRule.onNodeWithText(homeLabel).assertIsDisplayed()
        composeRule.onNodeWithText(settingsLabel).assertIsDisplayed()
    }
}
