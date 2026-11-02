package com.unblocker.app.data.repository

import android.content.Context
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.UserFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceUserRuleGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : UserRuleGateway {
    override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult = withContext(Dispatchers.IO) {
        val normalized = DomainName.normalizeUserEntry(domain)
            ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")

        try {
            DeviceLearning.setUserRule(context, normalized, feedback)
            RuleUpdateResult.Applied
        } catch (e: Exception) {
            RuleUpdateResult.Failed(e.message ?: "Failed to save rule")
        }
    }
}
