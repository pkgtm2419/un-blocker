package com.unblocker.app.data.repository

import android.content.Context
import com.unblocker.app.data.preferences.MyDomainsStore
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.logic.rules.DefaultRuleAdmission
import com.unblocker.app.logic.rules.RuleAction
import com.unblocker.app.logic.rules.RuleKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceUserRuleGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : UserRuleGateway {
    private val admission = DefaultRuleAdmission()
    private val myDomainsStore = MyDomainsStore.getInstance(context)
    private val psl = PublicSuffixRules.from(context)

    override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult = withContext(Dispatchers.IO) {
        val normalized = DomainName.normalizeUserEntry(domain)
            ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")

        if (feedback == UserFeedback.BLOCK) {
            val skip = admission.check(RuleAction.BLOCK, RuleKind.EXACT, normalized)
            if (skip != null) {
                return@withContext RuleUpdateResult.Failed("Domain rejected by admission guard: $skip")
            }
        }

        try {
            DeviceLearning.setUserRule(context, normalized, feedback)
            RuleUpdateResult.Applied
        } catch (e: Exception) {
            RuleUpdateResult.Failed(e.message ?: "Failed to save rule")
        }
    }

    override suspend fun setWholeSite(domain: String, feedback: UserFeedback): RuleUpdateResult = withContext(Dispatchers.IO) {
        val normalized = DomainName.normalizeUserEntry(domain)
            ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
        val registrable = psl.registrableDomain(normalized) ?: normalized

        if (feedback == UserFeedback.BLOCK) {
            val skip = admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, registrable)
            if (skip != null) {
                return@withContext RuleUpdateResult.Failed("Domain rejected by admission guard: $skip")
            }
        }

        try {
            DeviceLearning.setUserRuleSubtree(context, registrable, feedback)
            RuleUpdateResult.Applied
        } catch (e: Exception) {
            RuleUpdateResult.Failed(e.message ?: "Failed to save whole site rule")
        }
    }

    override suspend fun protectDomain(domain: String): RuleUpdateResult = withContext(Dispatchers.IO) {
        val normalized = DomainName.normalizeUserEntry(domain)
            ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
        val registrable = psl.registrableDomain(normalized) ?: normalized
        val added = myDomainsStore.addDomain(registrable)
        if (added) {
            RuleUpdateResult.Applied
        } else {
            RuleUpdateResult.Failed("Failed to protect domain (limit reached or already protected)")
        }
    }

    override suspend fun reset(domain: String): RuleUpdateResult = withContext(Dispatchers.IO) {
        val normalized = DomainName.normalizeUserEntry(domain)
            ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
        val registrable = psl.registrableDomain(normalized) ?: normalized
        try {
            DeviceLearning.setUserRule(context, normalized, UserFeedback.NONE)
            DeviceLearning.setUserRuleSubtree(context, registrable, UserFeedback.NONE)
            myDomainsStore.removeDomain(registrable)
            myDomainsStore.removeDomain(normalized)
            RuleUpdateResult.Applied
        } catch (e: Exception) {
            RuleUpdateResult.Failed(e.message ?: "Failed to reset rule")
        }
    }
}
