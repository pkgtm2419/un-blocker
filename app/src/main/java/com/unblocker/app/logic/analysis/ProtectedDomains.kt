package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.rules.RuleKind

/**
 * Checks infrastructure and user-selected protection during decisions and rule admission.
 */
interface ProtectedDomains {
    /**
     * Tests whether protection applies to a hostname.
     *
     * @param host DNS hostname to check.
     * @return Whether the implementation protects that hostname.
     */
    fun isProtected(host: String): Boolean

    /**
     * Tests whether protection applies to an already-normalized canonical hostname.
     *
     * @param normalized Canonical DNS hostname to check.
     * @return Whether the implementation protects that hostname.
     */
    fun isProtectedNormalized(normalized: String): Boolean = isProtected(normalized)

    /**
     * Tests whether a proposed rule intersects protected names.
     *
     * @param kind Exact, suffix or strict-subdomain scope.
     * @param host Root hostname of the candidate rule.
     * @return Whether the rule would cover protected infrastructure or user-selected domains.
     */
    fun ruleWouldBlockProtected(
        kind: RuleKind,
        host: String,
    ): Boolean

    /**
     * Default built-in protection policy.
     */
    companion object {
        /**
         * Protection backed by the current NeverBlockPolicy hosts, without user My domains entries.
         */
        val DEFAULT: ProtectedDomains = DefaultProtectedDomains()
    }
}

/**
 * Checks built-in exact hosts and user-selected subtrees bounded by registrable domains.
 *
 * @param builtInSupplier Current built-in protected hostname entries.
 * @param myDomainsSupplier Current user-selected protected subtree roots.
 * @param psl Public suffix context bounding ancestor checks.
 */
class DefaultProtectedDomains(
    private val builtInSupplier: () -> Set<String> = { NeverBlockPolicy.getProtectedHosts() },
    private val myDomainsSupplier: () -> Set<String> = { emptySet() },
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback,
) : ProtectedDomains {
    /**
     * Builds protection from fixed input sets.
     *
     * @param builtIn Built-in protected hostname entries.
     * @param myDomains User-selected protected subtree roots.
     * @param psl Public suffix context bounding ancestor checks.
     */
    constructor(
        builtIn: Set<String>,
        myDomains: Set<String> = emptySet(),
        psl: PublicSuffixRules = PublicSuffixRules.fallback,
    ) : this({ builtIn }, { myDomains }, psl)

    /**
     * Checks normalized exact/www infrastructure names and My domains ancestors.
     *
     * @param host Hostname to normalize.
     * @return Whether protection matches; invalid names return false.
     */
    override fun isProtected(host: String): Boolean {
        val normalized = DomainName.normalize(host) ?: return false
        return isProtectedNormalized(normalized)
    }

    /**
     * Checks exact/www infrastructure names and My domains ancestors for an already-normalized hostname.
     *
     * @param normalized Canonical hostname to check.
     * @return Whether protection matches.
     */
    override fun isProtectedNormalized(normalized: String): Boolean {
        val builtIn = builtInSupplier()
        if (builtIn.contains(normalized) ||
            (normalized.startsWith("www.") && builtIn.contains(normalized.removePrefix("www.")))
        ) {
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

    /**
     * Checks candidate rule coverage against built-in hosts and My domains subtrees.
     *
     * @param kind Candidate rule scope.
     * @param host Rule hostname to normalize.
     * @return Whether coverage intersects protection; invalid names return false.
     */
    override fun ruleWouldBlockProtected(
        kind: RuleKind,
        host: String,
    ): Boolean {
        val normalized = DomainName.normalize(host) ?: return false
        val builtIn = builtInSupplier()
        for (protectedHost in builtIn) {
            val matches =
                when (kind) {
                    RuleKind.EXACT -> protectedHost == normalized
                    RuleKind.SUFFIX -> protectedHost == normalized || protectedHost.endsWith(".$normalized")
                    RuleKind.WILDCARD -> protectedHost.endsWith(".$normalized")
                }
            if (matches) return true
        }

        val myDomains = myDomainsSupplier()
        for (myDomain in myDomains) {
            val matches =
                when (kind) {
                    RuleKind.EXACT -> {
                        myDomain == normalized
                    }

                    RuleKind.SUFFIX -> {
                        myDomain == normalized || myDomain.endsWith(".$normalized") ||
                            normalized.endsWith(".$myDomain")
                    }

                    RuleKind.WILDCARD -> {
                        myDomain.endsWith(".$normalized") || normalized.endsWith(".$myDomain")
                    }
                }
            if (matches) return true
        }

        return false
    }
}
