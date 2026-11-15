package com.unblocker.app.logic

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.logic.analysis.PublicSuffixRules

/**
 * Result of the DNS-only adult-domain classifier.
 *
 * @property blocked Whether this classifier matched a block condition.
 * @property reason Match explanation, empty when unmatched.
 * @property code Machine-readable reason for the matched or allowed result.
 */
data class AdultMatch(val blocked: Boolean, val reason: String, val code: DecisionReason)

/**
 * Classifies names using exceptions, a static list, TLDs and lexical patterns.
 *
 * @param context Asset context, or null to use embedded domains.
 * @param suffixRules Public suffix context used to select pattern targets.
 */
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

    /**
     * Adds normalized asset domains, falling back to embedded domains when loading fails.
     */
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

    /**
     * Adds a valid user-entered domain to the classifier in memory.
     *
     * @param domain Domain to normalize; invalid entries are ignored.
     */
    fun addDomain(domain: String) {
        DomainName.normalizeUserEntry(domain)?.let(adultDomains::add)
    }

    /**
     * Returns the classifier block flag and explanation.
     *
     * @param rawDomain DNS hostname to classify.
     * @return Block flag and reason, with an empty reason for unmatched or invalid names.
     */
    fun isAdultContent(rawDomain: String): Pair<Boolean, String> = match(rawDomain).let { it.blocked to it.reason }

    /**
     * Evaluates exceptions before static domains, TLDs and lexical patterns.
     *
     * @param rawDomain DNS hostname to normalize and classify.
     * @return Classifier result; invalid and unmatched domains are allowed.
     */
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

    /**
     * Returns the number of distinct domains in the static membership set.
     */
    fun getDomainCount(): Int = adultDomains.size
}
