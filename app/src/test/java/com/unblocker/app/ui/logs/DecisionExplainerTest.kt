package com.unblocker.app.ui.logs

import com.unblocker.app.R
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.data.db.entity.DomainStatEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [DecisionExplainer] mapping outcomes, policy reasons, and `UPSTREAM_FAIL` states (WP-21 / F30).
 */
class DecisionExplainerTest {
    @Test
    fun upstreamFailOutcomeMapsToDedicatedFailedExplanation() {
        val stat =
            DomainStatEntity(
                domain = "timeout.example.com",
                callCount = 3,
                blockedCount = 0,
                allowedCount = 0,
                firstSeen = 100L,
                lastSeen = 200L,
                lastDecision = 0,
                lastReason = DecisionReason.NO_MATCH.ordinal,
                lastOutcome = DomainStatEntity.OUTCOME_UPSTREAM_FAIL,
                failCount = 3,
                lastNetworkType = "wifi",
            )

        val explanation = DecisionExplainer.explain(stat)
        assertEquals(R.string.logs_history_decision_failed, explanation.outcomeTitleRes)
        assertEquals(R.string.why_reason_upstream_fail_title, explanation.reasonTitleRes)
    }

    @Test
    fun decisionReasonCodesMapToSpecificExplanations() {
        val cases =
            listOf(
                Triple(DecisionReason.USER_ALLOW, 0, R.string.why_reason_user_allow_title),
                Triple(DecisionReason.USER_BLOCK, 1, R.string.why_reason_user_block_title),
                Triple(DecisionReason.PROTECTED, 0, R.string.why_reason_protected_title),
                Triple(DecisionReason.PAUSED, 0, R.string.why_reason_paused_title),
                Triple(DecisionReason.STATIC_RULE, 1, R.string.why_reason_filter_list_title),
                Triple(DecisionReason.LEARNED_EVIDENCE, 1, R.string.why_reason_learned_title),
                Triple(DecisionReason.ADULT_STATIC, 1, R.string.why_reason_adult_title),
                Triple(DecisionReason.NO_MATCH, 0, R.string.why_reason_no_match_title),
            )

        for ((reason, decision, expectedTitle) in cases) {
            val stat =
                DomainStatEntity(
                    domain = "example.com",
                    callCount = 1,
                    blockedCount = if (decision == 1) 1 else 0,
                    allowedCount = if (decision == 0) 1 else 0,
                    firstSeen = 1L,
                    lastSeen = 1L,
                    lastDecision = decision,
                    lastReason = reason.ordinal,
                    lastOutcome = decision,
                )
            assertEquals(expectedTitle, DecisionExplainer.explain(stat).reasonTitleRes)
        }
    }
}
