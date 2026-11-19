package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.rules.*

/**
 * Matches overlay, extension, off-heap and compiled DNS advertising rules in that order.
 *
 * @param context Asset context, or null to use seed rules.
 * @param compiledRules Optional rules converted to the binary matcher.
 * @param ruleSet Explicit matcher taking precedence over compiled rules and assets.
 * @param offHeapRuleEngine Optional additional block-rule source.
 */
class AdDetector(
    private val context: Context? = null,
    private val compiledRules: CompiledRuleSet? = null,
    private val ruleSet: RuleSet? = null,
    private val offHeapRuleEngine: com.unblocker.data.cache.OffHeapRuleEngine? = null,
) {
    /**
     * User-added block domains (suffix rules). Stored as a HashSet for O(1) lookup
     * instead of a linear list scan on every query.
     */
    private val overlay = HashSet<String>()
    private val admission: RuleAdmission = DefaultRuleAdmission()
    private val activeRuleSet: RuleSet by lazy {
        ruleSet ?: compiledRules?.toRuleSet() ?: if (context != null) RuleSetHolder.get(context) else fallbackRuleSet()
    }

    /**
     * Adds an admitted suffix block rule to the in-memory overlay.
     *
     * @param domain User-entered domain; invalid or unsafe entries are ignored.
     */
    @Synchronized fun addDomain(domain: String) {
        DomainName.normalizeUserEntry(domain)?.let {
            if (admission.check(RuleAction.BLOCK, RuleKind.SUFFIX, it) == null) {
                overlay.add(it)
            }
        }
    }

    /**
     * Finds the first matching rule across the configured sources.
     *
     * @param domain DNS hostname to normalize and match.
     * @return Matching rule, or null for invalid names or no match.
     */
    fun matchRule(domain: String): DnsRule? {
        val normalized = DomainName.normalize(domain) ?: return null
        synchronized(overlay) {
            // 1. User overlay: exact and suffix match
            if (normalized in overlay) return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, normalized, BlockingCategory.AD)
            // 2. Parent suffix walk for overlay rules
            var dot = normalized.indexOf('.')
            while (dot != -1) {
                val nextDot = normalized.indexOf('.', dot + 1)
                if (nextDot == -1) break
                val parentSuffix = normalized.substring(dot + 1)
                if (parentSuffix in overlay) {
                    return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, parentSuffix, BlockingCategory.AD)
                }
                dot = nextDot
            }
        }
        ExtensionRuleStore.matchRule(normalized)?.let { return it }
        if (offHeapRuleEngine != null && offHeapRuleEngine.isLoaded() &&
            offHeapRuleEngine.isDomainBlocked(normalized)
        ) {
            return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, normalized, BlockingCategory.AD)
        }
        return activeRuleSet.match(domain)
    }

    /**
     * Checks the compatibility canary and configured advertising rules.
     *
     * @param raw DNS hostname to evaluate.
     * @return Block flag and explanation; unmatched names have an empty reason.
     */
    fun isAdDomain(raw: String): Pair<Boolean, String> {
        val domain = DomainName.normalize(raw) ?: return false to ""
        if (domain == "use-application-dns.net" || domain.endsWith(".use-application-dns.net")) {
            return true to "Firefox DoH canary compatibility rule"
        }
        val rule = matchRule(domain) ?: return false to ""
        if (rule.action == RuleAction.ALLOW) return false to "Shipped exception"
        return true to
            if (domain == rule.value) {
                "Static List Match ($domain)"
            } else {
                "Domain Suffix Match (${rule.value})"
            }
    }

    /**
     * Returns the sum of source rule counts, including overlaps between sources.
     */
    @Synchronized fun getDomainCount(): Int =
        activeRuleSet.ruleCount + overlay.size + ExtensionRuleStore.ruleCount + (offHeapRuleEngine?.ruleCount() ?: 0)

    /**
     * Provides seed domains used when compiled assets are unavailable.
     */
    companion object {
        private fun fallbackRuleSet(): RuleSet {
            val rules =
                fallback.mapNotNull(DomainName::normalize).map {
                    DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it, BlockingCategory.AD)
                }
            return CompiledRuleSet(rules).toRuleSet()
        }

        /**
         * Built-in suffix-rule seed domains, subject to the final decision pipeline precedence.
         */
        val defaultSeedRules =
            listOf(
                "doubleclick.net",
                "googleads.g.doubleclick.net",
                "adservice.google.com",
                "pagead2.googlesyndication.com",
                "pubads.g.doubleclick.net",
                "admob.com",
                "a.applovin.com",
                "unityads.unity3d.com",
                "vungle.com",
                "inmobi.com",
                "ironsrc.com",
                "criteo.com",
                "taboola.com",
                "outbrain.com",
                "adnxs.com",
                "app.adjust.com",
                "appsflyer.com",
                "appsflyersdk.com",
                "moloco.com",
                "branch.io",
                "kochava.com",
                "flurry.com",
                "api.mixpanel.com",
                "api.segment.io",
                "api.amplitude.com",
                "static.hotjar.com",
                "clarity.ms",
                "recrampwiped.com",
                "adsboosters.xyz",
                "hedeuntacks.com",
                "legbaratwind.com",
                "ronracepub.com",
                "popads.net",
                "popcash.net",
                "adsterra.com",
                "propellerads.com",
                "monetag.com",
                "clickadu.com",
                "hilltopads.com",
                "galaksion.com",
                "admaven.com",
                "onclickads.net",
                "droplink.co",
                "openx.net",
                "mintegral.com",
                "rayjump.com",
                "ironsource.com",
                "pangle.io",
                "chartboost.com",
                "fyber.com",
                "tapjoy.com",
                "an.facebook.com",
                "atlassbx.com",
                "mopub.com",
                "advertising.apple.com",
                "deofleadyetca.org",
                "razorpayads.com",
                "aureliatmeses.com",
                "sneerscuartel.com",
                "kernesowan.com",
                "busycdn.xyz",
                "bflixcdn.xyz",
                "googlesyndication.com",
                "s.youtube.com",
                "imasdk.googleapis.com",
            )

        private val fallback = defaultSeedRules
    }
}
