package com.unblocker.app.domain.model

import com.unblocker.app.logic.rules.RuleKind

/**
 * Allow or block action for a DNS policy decision.
 */
enum class BlockingAction {
    BLOCK,
    ALLOW
}

/**
 * Content category attached to a filtering decision.
 */
enum class BlockingCategory {
    AD,
    TRACKER,
    ADULT_CONTENT,
    CUSTOM,
    NORMAL
}

/**
 * Machine-readable explanation codes emitted by the decision pipeline.
 */
enum class DecisionReason {
    ALLOWED,
    USER_ALLOW,
    USER_BLOCK,
    STATIC_RULE,
    LEARNED_EVIDENCE,
    ADULT_STATIC,
    ADULT_TLD,
    ADULT_PATTERN,
    PROTECTED,
    PAUSED,
    NO_MATCH,
    ENGINE_ERROR,
    SUGGESTED,
    INVALID
}

/**
 * Domain policy result produced by the on-device filtering pipeline.
 *
 * @property action Allow or block outcome.
 * @property reason Human-readable explanation.
 * @property confidence Classifier confidence, conventionally from zero through one.
 * @property category Content category attributed to this decision.
 * @property timestamp Decision time in Unix epoch milliseconds.
 * @property reasonCode Machine-readable policy reason.
 * @property ruleValue Matched rule hostname when available.
 * @property ruleKind Matched rule scope when available.
 * @property sourceId Source identifier supplied by the matching rule, when available.
 */
data class BlockingDecision(
    val action: BlockingAction,
    val reason: String,
    val confidence: Float,
    val category: BlockingCategory,
    val timestamp: Long = System.currentTimeMillis(),
    val reasonCode: DecisionReason = DecisionReason.ALLOWED,
    val ruleValue: String? = null,
    val ruleKind: RuleKind? = null,
    val sourceId: Int? = null
) {
    /**
     * Whether the action is BLOCK.
     */
    val isBlocked: Boolean get() = action == BlockingAction.BLOCK

    /**
     * Constructs decisions with default metadata.
     */
    companion object {
        /**
         * Creates an allowed normal-content decision with the default ALLOWED reason code.
         *
         * @param reason Human-readable explanation.
         * @param confidence Confidence retained without clamping.
         * @return New allowed decision stamped with the current wall-clock time.
         */
        fun allow(reason: String = "Allowed normal traffic", confidence: Float = 1.0f): BlockingDecision {
            return BlockingDecision(
                action = BlockingAction.ALLOW,
                reason = reason,
                confidence = confidence,
                category = BlockingCategory.NORMAL
            )
        }

        /**
         * Creates a blocked decision with default metadata.
         *
         * @param reason Human-readable explanation.
         * @param confidence Confidence retained without clamping.
         * @param category Category responsible for blocking.
         * @return New blocked decision stamped with the current wall-clock time.
         */
        fun block(
            reason: String,
            confidence: Float,
            category: BlockingCategory = BlockingCategory.AD
        ): BlockingDecision {
            return BlockingDecision(
                action = BlockingAction.BLOCK,
                reason = reason,
                confidence = confidence,
                category = category
            )
        }
    }
}
