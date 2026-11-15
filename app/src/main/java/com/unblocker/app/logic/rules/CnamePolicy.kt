package com.unblocker.app.logic.rules

import com.unblocker.app.logic.analysis.ProtectedDomains

/**
 * Rechecks alias blocks, including on cache hits, against current original-host exceptions.
 *
 * @param enabled Current ad-filter toggle.
 * @param originalAllowed User allow-rule predicate for the original hostname.
 * @param matcher Current static and extension rule matcher.
 * @param protectedDomains Protection policy for the original hostname.
 */
class CnamePolicy(
    private val enabled: () -> Boolean,
    private val originalAllowed: (String) -> Boolean,
    private val matcher: (String) -> DnsRule?,
    private val protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT
) {
    /**
     * Finds a blocked alias only when filtering and original-host policy permit it.
     *
     * @param original Original queried hostname.
     * @param aliases Validated CNAME targets in response order.
     * @return First blocked alias, or null for an exempt original or no matching alias.
     */
    fun blockedAlias(original: String, aliases: List<String>): String? {
        if (!enabled() || originalAllowed(original) || protectedDomains.isProtected(original) || matcher(original)?.action == RuleAction.ALLOW) return null
        return aliases.firstOrNull { matcher(it)?.action == RuleAction.BLOCK }
    }
}
