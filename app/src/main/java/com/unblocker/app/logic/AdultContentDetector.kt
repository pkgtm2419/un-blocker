package com.unblocker.app.logic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class AdultContentDetector(private val context: Context? = null) {

    private val adultDomains = HashSet<String>(6000)

    // Safe domains/words to prevent false positives
    private val safeExceptions = setOf(
        "essex.ac.uk", "sussex.ac.uk", "middlesex.edu", "wessex.com",
        "sextant.com", "adulteducation.org", "learnadults.com", "isex.edu"
    )

    private val adultTlds = setOf("xxx", "adult", "porn", "sex", "cam")

    private val adultPatterns = listOf(
        Regex(".*(?:^|[\\.-])(?:porn|sex|xxx|nsfw|erotic|cams?|strip|hentai|milf|fap|brazzers|xvideos|pornhub|xnxx).*"),
        Regex(".*-porn-.*"),
        Regex(".*-sex-.*"),
        Regex(".*-xxx-.*"),
        Regex(".*-cam-.*"),
        Regex(".*tube\\d*\\.(?:com|net|org|xxx)$"),
        Regex(".*(?:adult|erotica|chaturbate|stripchat|livejasmin).*")
    )

    init {
        loadStaticList()
    }

    fun loadStaticList() {
        if (context == null) return
        try {
            context.assets.open("adult_domains.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            adultDomains.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val fallback = listOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
                "chaturbate.com", "cam4.com", "livejasmin.com", "stripchat.com", "xhamster.com",
                "bongacams.com", "myfreecams.com", "eporner.com", "spankbang.com", "brazzers.com"
            )
            adultDomains.addAll(fallback)
        }
    }

    fun addDomain(domain: String) {
        adultDomains.add(domain.trim().lowercase())
    }

    fun isAdultContent(rawDomain: String): Pair<Boolean, String> {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) return Pair(false, "")

        // False positive prevention
        for (safe in safeExceptions) {
            if (domain == safe || domain.endsWith(".$safe")) {
                return Pair(false, "")
            }
        }

        // 1. Direct match in adult domains
        if (adultDomains.contains(domain)) {
            return Pair(true, "Adult Database Match ($domain)")
        }

        // 2. Subdomain check
        var parentDomain = domain
        while (parentDomain.contains('.')) {
            parentDomain = parentDomain.substringAfter('.')
            if (adultDomains.contains(parentDomain)) {
                return Pair(true, "Adult Domain Suffix Match ($parentDomain)")
            }
        }

        // 3. TLD Check (.xxx, .adult, .porn, .sex)
        val tld = domain.substringAfterLast('.', "")
        if (adultTlds.contains(tld)) {
            return Pair(true, "Adult TLD Rule (.$tld)")
        }

        // 4. Pattern Matching
        for (pattern in adultPatterns) {
            if (pattern.matches(domain)) {
                return Pair(true, "Adult Pattern Match: ${pattern.pattern}")
            }
        }

        return Pair(false, "")
    }

    fun getDomainCount(): Int = adultDomains.size
}
