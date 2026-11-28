package com.unblocker.app.ui.logs

import androidx.annotation.StringRes
import com.unblocker.app.R
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.data.db.entity.DomainStatEntity

/**
 * Localized explanation metadata describing why a domain received its latest outcome (WP-21 / F30).
 *
 * @property outcomeTitleRes Localized outcome label resource (`Allowed`, `Blocked`, `Upstream Failed`).
 * @property reasonTitleRes Localized policy step summary resource.
 * @property reasonDetailRes Localized explanation of how this decision applies.
 */
data class DecisionExplanation(
    @param:StringRes val outcomeTitleRes: Int,
    @param:StringRes val reasonTitleRes: Int,
    @param:StringRes val reasonDetailRes: Int,
)

/**
 * Pure Kotlin decision explainer translating [DomainStatEntity] codes and [DecisionReason] into user-facing copy.
 */
object DecisionExplainer {
    /**
     * Produces localized explanation resources for a domain log aggregate row.
     */
    fun explain(stat: DomainStatEntity): DecisionExplanation {
        if (stat.lastOutcome == DomainStatEntity.OUTCOME_UPSTREAM_FAIL) {
            return DecisionExplanation(
                outcomeTitleRes = R.string.logs_history_decision_failed,
                reasonTitleRes = R.string.why_reason_upstream_fail_title,
                reasonDetailRes = R.string.why_reason_upstream_fail_desc,
            )
        }

        val outcomeRes =
            if (stat.lastOutcome == DomainStatEntity.OUTCOME_BLOCKED || stat.lastDecision == 1) {
                R.string.logs_history_decision_blocked
            } else {
                R.string.logs_history_decision_allowed
            }

        val reason = DecisionReason.entries.getOrNull(stat.lastReason)
        return when (reason) {
            DecisionReason.USER_ALLOW -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_user_allow_title, R.string.why_reason_user_allow_desc)
            }

            DecisionReason.USER_BLOCK -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_user_block_title, R.string.why_reason_user_block_desc)
            }

            DecisionReason.PROTECTED -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_protected_title, R.string.why_reason_protected_desc)
            }

            DecisionReason.PAUSED -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_paused_title, R.string.why_reason_paused_desc)
            }

            DecisionReason.STATIC_RULE, DecisionReason.ALLOWED -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_filter_list_title, R.string.why_reason_filter_list_desc)
            }

            DecisionReason.LEARNED_EVIDENCE -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_learned_title, R.string.why_reason_learned_desc)
            }

            DecisionReason.ADULT_STATIC, DecisionReason.ADULT_TLD, DecisionReason.ADULT_PATTERN -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_adult_title, R.string.why_reason_adult_desc)
            }

            DecisionReason.NO_MATCH -> {
                DecisionExplanation(outcomeRes, R.string.why_reason_no_match_title, R.string.why_reason_no_match_desc)
            }

            else -> {
                if (stat.lastDecision == 1) {
                    DecisionExplanation(outcomeRes, R.string.why_reason_filter_list_title, R.string.why_reason_filter_list_desc)
                } else {
                    DecisionExplanation(outcomeRes, R.string.why_reason_no_match_title, R.string.why_reason_no_match_desc)
                }
            }
        }
    }
}
