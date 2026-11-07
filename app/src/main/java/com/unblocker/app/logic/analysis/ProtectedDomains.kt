package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.rules.RuleKind

interface ProtectedDomains {
    fun isProtected(host: String): Boolean
    fun ruleWouldBlockProtected(kind: RuleKind, host: String): Boolean

    companion object {
        val DEFAULT: ProtectedDomains = DefaultProtectedDomains()
    }
}

class DefaultProtectedDomains(
    private val builtIn: Set<String> = NeverBlockPolicy.getProtectedHosts(),
    private val myDomains: Set<String> = emptySet(),
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback
) : ProtectedDomains {

    override fun isProtected(host: String): Boolean {
        val normalized = DomainName.normalize(host) ?: return false
        if (builtIn.contains(normalized) || (normalized.startsWith("www.") && builtIn.contains(normalized.removePrefix("www.")))) {
            return true
        }
        var h = normalized
        while (h.contains('.')) {
            if (myDomains.contains(h)) return true
            if (h == psl.registrableDomain(normalized)) break
            h = h.substringAfter('.')
        }
        return false
    }

    override fun ruleWouldBlockProtected(kind: RuleKind, host: String): Boolean {
        val normalized = DomainName.normalize(host) ?: return false
        for (protectedHost in builtIn) {
            val matches = when (kind) {
                RuleKind.EXACT -> protectedHost == normalized
                RuleKind.SUFFIX -> protectedHost == normalized || protectedHost.endsWith(".$normalized")
                RuleKind.WILDCARD -> protectedHost.endsWith(".$normalized")
            }
            if (matches) return true
        }

        for (myDomain in myDomains) {
            val matches = when (kind) {
                RuleKind.EXACT -> myDomain == normalized
                RuleKind.SUFFIX -> myDomain == normalized || myDomain.endsWith(".$normalized") || normalized.endsWith(".$myDomain")
                RuleKind.WILDCARD -> myDomain.endsWith(".$normalized") || normalized.endsWith(".$myDomain")
            }
            if (matches) return true
        }

        return false
    }
}
