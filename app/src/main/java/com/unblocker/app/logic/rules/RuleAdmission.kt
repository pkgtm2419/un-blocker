package com.unblocker.app.logic.rules

import com.unblocker.app.logic.analysis.ProtectedDomains
import com.unblocker.app.logic.analysis.PublicSuffixRules

/**
 * Safety boundary between valid DNS syntax and permitted list rules.
 */
interface RuleAdmission {
    /**
     * Checks whether a candidate rule may be admitted.
     *
     * @param action Allow or block action.
     * @param kind Hostname matching scope.
     * @param host Normalized hostname.
     * @return Skip reason, or null when admitted.
     */
    fun check(action: RuleAction, kind: RuleKind, host: String): SkipReason?
}

/**
 * Rejects IP literals, broad suffixes, protected block coverage and quarantined rules.
 *
 * @param psl Public suffix context for broad-rule checks.
 * @param protected Protection policy for candidate block coverage.
 */
class DefaultRuleAdmission(
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback,
    private val protected: ProtectedDomains = ProtectedDomains.DEFAULT
) : RuleAdmission {
    /**
     * Applies IP, single-label, suffix, protected-host and quarantine gates in order.
     *
     * @param action Candidate allow or block action.
     * @param kind Candidate hostname scope.
     * @param host Normalized hostname.
     * @return First skip reason, or null when admitted.
     */
    override fun check(action: RuleAction, kind: RuleKind, host: String): SkipReason? {
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) return SkipReason.IP_LITERAL
        if (!host.contains('.')) return SkipReason.SINGLE_LABEL                 // 'com', 'localhost'
        if (psl.isPublicSuffix(host)) return SkipReason.PUBLIC_SUFFIX           // 'co.uk', 'github.io', 'blogspot.com'
        if (action == RuleAction.BLOCK && protected.ruleWouldBlockProtected(kind, host)) return SkipReason.PROTECTED_HOST
        if (QuarantineList.contains(kind, host)) return SkipReason.QUARANTINED  // asset rule-quarantine.txt
        return null
    }
}
