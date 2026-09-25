package com.unblocker.app.logic.analysis

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device autonomous network analysis and self-learning tracking system.
 * Operates 100% locally using system resources without cloud dependencies.
 * Implements Multi-Factor Ad & Tracker Detection as specified in Section 1.1 of un-blocker-improvement-plan.md.
 * Zero user logs or history are created or stored.
 */
class LocalNetworkLearner(private val context: android.content.Context? = null) {

    // In-memory learned reputation cache: domain -> confidence score (0.0 to 1.0)
    private val learnedReputations = ConcurrentHashMap<String, Float>()

    // Temporal cadence tracking: domain -> recent query timestamps (sliding window)
    private val queryTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()

    init {
        loadPersistedTrackers()
    }

    private fun loadPersistedTrackers() {
        if (context == null) return
        try {
            val file = java.io.File(context.filesDir, "learned_trackers.txt")
            if (file.exists()) {
                file.forEachLine { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split(':')
                        val domain = parts[0]
                        val score = if (parts.size > 1) parts[1].toFloatOrNull() ?: 0.85f else 0.85f
                        learnedReputations[domain] = score
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun persistLearnedDomain(domain: String, score: Float) {
        if (context == null) return
        try {
            val file = java.io.File(context.filesDir, "learned_trackers.txt")
            file.appendText("$domain:$score\n")
        } catch (ignored: Exception) {}
    }

    fun getLearnedTrackersCount(): Int = learnedReputations.size
    fun getAllLearnedDomains(): Set<String> = learnedReputations.keys.toSet()

    private val adLexicalTokens = setOf(
        "ad", "ads", "track", "tracker", "tracking", "telemetry", "pixel",
        "beacon", "analytics", "metrics", "dsp", "ssp", "banner", "pop",
        "monetiz", "monetization", "affiliate", "sponsor", "adserver",
        "adsystem", "bidder", "rtb", "stats", "tagmanager", "syndication",
        "doubleclick", "admob", "applovin", "unityads", "vungle", "inmobi", "criteo", "taboola"
    )

    private val knownTrackers = setOf(
        "google-analytics.com", "ssl.google-analytics.com",
        "adjust.com", "appsflyer.com", "branch.io", "kochava.com",
        "flurry.com", "singular.net", "braze.com", "mixpanel.com",
        "segment.com", "segment.io", "amplitude.com", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "clarity.ms", "hotjar.com",
        "newrelic.com", "app-measurement.com"
    )

    private val ddnsProviders = setOf(
        "duckdns.org", "no-ip.biz", "no-ip.com", "ddns.net", "zapto.org",
        "hopto.org", "bounceme.net", "ngrok.io", "servehttp.com"
    )

    private val safeExceptions = setOf(
        "google.com", "android.com", "github.com", "wikipedia.org",
        "stackoverflow.com", "microsoft.com", "apple.com", "cloudflare.com",
        "youtube.com", "youtu.be", "youtubekids.com", "googlevideo.com", "ytimg.com",
        "ggpht.com", "googleapis.com", "gstatic.com", "amazon.com", "aws.amazon.com",
        "netflix.com", "nflxvideo.net", "instagram.com", "facebook.com",
        "fbcdn.net", "whatsapp.com", "twitter.com", "x.com", "twimg.com",
        "reddit.com", "redditmedia.com", "linkedin.com", "spotify.com",
        "spotifycdn.com", "vimeo.com", "twitch.tv", "fastly.net", "akamai.net",
        "akamaiedge.net", "cloudfront.net"
    )

    /**
     * Extracts multi-factor structural and behavioral signatures for deep inspection.
     * Aligned with Section 1.1.1 of un-blocker-improvement-plan.md.
     */
    fun extractDomainSignature(domain: String): DomainSignature {
        val cleanDomain = domain.trim().lowercase()
        val subdomainDepth = cleanDomain.count { it == '.' }
        val hasLexical = evaluateLexical(cleanDomain) > 0.35f
        val hasTracker = knownTrackers.any { cleanDomain == it || cleanDomain.endsWith(".$it") }
        val isDdns = ddnsProviders.any { cleanDomain.endsWith(".$it") }
        val entropy = evaluateEntropy(cleanDomain)
        val cadence = evaluateCadence(cleanDomain, System.currentTimeMillis())

        val suspiciousPatterns = mutableListOf<String>()
        if (subdomainDepth >= 4 && (hasLexical || entropy > 0.5f)) {
            suspiciousPatterns.add("Deep nested subdomain with tracker markers")
        }
        if (isDdns && (hasLexical || entropy > 0.5f)) {
            suspiciousPatterns.add("Dynamic DNS tracker endpoint")
        }
        if (hasTracker) {
            suspiciousPatterns.add("Known tracker framework signature")
        }
        if (entropy > 3.1f) {
            suspiciousPatterns.add("High entropy DGA/algorithmic subdomain")
        }

        val analysis = analyzeQuery(cleanDomain)

        return DomainSignature(
            domain = cleanDomain,
            subdomainDepth = subdomainDepth,
            hasAdLexicalToken = hasLexical,
            hasTrackerSignature = hasTracker,
            isDdnsOrDynamic = isDdns,
            entropy = entropy,
            cadenceScore = cadence,
            suspiciousPatterns = suspiciousPatterns,
            compositeThreatScore = analysis.score
        )
    }

    /**
     * Analyze a network domain query using local behavioral, lexical, and temporal heuristics.
     * Returns a composite suspicion score between 0.0 and 1.0.
     * Supports dynamic adaptive confidence thresholds from the 7-day learning engine.
     */
    fun analyzeQuery(domain: String, threshold: Float = BLOCK_THRESHOLD): AnalysisScore {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isBlank()) return AnalysisScore(0.0f, "Empty")

        // 1. Safe domain bypass: Allow main platform and content CDN services
        // UNLESS it's an explicit ad subdomain
        for (safe in safeExceptions) {
            if (cleanDomain == safe || cleanDomain.endsWith(".$safe")) {
                val isExplicitAdSubdomain = cleanDomain.startsWith("ads.") ||
                        cleanDomain.startsWith("ad.") ||
                        cleanDomain.startsWith("pagead") ||
                        cleanDomain.startsWith("adservice.") ||
                        cleanDomain.startsWith("googleads.")
                if (!isExplicitAdSubdomain) {
                    return AnalysisScore(0.0f, "Safe service domain")
                }
            }
        }

        // 2. Check previous learned reputation
        val cachedScore = learnedReputations[cleanDomain]
        if (cachedScore != null && cachedScore >= threshold) {
            return AnalysisScore(cachedScore, "Learned tracker pattern")
        }

        // 3. Known tracker framework direct detection
        val isKnownTracker = knownTrackers.any { cleanDomain == it || cleanDomain.endsWith(".$it") }
        if (isKnownTracker) {
            return AnalysisScore(0.95f, "Known mobile tracker / telemetry framework")
        }

        // 4. Temporal & Cadence Analysis (Burst and Heartbeat Detection)
        val now = System.currentTimeMillis()
        val cadenceScore = evaluateCadence(cleanDomain, now)

        // 5. Lexical Token Analysis
        val lexicalScore = evaluateLexical(cleanDomain)

        // 6. Shannon Entropy Analysis (Detects algorithmic/pseudo-random tracker subdomains)
        val entropyScore = evaluateEntropy(cleanDomain)

        // 7. Structural Multi-Factor Checks: Subdomain Depth & Dynamic DNS
        val subdomainDepth = cleanDomain.count { it == '.' }
        val isDdns = ddnsProviders.any { cleanDomain.endsWith(".$it") }
        var structuralBoost = 0.0f
        if (subdomainDepth >= 4 && (lexicalScore > 0.40f || entropyScore > 0.50f)) {
            structuralBoost += 0.15f
        }
        if (isDdns && (lexicalScore > 0.35f || entropyScore > 0.50f)) {
            structuralBoost += 0.20f
        }

        // Composite Suspicion Score:
        // Lexical token markers are the primary requirement for ad/tracker blocking.
        // Cadence bursts and entropy alone must NEVER block normal websites.
        val baseScore = when {
            lexicalScore >= BLOCK_THRESHOLD -> lexicalScore
            lexicalScore >= 0.50f && (cadenceScore >= 0.50f || entropyScore >= 0.50f) -> 0.75f
            lexicalScore >= 0.40f && cadenceScore >= 0.60f && entropyScore >= 0.60f -> 0.70f
            else -> maxOf(cadenceScore * 0.35f, entropyScore * 0.35f)
        }

        val compositeScore = (baseScore + structuralBoost).coerceAtMost(1.0f)

        // Self-Learning: Update reputation weight in memory and persist newly learned tracker on-device
        if (compositeScore >= threshold) {
            val isNew = !learnedReputations.containsKey(cleanDomain)
            if (learnedReputations.size > MAX_LEARNED_REPUTATIONS) {
                val entriesToEvict = learnedReputations.entries
                    .sortedBy { it.value }
                    .take(100)
                for (entry in entriesToEvict) {
                    learnedReputations.remove(entry.key)
                }
            }
            learnedReputations[cleanDomain] = compositeScore
            if (isNew) {
                persistLearnedDomain(cleanDomain, compositeScore)
            }
        }

        val reason = when {
            isKnownTracker -> "Known mobile tracker / telemetry framework"
            lexicalScore >= threshold -> "Ad/Tracker lexical signature detected"
            compositeScore >= threshold -> "Autonomous multi-factor analysis flagged tracker"
            else -> "Benign traffic"
        }

        return AnalysisScore(compositeScore, reason)
    }

    /**
     * Evaluates query arrival patterns in an in-memory sliding window.
     * Detects high-frequency bursts (typical of ad impression logging)
     * and periodic beacons (typical of background telemetry).
     */
    private fun evaluateCadence(domain: String, timestamp: Long): Float {
        // Prune stale domains if the map exceeds capacity threshold
        if (queryTimestamps.size > MAX_TRACKED_DOMAINS) {
            val iterator = queryTimestamps.entries.iterator()
            var pruned = 0
            while (iterator.hasNext() && pruned < 200) {
                val entry = iterator.next()
                val deque = entry.value
                val isStale = synchronized(deque) {
                    deque.isEmpty() || (timestamp - deque.last() > 180_000L)
                }
                if (isStale) {
                    iterator.remove()
                    pruned++
                }
            }
        }

        val window = queryTimestamps.computeIfAbsent(domain) { ArrayDeque() }

        synchronized(window) {
            window.addLast(timestamp)
            // Keep window to last 15 queries and within 60 seconds
            while (window.isNotEmpty() && timestamp - window.first() > 60_000L) {
                window.removeFirst()
            }
            if (window.size > 15) {
                window.removeFirst()
            }

            if (window.size < 3) return 0.0f

            // Check for rapid burst: >= 4 queries within 600ms
            val recentCount = window.count { timestamp - it < 600L }
            if (recentCount >= 4) {
                return 0.85f
            }

            // Check for periodic heartbeat beacon
            val intervals = mutableListOf<Long>()
            val list = window.toList()
            for (i in 1 until list.size) {
                intervals.add(list[i] - list[i - 1])
            }

            if (intervals.size >= 4) {
                val avg = intervals.average()
                if (avg in 2000.0..30000.0) { // Beacons between 2s and 30s
                    val variance = intervals.map { (it - avg) * (it - avg) }.average()
                    val stdDev = sqrt(variance)
                    // If intervals are highly regular (low std deviation relative to avg)
                    if (stdDev < avg * 0.25) {
                        return 0.75f
                    }
                }
            }

            return (window.size / 15.0f).coerceAtMost(0.4f)
        }
    }

    /**
     * Lexical token detection in domain components.
     */
    fun evaluateLexical(domain: String): Float {
        var score = 0.0f
        val parts = domain.split('.', '-', '_')

        for (part in parts) {
            if (adLexicalTokens.contains(part)) {
                score += 0.70f
            }
        }

        // Subdomain matching common ad prefixes
        if (domain.startsWith("ads.") || domain.startsWith("ad.") || domain.startsWith("pagead.") || domain.startsWith("pubads.")) {
            score += 0.70f
        }

        return score.coerceAtMost(1.0f)
    }

    /**
     * Calculates Shannon entropy of the domain's subdomains to detect
     * dynamically generated machine strings used by tracker CDNs and ad bidding networks.
     */
    fun evaluateEntropy(domain: String): Float {
        val sub = domain.substringBeforeLast('.', "").substringBeforeLast('.', "")
        if (sub.length < 6) return 0.0f

        val charCounts = HashMap<Char, Int>()
        for (c in sub) {
            charCounts[c] = (charCounts[c] ?: 0) + 1
        }

        var entropy = 0.0
        val len = sub.length.toDouble()
        for (count in charCounts.values) {
            val freq = count / len
            entropy -= freq * (ln(freq) / ln(2.0))
        }

        // Normal words have entropy ~2.0 - 2.8. Random strings have entropy > 3.2
        return when {
            entropy > 3.4 -> 0.85f
            entropy > 3.1 -> 0.65f
            entropy > 2.8 -> 0.35f
            else -> 0.0f
        }
    }

    fun isAdOrTracker(domain: String): Boolean {
        val analysis = analyzeQuery(domain)
        return analysis.score >= BLOCK_THRESHOLD
    }

    companion object {
        const val BLOCK_THRESHOLD = 0.65f
        const val MAX_TRACKED_DOMAINS = 2000
        const val MAX_LEARNED_REPUTATIONS = 5000
    }
}

data class AnalysisScore(
    val score: Float,
    val reason: String
)
