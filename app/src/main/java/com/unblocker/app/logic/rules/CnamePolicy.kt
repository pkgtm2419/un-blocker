package com.unblocker.app.logic.rules

/** Called again on cache hits: toggles and original-host exceptions stay authoritative. */
class CnamePolicy(
    private val enabled: () -> Boolean,
    private val originalAllowed: (String) -> Boolean,
    private val matcher: (String) -> DnsRule?
) {
    fun blockedAlias(original: String, aliases: List<String>): String? {
        if (!enabled() || originalAllowed(original) || matcher(original)?.action == RuleAction.ALLOW) return null
        return aliases.firstOrNull { matcher(it)?.action == RuleAction.BLOCK }
    }
}
