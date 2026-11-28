package com.unblocker.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.settings.SettingsScreen
import com.unblocker.app.ui.theme.UnblockerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI instrumentation test verifying searchable Settings overview, section drilldown, and initialSection routing (WP-13).
 */
@RunWith(AndroidJUnit4::class)
class SettingsIaNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFiltersGroupsAndOpensSectionDetail() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = FilteringPreferences.getInstance(context)
        val qsm = QuickStartManager.getInstance(context)

        composeRule.setContent {
            UnblockerTheme {
                SettingsScreen(
                    preferences = prefs,
                    quickStartManager = qsm,
                    onNavigateToExtensions = {},
                )
            }
        }

        val searchHint = context.getString(R.string.settings_search_placeholder)
        val privacyTitle = context.getString(R.string.settings_section_privacy_title)
        val pauseLogging = context.getString(R.string.settings_pause_logging_title)
        val backDesc = context.getString(R.string.action_back)

        composeRule.onNodeWithText(searchHint).performTextInput("privacy")
        composeRule.onNodeWithText(privacyTitle).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(pauseLogging).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(backDesc).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
    }

    @Test
    fun initialSectionDeepLinkOpensSectionDirectly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = FilteringPreferences.getInstance(context)
        val qsm = QuickStartManager.getInstance(context)

        composeRule.setContent {
            UnblockerTheme {
                SettingsScreen(
                    preferences = prefs,
                    quickStartManager = qsm,
                    initialSection = "automation",
                    onNavigateToExtensions = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.settings_auto_boot_title)).assertIsDisplayed()
    }
}
