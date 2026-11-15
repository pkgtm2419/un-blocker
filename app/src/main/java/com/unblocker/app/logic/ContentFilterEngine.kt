package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.data.model.ContentType
import com.unblocker.app.data.model.DetectionMethod
import com.unblocker.app.data.model.FilterResult
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.data.preferences.MyDomainsStore
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.analysis.NeverBlockPolicy
import com.unblocker.app.logic.analysis.PrivateDomainSet
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.rules.CnamePolicy

/**
 * Adapts the on-device decision pipeline to UI and VPN filter results.
 *
 * Learning identifiers use private stores; query logging is managed separately by the VPN/logging path.
 *
 * @param context Application context for assets, preferences and private stores.
 * @param adDetector Static and extension-rule matcher.
 * @param adultContentDetector Adult-domain classifier.
 * @param networkLearner Evidence collector used by the default adaptive engine.
 * @param preferences Current filtering settings.
 * @param allowlistedDomains User allow rules.
 * @param blocklistedDomains User block rules.
 * @property adaptiveEngine Evidence-based decision component exposed to callers.
 */
class ContentFilterEngine(
    private val context: Context,
    private val adDetector: AdDetector =
        AdDetector(
            context = context,
            offHeapRuleEngine = createDefaultOffHeapEngine(context),
        ),
    private val adultContentDetector: AdultContentDetector = AdultContentDetector(context),
    private val networkLearner: LocalNetworkLearner =
        LocalNetworkLearner(
            context = context,
            isKnownTracker = { adDetector.matchRule(it)?.action == com.unblocker.app.logic.rules.RuleAction.BLOCK },
        ),
    private val preferences: FilteringPreferences = FilteringPreferences.getInstance(context),
    private val allowlistedDomains: PrivateDomainSet = DeviceLearning.allowlist(context),
    private val blocklistedDomains: PrivateDomainSet = DeviceLearning.blocklist(context),
    val adaptiveEngine: AdaptiveBlockingEngine = AdaptiveBlockingEngine(preferences, networkLearner),
) {
    init {
        NeverBlockPolicy.load(context)
        com.unblocker.app.data.repository.ExtensionRepository
            .getInstance(context)
            .reloadActiveRules()
    }

    private val myDomainsStore = MyDomainsStore.getInstance(context)
    private val protectedDomains =
        DefaultProtectedDomains(
            builtInSupplier = { NeverBlockPolicy.getProtectedHosts() },
            myDomainsSupplier = { myDomainsStore.domains.value },
            psl = PublicSuffixRules.from(context),
        )

    private val cnamePolicy =
        CnamePolicy(
            { preferences.adBlockingEnabled.value },
            allowlistedDomains::contains,
            adDetector::matchRule,
            protectedDomains,
        )

    /**
     * Checks aliases while honoring original-host exceptions and the ad-filter toggle.
     *
     * @param original Original queried hostname.
     * @param aliases Validated CNAME targets in response order.
     * @return First alias matching a block rule, or null when none may be blocked.
     */
    fun blockedAlias(
        original: String,
        aliases: List<String>,
    ): String? = cnamePolicy.blockedAlias(original, aliases)

    private val decideBlockingUseCase =
        DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultContentDetector,
            adaptiveBlockingEngine = adaptiveEngine,
            preferences = preferences,
            isAllowlisted = allowlistedDomains::contains,
            isBlocklisted = blocklistedDomains::contains,
            protectedDomains = protectedDomains,
            psl = PublicSuffixRules.from(context),
        )

    /**
     * Returns the current filtering result for a domain, allowing blank input.
     *
     * @param rawDomain DNS hostname to trim and evaluate.
     * @return Decision, explanation and detection method for the supplied name.
     */
    fun analyzeAndFilter(rawDomain: String): FilterResult {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) {
            return FilterResult(
                domain = domain,
                contentType = ContentType.NORMAL,
                shouldBlock = false,
                reason = "Empty domain",
                detectionMethod = DetectionMethod.NONE,
                confidence = 1.0f,
            )
        }

        return evaluateDomain(domain)
    }

    private fun evaluateDomain(domain: String): FilterResult {
        val decision = decideBlockingUseCase(domain)

        val contentType =
            when (decision.category) {
                BlockingCategory.AD, BlockingCategory.TRACKER -> ContentType.AD
                BlockingCategory.ADULT_CONTENT -> ContentType.ADULT_CONTENT
                BlockingCategory.CUSTOM -> ContentType.CUSTOM
                BlockingCategory.NORMAL -> ContentType.NORMAL
            }

        val method =
            when (decision.reasonCode) {
                com.unblocker.app.domain.model.DecisionReason.USER_BLOCK -> DetectionMethod.CUSTOM_RULE

                com.unblocker.app.domain.model.DecisionReason.STATIC_RULE,
                com.unblocker.app.domain.model.DecisionReason.ADULT_STATIC,
                -> DetectionMethod.STATIC_LIST

                com.unblocker.app.domain.model.DecisionReason.ADULT_TLD -> DetectionMethod.TLD_RULE

                com.unblocker.app.domain.model.DecisionReason.ADULT_PATTERN -> DetectionMethod.PATTERN_MATCH

                com.unblocker.app.domain.model.DecisionReason.LEARNED_EVIDENCE -> DetectionMethod.HEURISTIC_ANALYSIS

                else -> DetectionMethod.NONE
            }

        return FilterResult(
            domain = domain,
            contentType = contentType,
            shouldBlock = decision.isBlocked,
            reason = decision.reason,
            detectionMethod = method,
            confidence = decision.confidence,
        )
    }

    /**
     * Loads the optional installed off-heap rule file.
     */
    companion object {
        /**
         * Loads rules.bin when present and at least eight bytes long.
         *
         * @param context Context whose private files directory contains the rules.
         * @return Loaded engine, or null when absent, too short or loading fails.
         */
        fun createDefaultOffHeapEngine(context: Context): com.unblocker.data.cache.OffHeapRuleEngine? {
            val file = java.io.File(context.filesDir, "rules.bin")
            if (!file.exists() || file.length() < 8L) return null
            return runCatching {
                com.unblocker.data.cache.OffHeapRuleEngine().apply {
                    reloadFromFile(file)
                }
            }.getOrNull()
        }
    }
}
