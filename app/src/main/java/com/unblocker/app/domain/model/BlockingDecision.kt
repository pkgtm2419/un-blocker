package com.unblocker.app.domain.model

enum class BlockingAction {
    BLOCK,
    ALLOW
}

enum class BlockingCategory {
    AD,
    TRACKER,
    ADULT_CONTENT,
    NORMAL
}

/**
 * Domain model representing a verified blocking decision produced by the on-device filtering pipeline.
 * Aligned with Clean Architecture specifications in un-blocker-improvement-plan.md.
 */
data class BlockingDecision(
    val action: BlockingAction,
    val reason: String,
    val confidence: Float,
    val category: BlockingCategory,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isBlocked: Boolean get() = action == BlockingAction.BLOCK

    companion object {
        fun allow(reason: String = "Allowed normal traffic", confidence: Float = 1.0f): BlockingDecision {
            return BlockingDecision(
                action = BlockingAction.ALLOW,
                reason = reason,
                confidence = confidence,
                category = BlockingCategory.NORMAL
            )
        }

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
