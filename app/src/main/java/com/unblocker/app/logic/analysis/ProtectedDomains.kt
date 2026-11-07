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
    private val builtInSupplier: () -> Set<String> = { NeverBlockPolicy.getProtectedHosts() },
    private val myDomainsSupplier: () -> Set<String> = { emptySet() },
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback
) : ProtectedDomains {

    constructor(
        builtIn: Set<String>,
        myDomains: Set<String> = emptySet(),
        psl: PublicSuffixRules = PublicSuffixRules.fallback
    ) : this({ builtIn }, { myDomains }, psl)

    override fun isProtected(host: String): Boolean {
        val normalized = DomainName.normalize(host) ?: return false
        val builtIn = builtInSupplier()
        if (builtIn.contains(normalized) || (normalized.startsWith("www.") && builtIn.contains(normalized.removePrefix("www.")))) {
            return true
        }
        val myDomains = myDomainsSupplier()
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
        val builtIn = builtInSupplier()
        for (protectedHost in builtIn) {
            val matches = when (kind) {
                RuleKind.EXACT -> protectedHost == normalized
                RuleKind.SUFFIX -> protectedHost == normalized || protectedHost.endsWith(".$normalized")
                RuleKind.WILDCARD -> protectedHost.endsWith(".$normalized")
            }
            if (matches) return true
        }

        val myDomains = myDomainsSupplier()
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
