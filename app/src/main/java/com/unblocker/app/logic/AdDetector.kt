package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.logic.rules.*

class AdDetector(private val context: Context? = null, private val compiledRules: CompiledRuleSet? = null) {
    private val seedRules = LinkedHashSet<DnsRule>()
    @Volatile private var engine = DnsRuleEngine(CompiledRuleSet(emptyList()))

    init { loadStaticList() }

    @Synchronized fun loadStaticList() {
        seedRules.clear()
        if (compiledRules != null || context != null) {
            val compiled=compiledRules ?: context!!.assets.open("dns-rules.tsv").reader().use { CompiledRuleSet.fromTsv(it) }
            seedRules.addAll(compiled.rules.filter { it.category==com.unblocker.app.domain.model.BlockingCategory.AD })
        } else fallback.mapNotNull(DomainName::normalize).forEach {
            seedRules.add(DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it))
        }
        engine = DnsRuleEngine(CompiledRuleSet(seedRules.toList()))
    }

    @Synchronized fun addDomain(domain: String) {
        DomainName.normalize(domain)?.let {
            seedRules.add(DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, it))
            engine = DnsRuleEngine(CompiledRuleSet(seedRules.toList()))
        }
    }

    fun matchRule(domain: String): DnsRule? = engine.match(domain)

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

    @Synchronized fun getDomainCount(): Int = seedRules.size

    companion object {
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
