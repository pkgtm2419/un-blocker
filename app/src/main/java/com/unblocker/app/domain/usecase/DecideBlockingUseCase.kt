package com.unblocker.app.domain.usecase

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.NeverBlockPolicy
import com.unblocker.app.logic.analysis.ProtectedDomains
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.logic.rules.RuleAction

/**
 * Clean Architecture Use Case for evaluating network domains and deciding blocking actions.
 * Orchestrates static lists, independent local evidence, and optional adult-content filters.
 */
class DecideBlockingUseCase(
    private val adDetector: AdDetector,
    private val adultContentDetector: AdultContentDetector,
    private val adaptiveBlockingEngine: AdaptiveBlockingEngine,
    private val isAllowlisted: (String) -> Boolean = { false },
    private val isBlocklisted: (String) -> Boolean = { false },
    private val isAdBlockingEnabled: () -> Boolean = { true },
    private val isAdultBlockingEnabled: () -> Boolean = { true },
    private val isPaused: () -> Boolean = { false },
    private val blockingMode: () -> BlockingMode = { BlockingMode.SMART },
    private val filteringTier: () -> com.unblocker.app.data.preferences.FilteringTier = { com.unblocker.app.data.preferences.FilteringTier.OPTIMAL },
    private val protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT,
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback
) {
    constructor(
        adDetector: AdDetector,
        adultContentDetector: AdultContentDetector,
        adaptiveBlockingEngine: AdaptiveBlockingEngine,
        preferences: FilteringPreferences,
        isAllowlisted: (String) -> Boolean = { false },
        isBlocklisted: (String) -> Boolean = { false },
        protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT,
        psl: PublicSuffixRules = PublicSuffixRules.fallback
    ) : this(
        adDetector = adDetector,
        adultContentDetector = adultContentDetector,
        adaptiveBlockingEngine = adaptiveBlockingEngine,
        isAllowlisted = isAllowlisted,
        isBlocklisted = isBlocklisted,
        isAdBlockingEnabled = { preferences.adBlockingEnabled.value },
        isAdultBlockingEnabled = { preferences.adultBlockingEnabled.value },
        isPaused = { preferences.isPaused() },
        blockingMode = { preferences.blockingMode.value },
        filteringTier = { preferences.filteringTier.value },
        protectedDomains = protectedDomains,
        psl = psl
    )

    operator fun invoke(rawDomain: String): BlockingDecision {
        return try {
            evaluate(rawDomain)
        } catch (e: Exception) {
            // Section 2.2: Fail open on any error
            BlockingDecision.allow("Engine error (fail open)", 1.0f).copy(
                reasonCode = DecisionReason.ENGINE_ERROR
            )
        }
    }

    private fun evaluate(rawDomain: String): BlockingDecision {
        // Step 1: normalize(host) -> invalid => ALLOW(INVALID)
        val domain = DomainName.normalize(rawDomain)
            ?: return BlockingDecision.allow("Invalid domain query", 1.0f).copy(reasonCode = DecisionReason.INVALID)

        // Step 2: paused (notification "Pause 5 min") -> ALLOW(PAUSED)
        if (isPaused()) {
            return BlockingDecision.allow("Protection paused", 1.0f).copy(reasonCode = DecisionReason.PAUSED)
        }

        // Step 3: user ALLOW (exact host OR ancestor entry marked "whole site") -> ALLOW(USER_ALLOW)
        if (isAllowlisted(domain)) {
            adaptiveBlockingEngine.networkLearner.syncFeedback(domain, UserFeedback.ALLOW)
            return BlockingDecision.allow("Local exception", 1.0f).copy(reasonCode = DecisionReason.USER_ALLOW)
        }

        // Step 4: user BLOCK (exact host OR ancestor entry; per-app scope if set) -> BLOCK(USER_BLOCK)
        if (isBlocklisted(domain)) {
            adaptiveBlockingEngine.networkLearner.syncFeedback(domain, UserFeedback.BLOCK)
            return BlockingDecision.block("Local block rule", 1.0f, BlockingCategory.CUSTOM).copy(
                reasonCode = DecisionReason.USER_BLOCK
            )
        }
        adaptiveBlockingEngine.networkLearner.syncFeedback(domain, UserFeedback.NONE)

        // Step 5: mode == APPROVED_ONLY -> ALLOW(NO_MATCH) [record suggestion if matched]
        val currentMode = blockingMode()
        if (currentMode == BlockingMode.APPROVED_ONLY) {
            return BlockingDecision.allow("Approved-only mode: benign traffic", 1.0f).copy(
                reasonCode = DecisionReason.NO_MATCH
            )
        }

        // Step 6: protected (built-in exact | user "My domains" subtree) -> ALLOW(PROTECTED)
        if (protectedDomains.isProtected(domain) || NeverBlockPolicy.isNeverBlock(domain)) {
            return BlockingDecision.allow("Protected infrastructure", 1.0f).copy(reasonCode = DecisionReason.PROTECTED)
        }

        // Step 6b: Mobile Unbreak protection (essential mobile app dependencies) -> ALLOW(UNBREAK)
        if (com.unblocker.app.logic.analysis.MobileUnbreakList.isEssentialDomain(domain)) {
            return BlockingDecision.allow("Essential mobile app dependency", 1.0f).copy(
                reasonCode = DecisionReason.ALLOWED
            )
        }

        // Step 7: rules (shipped + downloaded): ALLOW beats BLOCK; BLOCK only if admitted
        val isAdBlocking = isAdBlockingEnabled()
        if (isAdBlocking) {
            // In BASIC mode, allow game reward ads and shopping redirects to prevent app breakage
            val currentTier = filteringTier()
            if (currentTier == com.unblocker.app.data.preferences.FilteringTier.BASIC &&
                com.unblocker.app.logic.analysis.MobileUnbreakList.isRewardOrShoppingDomain(domain)) {
                return BlockingDecision.allow("Basic mode: game reward and shopping redirect preserved", 1.0f).copy(
                    reasonCode = DecisionReason.ALLOWED
                )
            }

            val rule = adDetector.matchRule(domain)
            if (rule != null) {
                if (rule.action == RuleAction.ALLOW) {
                    return BlockingDecision(
                        action = BlockingAction.ALLOW,
                        reason = "Shipped allow rule",
                        confidence = 1.0f,
                        category = rule.category,
                        reasonCode = DecisionReason.ALLOWED,
                        ruleValue = rule.value,
                        ruleKind = rule.kind,
                        sourceId = rule.sourceId
                    )
                } else if (rule.action == RuleAction.BLOCK) {
                    return BlockingDecision(
                        action = BlockingAction.BLOCK,
                        reason = if (domain == rule.value) "Static List Match ($domain)" else "Domain Suffix Match (${rule.value})",
                        confidence = 0.98f,
                        category = rule.category,
                        reasonCode = DecisionReason.STATIC_RULE,
                        ruleValue = rule.value,
                        ruleKind = rule.kind,
                        sourceId = rule.sourceId
                    )
                }
            }

            val (isAdSeed, seedReason) = adDetector.isAdDomain(domain)
            if (isAdSeed) {
                return BlockingDecision(
                    action = BlockingAction.BLOCK,
                    reason = seedReason,
                    confidence = 0.98f,
                    category = BlockingCategory.AD,
                    reasonCode = DecisionReason.STATIC_RULE
                )
            }
        }

        // Step 8: learner: BLOCK only if registrable domain is not protected and not user-allowed -> BLOCK(LEARNED)
        if (isAdBlocking) {
            val regDomain = psl.registrableDomain(domain) ?: domain
            if (!protectedDomains.isProtected(regDomain) && !isAllowlisted(regDomain)) {
                val adaptiveDecision = adaptiveBlockingEngine.evaluateDomain(domain)
                if (adaptiveDecision.isBlocked) {
                    return adaptiveDecision
                }
            }
        }

        // Step 9: adult filter (if enabled): list / TLD / label-token patterns -> BLOCK(ADULT_*)
        val isAdultBlocking = isAdultBlockingEnabled()
        if (isAdultBlocking) {
            val adult = adultContentDetector.match(domain)
            if (adult.blocked) {
                return BlockingDecision(
                    action = BlockingAction.BLOCK,
                    reason = adult.reason,
                    confidence = 0.92f,
                    category = BlockingCategory.ADULT_CONTENT,
                    reasonCode = adult.code
                )
            }
        }

        // Step 10: else ALLOW(NO_MATCH)
        return BlockingDecision.allow("Allowed benign traffic", 1.0f).copy(reasonCode = DecisionReason.NO_MATCH)
    }
}
