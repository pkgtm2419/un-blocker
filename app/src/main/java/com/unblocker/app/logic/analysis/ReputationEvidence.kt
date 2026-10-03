package com.unblocker.app.logic.analysis

enum class ReputationState { UNKNOWN, OBSERVING, SUSPECT, CONFIRMED, SUPPRESSED }
enum class UserFeedback { NONE, ALLOW, BLOCK }
data class ReputationEvidence(
    val score: Float = 0f,
    val state: ReputationState = ReputationState.UNKNOWN,
    val mask: Int = 0,
    val positiveWindows: Int = 0,
    val feedback: UserFeedback = UserFeedback.NONE,
    val dayBucket: Long = 0
) {
    val confirmed: Boolean get() = feedback != UserFeedback.ALLOW &&
        (feedback == UserFeedback.BLOCK || state == ReputationState.CONFIRMED)
}
