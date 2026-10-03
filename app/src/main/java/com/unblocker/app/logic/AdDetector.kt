package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.rules.*

class AdDetector(
    private val context: Context? = null,
    private val compiledRules: CompiledRuleSet? = null,
    private val ruleSet: RuleSet? = null
) {
    private val overlay = mutableListOf<DnsRule>()
    private val activeRuleSet: RuleSet by lazy {
        ruleSet ?: compiledRules?.toRuleSet() ?: if (context != null) RuleSetHolder.get(context) else fallbackRuleSet()
    }

    @Synchronized fun addDomain(domain: String) {
        DomainName.normalize(domain)?.let {
            overlay.add(DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it))
        }
    }

    fun matchRule(domain: String): DnsRule? {
        val normalized = DomainName.normalize(domain) ?: return null
        synchronized(overlay) {
            for (action in arrayOf(RuleAction.ALLOW, RuleAction.BLOCK)) {
                for (r in overlay) {
                    if (r.action == action) {
                        if (r.kind == RuleKind.EXACT && r.value == normalized) return r
                        if (r.kind == RuleKind.SUFFIX && (normalized == r.value || normalized.endsWith("." + r.value))) return r
                        if (r.kind == RuleKind.WILDCARD && normalized.endsWith("." + r.value)) return r
                    }
                }
            }
        }
        return activeRuleSet.match(domain)
    }

    fun isAdDomain(raw: String): Pair<Boolean, String> {
        val domain = DomainName.normalize(raw) ?: return false to ""
        if (domain == "use-application-dns.net" || domain.endsWith(".use-application-dns.net")) {
            return true to "Firefox DoH canary compatibility rule"
        }
        val rule = matchRule(domain) ?: return false to ""
        if (rule.action == RuleAction.ALLOW) return false to "Shipped exception"
        return true to if (domain == rule.value) "Static List Match ($domain)"
            else "Domain Suffix Match (${rule.value})"
    }

    @Synchronized fun getDomainCount(): Int = activeRuleSet.ruleCount + overlay.size

    companion object {
        private fun fallbackRuleSet(): RuleSet {
            val rules = fallback.mapNotNull(DomainName::normalize).map {
                DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it, BlockingCategory.AD)
            }
            return CompiledRuleSet(rules).toRuleSet()
        }

        private val fallback = listOf(
            "doubleclick.net", "googleads.g.doubleclick.net", "adservice.google.com",
            "pagead2.googlesyndication.com", "pubads.g.doubleclick.net", "admob.com",
            "applovin.com", "unityads.unity3d.com", "vungle.com", "inmobi.com", "ironsrc.com",
            "criteo.com", "taboola.com", "outbrain.com", "adnxs.com", "adjust.com", "appsflyer.com",
            "branch.io", "kochava.com", "flurry.com", "mixpanel.com", "segment.io", "amplitude.com",
            "hotjar.com", "clarity.ms", "recrampwiped.com", "adsboosters.xyz", "hedeuntacks.com",
            "legbaratwind.com", "ronracepub.com", "popads.net", "popcash.net", "adsterra.com",
            "propellerads.com", "monetag.com", "clickadu.com", "hilltopads.com", "galaksion.com",
            "admaven.com", "onclickads.net", "droplink.co", "openx.net"
        )
    }
}
