package com.unblocker.app.logic.analysis

/**
 * Stages of local evidence accumulation and suppression.
 */
enum class ReputationState { UNKNOWN, OBSERVING, SUSPECT, CONFIRMED, SUPPRESSED }

/**
 * Explicit allow/block intent, or absence of user feedback.
 */
enum class UserFeedback { NONE, ALLOW, BLOCK }

/**
 * Persistable coarse evidence for an opaque hostname identifier.
 *
 * @property score Combined evidence score from zero through one.
 * @property state Current evidence accumulation state.
 * @property mask Lexical/entropy/cadence/structural bits 1/2/4/8 and trusted-alias bit 16.
 * @property positiveWindows Count of qualifying observation windows, bounded to 255 by policy.
 * @property feedback User intent synchronized from explicit rules.
 * @property dayBucket Coarse last-update day, normally Unix epoch days.
 */
data class ReputationEvidence(
    val score: Float = 0f,
    val state: ReputationState = ReputationState.UNKNOWN,
    val mask: Int = 0,
    val positiveWindows: Int = 0,
    val feedback: UserFeedback = UserFeedback.NONE,
    val dayBucket: Long = 0,
) {
    /**
     * Whether feedback or evidence confirms blocking, with ALLOW feedback taking precedence.
     */
    val confirmed: Boolean get() =
        feedback != UserFeedback.ALLOW &&
            (feedback == UserFeedback.BLOCK || state == ReputationState.CONFIRMED)
}
