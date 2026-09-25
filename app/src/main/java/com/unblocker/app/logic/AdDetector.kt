package com.unblocker.app.logic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class AdDetector(private val context: Context? = null) {

    private val adDomains = HashSet<String>(15000)

    private val adPatterns = listOf(
        Regex(".*(?:^|\\.)ads?\\..*"),
        Regex(".*(?:^|\\.)adservice\\..*"),
        Regex(".*(?:^|\\.)pagead\\d?\\..*"),
        Regex(".*(?:^|\\.)doubleclick\\.net$"),
        Regex(".*(?:^|\\.)googlesyndication\\.com$"),
        Regex(".*(?:^|\\.)admob\\.com$"),
        Regex(".*(?:^|\\.)applovin\\.com$"),
        Regex(".*(?:^|\\.)unityads\\.unity3d\\.com$"),
        Regex(".*(?:^|\\.)vungle\\.com$"),
        Regex(".*(?:^|\\.)inmobi\\.com$"),
        Regex(".*(?:^|\\.)ironsrc\\.com$"),
        Regex(".*(?:^|\\.)criteo\\.(?:com|net)$"),
        Regex(".*(?:^|\\.)taboola\\.com$"),
        Regex(".*(?:^|\\.)outbrain\\.com$"),
        Regex(".*(?:^|\\.)adnxs\\.com$"),
        Regex(".*(?:^|\\.)rubiconproject\\.com$"),
        Regex(".*(?:^|\\.)pubmatic\\.com$"),
        Regex(".*(?:^|\\.)openx\\.net$"),
        Regex(".*(?:^|\\.)moatads\\.com$"),
        Regex(".*(?:^|\\.)scorecardresearch\\.com$"),
        Regex(".*(?:^|\\.)quantserve\\.com$"),
        Regex(".*(?:^|\\.)amazon-adsystem\\.com$"),
        Regex(".*(?:^|\\.)adjust\\.com$"),
        Regex(".*(?:^|\\.)appsflyer\\.com$"),
        Regex(".*(?:^|\\.)branch\\.io$"),
        Regex(".*(?:^|\\.)kochava\\.com$"),
        Regex(".*(?:^|\\.)telemetry\\..*"),
        Regex(".*(?:^|\\.)analytics\\..*"),
        Regex(".*(?:^|\\.)metrics\\..*"),
        Regex(".*(?:^|\\.)tracker\\..*")
    )

    init {
        loadStaticList()
    }

    fun loadStaticList() {
        if (context == null) return
        try {
            context.assets.open("ad_domains.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            adDomains.add(trimmed)
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
                "ironsrc.com", "criteo.com", "taboola.com", "outbrain.com", "adnxs.com"
            )
            adDomains.addAll(fallback)
        }
    }

    fun addDomain(domain: String) {
        adDomains.add(domain.trim().lowercase())
    }

    fun isAdDomain(rawDomain: String): Pair<Boolean, String> {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) return Pair(false, "")

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
