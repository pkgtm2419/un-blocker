package com.unblocker.app.logic.rules

import com.unblocker.app.logic.analysis.ProtectedDomains
import com.unblocker.app.logic.analysis.PublicSuffixRules

interface RuleAdmission {
    fun check(action: RuleAction, kind: RuleKind, host: String): SkipReason?
}

class DefaultRuleAdmission(
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback,
    private val protected: ProtectedDomains = ProtectedDomains.DEFAULT
) : RuleAdmission {
    override fun check(action: RuleAction, kind: RuleKind, host: String): SkipReason? {
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) return SkipReason.IP_LITERAL
        if (!host.contains('.')) return SkipReason.SINGLE_LABEL                 // 'com', 'localhost'
        if (psl.isPublicSuffix(host)) return SkipReason.PUBLIC_SUFFIX           // 'co.uk', 'github.io', 'blogspot.com'
        if (action == RuleAction.BLOCK && protected.ruleWouldBlockProtected(kind, host)) return SkipReason.PROTECTED_HOST
        if (QuarantineList.contains(kind, host)) return SkipReason.QUARANTINED  // asset rule-quarantine.txt
        return null
    }
}
