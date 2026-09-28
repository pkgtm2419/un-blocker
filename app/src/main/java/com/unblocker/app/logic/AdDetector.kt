package com.unblocker.app.logic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class AdDetector(private val context: Context? = null) {

    private val adDomains = HashSet<String>(15000)

    private val adPatterns = listOf(
        // DoH Canary Domain
        Regex(".*(?:^|\\.)use-application-dns\\.net$"),
        // Ad subdomains & servers: ad, ads, adserver, adsystem, adservice, adclick, adview, adtech, adnetwork, advertising
        Regex(".*(?:^|\\.)ad[s]?(?:erver|service|system|vertising|vert|click|view|counter|form|tech|track|network|delivery|manager)?\\d*\\..*"),
        // Tracking, metrics, telemetry, pixel, beacon, analytics
        Regex(".*(?:^|\\.)(?:track|tracker|tracking|telemetry|analytics|metrics|pixel|beacon|syndication|affiliate|sponsor|bidder|monetiz)\\d*\\..*"),
        // Pagead, Google Syndication, Doubleclick, Google Ad Services
        Regex(".*(?:^|\\.)pagead\\d?\\..*"),
        Regex(".*(?:^|\\.)googlesyndication\\.com$"),
        Regex(".*(?:^|\\.)doubleclick\\.net$"),
        Regex(".*(?:^|\\.)googleadservices\\.com$"),
        // Major programmatic ad networks & exchanges patterns
        Regex(".*(?:^|\\.)(?:criteo|taboola|outbrain|adnxs|pubmatic|rubiconproject|openx|smartadserver|admob|applovin|unityads|vungle|inmobi|ironsrc|branch|kochava|appsflyer|adjust|chartboost|liftoff|fyber|pangle|mintegral|revcontent|mgid|ezoic|sovrn|sharethrough|triplelift|casalemedia|bidswitch|moatads|quantserve|scorecardresearch)\\..*")
    )

    init {
        loadStaticList()
    }

    fun loadStaticList() {
        if (context == null) {
            val fallback = listOf(
                "doubleclick.net", "googleads.g.doubleclick.net", "adservice.google.com",
                "pagead2.googlesyndication.com", "pubads.g.doubleclick.net", "admob.com",
                "applovin.com", "unityads.unity3d.com", "vungle.com", "inmobi.com",
                "ironsrc.com", "criteo.com", "taboola.com", "outbrain.com", "adnxs.com",
                "adjust.com", "appsflyer.com", "branch.io", "kochava.com", "flurry.com",
                "mixpanel.com", "segment.io", "amplitude.com", "hotjar.com", "clarity.ms"
            )
            fallback.mapNotNull(DomainName::normalize).forEach(adDomains::add)
            return
        }
        try {
            context.assets.open("ad_domains.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            DomainName.normalize(trimmed)?.let(adDomains::add)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback default top domains
            val fallback = listOf(
                "doubleclick.net", "googleads.g.doubleclick.net", "adservice.google.com",
                "pagead2.googlesyndication.com", "pubads.g.doubleclick.net", "admob.com",
                "applovin.com", "unityads.unity3d.com", "vungle.com", "inmobi.com",
                "ironsrc.com", "criteo.com", "taboola.com", "outbrain.com", "adnxs.com",
                "adjust.com", "appsflyer.com", "branch.io", "kochava.com", "flurry.com",
                "mixpanel.com", "segment.io", "amplitude.com", "hotjar.com", "clarity.ms"
            )
            fallback.mapNotNull(DomainName::normalize).forEach(adDomains::add)
        }
    }

    fun addDomain(domain: String) {
        DomainName.normalize(domain)?.let(adDomains::add)
    }

    fun isAdDomain(rawDomain: String): Pair<Boolean, String> {
        val domain = DomainName.normalize(rawDomain) ?: return Pair(false, "")

        // 0. DoH Canary Domain Check (forces Chrome & Firefox to use local DNS)
        if (domain == "use-application-dns.net" || domain.endsWith(".use-application-dns.net")) {
            return Pair(true, "DoH Canary Domain Block")
        }

        // 1. Direct match
        if (adDomains.contains(domain)) {
            return Pair(true, "Static List Match ($domain)")
        }

        // 2. Subdomain check (e.g. sub.domain.com -> domain.com)
        var parentDomain = domain
        while (parentDomain.contains('.')) {
            parentDomain = parentDomain.substringAfter('.')
            if (adDomains.contains(parentDomain)) {
                return Pair(true, "Domain Suffix Match ($parentDomain)")
            }
        }

        // 3. Pattern match
        for (pattern in adPatterns) {
            if (pattern.matches(domain)) {
                return Pair(true, "Pattern Match: ${pattern.pattern}")
            }
        }

        return Pair(false, "")
    }

    fun getDomainCount(): Int = adDomains.size
}
