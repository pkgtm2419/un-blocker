package com.unblocker.app.domain.usecase

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.domain.model.DecisionReason

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
    private val isAdultBlockingEnabled: () -> Boolean = { true }
) {
    constructor(
        adDetector: AdDetector,
        adultContentDetector: AdultContentDetector,
        adaptiveBlockingEngine: AdaptiveBlockingEngine,
        preferences: FilteringPreferences
    ) : this(
        adDetector = adDetector,
        adultContentDetector = adultContentDetector,
        adaptiveBlockingEngine = adaptiveBlockingEngine,
        isAdBlockingEnabled = { preferences.adBlockingEnabled.value },
        isAdultBlockingEnabled = { preferences.adultBlockingEnabled.value }
    )

    operator fun invoke(rawDomain: String): BlockingDecision {
        val domain = com.unblocker.app.logic.DomainName.normalize(rawDomain)
        if (domain == null) {
            return BlockingDecision.allow("Empty domain query", 1.0f)
        }

        if (isAllowlisted(domain)) {
            adaptiveBlockingEngine.networkLearner.syncFeedback(domain,UserFeedback.ALLOW)
            return BlockingDecision.allow("Local exception", 1.0f).copy(reasonCode=DecisionReason.USER_ALLOW)
        }
        if (isBlocklisted(domain)) {
            adaptiveBlockingEngine.networkLearner.syncFeedback(domain,UserFeedback.BLOCK)
            return BlockingDecision.block("Local block rule", 1.0f, BlockingCategory.CUSTOM).copy(reasonCode=DecisionReason.USER_BLOCK)
        }
        adaptiveBlockingEngine.networkLearner.syncFeedback(domain,UserFeedback.NONE)

        val isAdBlocking = isAdBlockingEnabled()
        val isAdultBlocking = isAdultBlockingEnabled()
        var shippedRuleDecision: BlockingDecision? = null
        
        // 1. Check Ad / Tracker Detection Static Rules
        if (isAdBlocking) {
            val (isAdSeed, seedReason) = adDetector.isAdDomain(domain)
            if (isAdSeed) {
                shippedRuleDecision = BlockingDecision(
                    action = BlockingAction.BLOCK,
                    reason = seedReason,
                    confidence = 0.98f,
                    category = BlockingCategory.AD,
                    reasonCode = DecisionReason.STATIC_RULE
                )
            } else {
                val rule = adDetector.matchRule(domain)
                if (rule != null) {
                    if (rule.action == com.unblocker.app.logic.rules.RuleAction.BLOCK) {
                        shippedRuleDecision = BlockingDecision(
                            action = BlockingAction.BLOCK,
                            reason = "Shipped block rule",
                            confidence = 0.98f,
                            category = rule.category,
                            reasonCode = DecisionReason.STATIC_RULE
                        )
                    } else if (rule.action == com.unblocker.app.logic.rules.RuleAction.ALLOW) {
                        shippedRuleDecision = BlockingDecision(
                            action = BlockingAction.ALLOW,
                            reason = "Shipped allow rule",
                            confidence = 1.0f,
                            category = rule.category,
                            reasonCode = DecisionReason.ALLOWED
                        )
                    }
                }
            }
        }
        
        if (shippedRuleDecision != null) return shippedRuleDecision
        
        // 2. NeverBlock Guard
        if (com.unblocker.app.logic.analysis.NeverBlockPolicy.isNeverBlock(domain)) {
            return BlockingDecision.allow("Protected infrastructure", 1.0f).copy(reasonCode = DecisionReason.ALLOWED)
        }
        
        // 3. Adaptive learner
        if (isAdBlocking) {
            val adaptiveDecision = adaptiveBlockingEngine.evaluateDomain(domain)
            if (adaptiveDecision.isBlocked) return adaptiveDecision
        }

        // 2. Check 18+ Adult Content Filtering (Option 2)
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

        // 3. Allowed Benign Traffic
        return BlockingDecision.allow("Allowed benign traffic", 1.0f)
    }
}
