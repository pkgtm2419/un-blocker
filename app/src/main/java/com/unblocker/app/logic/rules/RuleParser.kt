package com.unblocker.app.logic.rules

import com.unblocker.app.logic.DomainName

object RuleParser {
    fun parse(raw: String): DnsRule? {
        var value = raw.trim()
        if (value.isEmpty() || value.startsWith('#') || value.startsWith('!')) return null
        val action = if (value.startsWith("@@||")) RuleAction.ALLOW else RuleAction.BLOCK
        if (action == RuleAction.ALLOW) value = value.removePrefix("@@")
        val kind = when {
            value.startsWith("||") && value.endsWith('^') -> {
                value = value.substring(2, value.length - 1)
                RuleKind.SUFFIX
            }
            value.startsWith("*.") -> {
                value = value.substring(2)
                RuleKind.WILDCARD
            }
            else -> RuleKind.EXACT
        }
        return DomainName.normalize(value)?.let { DnsRule(action, kind, it) }
    }
}
