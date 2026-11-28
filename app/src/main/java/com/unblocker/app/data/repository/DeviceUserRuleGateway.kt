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
import com.unblocker.data.db.dao.UserRuleDao
import com.unblocker.data.db.entity.UserRuleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists HMAC-backed user overrides, readable `user_rule` Room entries (WP-20 / D2), and
 * My domains protection entries on the IO dispatcher.
 *
 * @param context Application context used to access rule storage, preferences, and public-suffix assets.
 * @param userRuleDao Readable no-backup Room table DAO mirroring new/updated user exceptions.
 */
@Singleton
class DeviceUserRuleGateway internal constructor(
    private val context: Context,
    private val userRuleDao: UserRuleDao,
    private val publicSuffixRules: PublicSuffixRules,
) : UserRuleGateway {
    /**
     * Hilt entrypoint backed by packaged [PublicSuffixRules].
     */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        userRuleDao: UserRuleDao,
    ) : this(
        context = context,
        userRuleDao = userRuleDao,
        publicSuffixRules = PublicSuffixRules.from(context),
    )

    private val admission = DefaultRuleAdmission()
    private val myDomainsStore = MyDomainsStore.getInstance(context)

    /**
     * Normalizes and saves an exact-domain override, applying admission checks to block requests.
     *
     * @param domain User-entered host to normalize.
     * @param feedback Requested allow, block, or removal feedback.
     * @return Applied when the operation succeeds, otherwise a failure explanation.
     */
    override suspend fun set(
        domain: String,
        feedback: UserFeedback,
    ): RuleUpdateResult =
        withContext(Dispatchers.IO) {
            val normalized =
                DomainName.normalizeUserEntry(domain)
                    ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")

            if (feedback == UserFeedback.BLOCK) {
                val skip = admission.check(RuleAction.BLOCK, RuleKind.EXACT, normalized)
                if (skip != null) {
                    return@withContext RuleUpdateResult.Failed("Domain rejected by admission guard: $skip")
                }
            }

            try {
                DeviceLearning.setUserRule(context, normalized, feedback)
                syncUserRule(normalized = normalized, isWholeSite = false, feedback = feedback)
                RuleUpdateResult.Applied
            } catch (e: Exception) {
                RuleUpdateResult.Failed(e.message ?: "Failed to save rule")
            }
        }

    /**
     * Normalizes the host to its registrable site and saves a subtree override.
     *
     * @param domain User-entered host to normalize.
     * @param feedback Requested allow, block, or removal feedback.
     * @return Applied when the operation succeeds, otherwise a failure explanation.
     */
    override suspend fun setWholeSite(
        domain: String,
        feedback: UserFeedback,
    ): RuleUpdateResult =
        withContext(Dispatchers.IO) {
            val normalized =
                DomainName.normalizeUserEntry(domain)
                    ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
            val registrable = publicSuffixRules.registrableDomain(normalized) ?: normalized

            if (feedback == UserFeedback.BLOCK) {
                val skip = admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, registrable)
                if (skip != null) {
                    return@withContext RuleUpdateResult.Failed("Domain rejected by admission guard: $skip")
                }
            }

            try {
                DeviceLearning.setUserRuleSubtree(context, registrable, feedback)
                syncUserRule(normalized = registrable, isWholeSite = true, feedback = feedback)
                RuleUpdateResult.Applied
            } catch (e: Exception) {
                RuleUpdateResult.Failed(e.message ?: "Failed to save whole site rule")
            }
        }

    /**
     * Adds the registrable site to the readable My domains preferences list.
     *
     * @param domain User-entered host to normalize.
     * @return Applied when the operation succeeds, otherwise a failure explanation.
     */
    override suspend fun protectDomain(domain: String): RuleUpdateResult =
        withContext(Dispatchers.IO) {
            val normalized =
                DomainName.normalizeUserEntry(domain)
                    ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
            val registrable = publicSuffixRules.registrableDomain(normalized) ?: normalized
            val added = myDomainsStore.addDomain(registrable)
            if (added) {
                RuleUpdateResult.Applied
            } else {
                RuleUpdateResult.Failed("Failed to protect domain (limit reached or already protected)")
            }
        }

    /**
     * Removes the host override, registrable-site override, readable `user_rule` rows, and My domains entries.
     *
     * @param domain User-entered host to normalize.
     * @return Applied when the operation succeeds, otherwise a failure explanation.
     */
    override suspend fun reset(domain: String): RuleUpdateResult =
        withContext(Dispatchers.IO) {
            val normalized =
                DomainName.normalizeUserEntry(domain)
                    ?: return@withContext RuleUpdateResult.Failed("Invalid domain name: $domain")
            val registrable = publicSuffixRules.registrableDomain(normalized) ?: normalized
            try {
                DeviceLearning.setUserRule(context, normalized, UserFeedback.NONE)
                DeviceLearning.setUserRuleSubtree(context, registrable, UserFeedback.NONE)
                userRuleDao.deleteByDomain(normalized)
                if (registrable != normalized) {
                    userRuleDao.deleteByDomain(registrable)
                }
                myDomainsStore.removeDomain(registrable)
                myDomainsStore.removeDomain(normalized)
                RuleUpdateResult.Applied
            } catch (e: Exception) {
                RuleUpdateResult.Failed(e.message ?: "Failed to reset rule")
            }
        }

    private suspend fun syncUserRule(
        normalized: String,
        isWholeSite: Boolean,
        feedback: UserFeedback,
        source: String = "manual",
    ) {
        if (feedback == UserFeedback.NONE) {
            userRuleDao.deleteByDomain(normalized)
            return
        }
        val now = System.currentTimeMillis()
        val existing = userRuleDao.getByDomain(normalized)
        val action =
            when (feedback) {
                UserFeedback.ALLOW -> UserRuleEntity.ACTION_ALLOW
                UserFeedback.BLOCK -> UserRuleEntity.ACTION_BLOCK
                UserFeedback.NONE -> return
            }
        userRuleDao.upsert(
            UserRuleEntity(
                domain = normalized,
                action = action,
                isWholeSite = isWholeSite,
                source = source,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}
