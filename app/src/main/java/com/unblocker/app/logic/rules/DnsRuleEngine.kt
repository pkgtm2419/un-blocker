package com.unblocker.app.logic.rules

import com.unblocker.app.logic.DomainName

/**
 * Indexes a compiled rule snapshot with allow-rule precedence.
 *
 * @param ruleSet Rules to index by action, scope and hostname.
 */
class DnsRuleEngine(
    ruleSet: CompiledRuleSet,
) {
    private val indexes =
        ruleSet.rules.groupBy { it.action }.mapValues { (_, rules) ->
            rules.groupBy { it.kind }.mapValues { (_, typed) -> typed.associateBy { it.value } }
        }

    /**
     * Checks exact hosts before progressively broader suffix and wildcard matches.
     *
     * @param raw Hostname to normalize and match.
     * @return Matching allow rule before any block rule, or null for invalid/unmatched input.
     */
    fun match(raw: String): DnsRule? {
        val domain = DomainName.normalize(raw) ?: return null
        for (action in arrayOf(RuleAction.ALLOW, RuleAction.BLOCK)) {
            val kinds = indexes[action] ?: continue
            kinds[RuleKind.EXACT]?.get(domain)?.let { return it }
            var suffix = domain
            while (true) {
                kinds[RuleKind.SUFFIX]?.get(suffix)?.let { return it }
                if (suffix != domain) kinds[RuleKind.WILDCARD]?.get(suffix)?.let { return it }
                val dot = suffix.indexOf('.')
                if (dot < 0) break
                suffix = suffix.substring(dot + 1)
            }
        }
        return null
    }
}
