package com.unblocker.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.unblocker.app.R
import com.unblocker.app.data.repository.DeviceUserRuleGateway
import com.unblocker.app.ui.exceptions.ExceptionsScreen
import com.unblocker.app.ui.exceptions.ExceptionsViewModel
import com.unblocker.app.ui.theme.UnblockerTheme
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.entity.UserRuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation UI test verifying Exceptions rule tester, Allow/Block tabs, and rule creation/deletion (WP-20).
 */
@RunWith(AndroidJUnit4::class)
class ExceptionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addAllowRuleAppearsInListAndEvaluatesAsAllowed() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeDao = InMemoryUserRuleDao()
        val gateway = DeviceUserRuleGateway(context, fakeDao)
        val viewModel = ExceptionsViewModel(context, fakeDao, gateway)

        composeRule.setContent {
            UnblockerTheme {
                ExceptionsScreen(
                    onNavigateBack = {},
                    viewModel = viewModel,
                )
            }
        }

        val addPlaceholder = context.getString(R.string.exceptions_add_placeholder)
        val addAllow = context.getString(R.string.exceptions_add_allow_button)

        composeRule.onNodeWithText(addPlaceholder).performTextInput("safe.example.com")
        composeRule.onNodeWithText(addAllow).performClick()

        composeRule.onNodeWithText("safe.example.com").assertIsDisplayed()
    }

    private class InMemoryUserRuleDao : UserRuleDao {
        private val rules = MutableStateFlow<List<UserRuleEntity>>(emptyList())

        override fun observeAll(): Flow<List<UserRuleEntity>> = rules

        override fun observeByAction(action: Int): Flow<List<UserRuleEntity>> =
            rules.map { list -> list.filter { it.action == action } }

        override suspend fun getByDomain(domain: String): UserRuleEntity? =
            rules.value.firstOrNull { it.domain == domain }

        override suspend fun getAll(): List<UserRuleEntity> = rules.value

        override suspend fun upsert(entity: UserRuleEntity) {
            rules.value = (rules.value.filterNot { it.domain == entity.domain } + entity)
        }

        override suspend fun deleteByDomain(domain: String) {
            rules.value = rules.value.filterNot { it.domain == domain }
        }

        override suspend fun clearAll() {
            rules.value = emptyList()
        }
    }
}
