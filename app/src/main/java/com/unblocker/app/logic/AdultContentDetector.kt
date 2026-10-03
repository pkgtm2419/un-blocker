package com.unblocker.app.logic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.logic.analysis.PublicSuffixRules

data class AdultMatch(val blocked: Boolean, val reason: String, val code: DecisionReason)

class AdultContentDetector(
    private val context: Context? = null,
    private val suffixRules: PublicSuffixRules = PublicSuffixRules.from(context)
) {

    private val adultDomains = HashSet<String>(6000)

    // Safe domains/words to prevent false positives for major websites and services
    private val safeExceptions = setOf(
        "essex.ac.uk", "sussex.ac.uk", "middlesex.edu", "wessex.com",
        "sextant.com", "adulteducation.org", "learnadults.com", "isex.edu",
        "youtube.com", "youtu.be", "youtubekids.com", "ytimg.com", "googlevideo.com",
        "google.com", "gstatic.com", "googleapis.com", "ggpht.com", "vimeo.com",
        "dailymotion.com", "metacafe.com", "twitch.tv", "netflix.com", "nflxvideo.net",
        "facebook.com", "instagram.com", "twitter.com", "x.com", "reddit.com",
        "wikipedia.org", "github.com", "amazon.com", "microsoft.com", "apple.com",
        "spotify.com", "linkedin.com", "cloudflare.com", "stripe.com", "adultswim.com",
        "sexualhealth.org"
    )

    private val adultTlds = setOf("xxx", "adult", "porn", "sex")

    private val adultPatterns = listOf(
        Regex(".*(?:^|[\\.-])(?:porn|porno|xxx|nsfw|erotic|erotica|hentai|milf|brazzers|xvideos|pornhub|xnxx|chaturbate|stripchat|livejasmin|spankbang|youporn|redtube)(?:[\\.-]|$).*"),
        Regex(".*-porn-.*"),
        Regex(".*-xxx-.*"),
        Regex(".*(?:red|x|porno|dirty|free|wet|spank|erotic|sex)tube\\d*\\.(?:com|net|org|xxx)$"),
        Regex(".*(?:^|[\\.-])(?:cam-sex|sex-cam|strip-club|strip-poker|adult-video|adult-movie|sex-video|live-cam-strip)(?:[\\.-]|$).*")
    )

    init {
        loadStaticList()
    }

    private fun isInstitutional(domain: String): Boolean {
        val labels = domain.split('.')
        for (i in 1 until labels.size) {
            val suffix = labels.drop(i).joinToString(".")
            if (suffix == "gov" || suffix == "edu" || suffix == "mil" || suffix == "ac" ||
                suffix.startsWith("gov.") || suffix.startsWith("edu.") ||
                suffix.startsWith("ac.") || suffix.startsWith("mil.")) {
                return true
            }
        }
        return false
    }

    fun loadStaticList() {
        if (context == null) {
            val fallback = listOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
                "chaturbate.com", "cam4.com", "livejasmin.com", "stripchat.com", "xhamster.com",
                "bongacams.com", "myfreecams.com", "eporner.com", "spankbang.com", "brazzers.com"
            )
            fallback.mapNotNull(DomainName::normalize).forEach(adultDomains::add)
            return
        }
        try {
            context.assets.open("adult_domains.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            DomainName.normalize(trimmed)?.let(adultDomains::add)
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
            fallback.mapNotNull(DomainName::normalize).forEach(adultDomains::add)
        }
    }

    fun addDomain(domain: String) {
        DomainName.normalize(domain)?.let(adultDomains::add)
    }

    fun isAdultContent(rawDomain: String): Pair<Boolean, String> = match(rawDomain).let { it.blocked to it.reason }

    fun match(rawDomain: String): AdultMatch {
        val domain = DomainName.normalize(rawDomain) ?: return AdultMatch(false,"",DecisionReason.ALLOWED)

        // False positive prevention
        for (safe in safeExceptions) {
            if (domain == safe || domain.endsWith(".$safe")) {
                return AdultMatch(false,"",DecisionReason.ALLOWED)
            }
        }

        // 1. Direct match in adult domains
        if (adultDomains.contains(domain)) {
            return AdultMatch(true,"Adult Database Match ($domain)",DecisionReason.ADULT_STATIC)
        }

        // 2. Subdomain check
        var parentDomain = domain
        while (parentDomain.contains('.')) {
            parentDomain = parentDomain.substringAfter('.')
            if (adultDomains.contains(parentDomain)) {
                return AdultMatch(true,"Adult Domain Suffix Match ($parentDomain)",DecisionReason.ADULT_STATIC)
            }
        }

        // 3. TLD Check (.xxx, .adult, .porn, .sex)
        val tld = domain.substringAfterLast('.', "")
        if (adultTlds.contains(tld)) {
            return AdultMatch(true,"Adult TLD Rule (.$tld)",DecisionReason.ADULT_TLD)
        }

        // Institutional domains skip pattern matching
        if (isInstitutional(domain)) {
            return AdultMatch(false,"",DecisionReason.ALLOWED)
        }

        // 4. Pattern Matching on registrable domain and subdomains (exclude public suffix)
        val evalTarget = suffixRules.registrableDomain(domain) ?: domain
        for (pattern in adultPatterns) {
            if (pattern.matches(evalTarget) || pattern.matches(domain)) {
                return AdultMatch(true,"Adult Pattern Match",DecisionReason.ADULT_PATTERN)
            }
        }

        return AdultMatch(false,"",DecisionReason.ALLOWED)
    }

    fun getDomainCount(): Int = adultDomains.size
}
