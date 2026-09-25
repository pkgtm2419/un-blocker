package com.unblocker.app.logic.analysis

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device autonomous network analysis and self-learning tracking system.
 * Operates 100% locally using system resources without cloud dependencies.
 * Zero user logs or history are created or stored.
 */
class LocalNetworkLearner {

    // In-memory learned reputation cache: domain -> confidence score (0.0 to 1.0)
    private val learnedReputations = ConcurrentHashMap<String, Float>()

    // Temporal cadence tracking: domain -> recent query timestamps (sliding window)
    private val queryTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()

    private val adLexicalTokens = setOf(
        "ad", "ads", "track", "tracker", "tracking", "telemetry", "pixel",
        "beacon", "analytics", "metrics", "dsp", "ssp", "banner", "pop",
        "monetiz", "monetization", "affiliate", "sponsor", "adserver",
        "adsystem", "bidder", "rtb", "stats", "tagmanager", "syndication",
        "doubleclick", "admob", "applovin", "unityads", "vungle", "inmobi", "criteo", "taboola"
    )

    private val safeExceptions = setOf(
        "google.com", "android.com", "github.com", "wikipedia.org",
        "stackoverflow.com", "microsoft.com", "apple.com", "cloudflare.com"
    )

    /**
     * Analyze a network domain query using local behavioral, lexical, and temporal heuristics.
     * Returns a composite suspicion score between 0.0 and 1.0.
     */
    fun analyzeQuery(domain: String): AnalysisScore {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isBlank()) return AnalysisScore(0.0f, "Empty")

        // 1. Safe domain bypass
        for (safe in safeExceptions) {
            if (cleanDomain == safe || cleanDomain.endsWith(".$safe") && !cleanDomain.contains("ad")) {
                // If it's a known benign service without explicit ad tokens, preserve it
                if (!cleanDomain.startsWith("ads.") && !cleanDomain.startsWith("pagead")) {
                    return AnalysisScore(0.0f, "Safe domain")
                }
            }
        }

        // 2. Check previous learned reputation
        val cachedScore = learnedReputations[cleanDomain]
        if (cachedScore != null && cachedScore >= BLOCK_THRESHOLD) {
            return AnalysisScore(cachedScore, "Learned tracker pattern")
        }

        // 3. Temporal & Cadence Analysis (Burst and Heartbeat Detection)
        val now = System.currentTimeMillis()
        val cadenceScore = evaluateCadence(cleanDomain, now)

        // 4. Lexical Token Analysis
        val lexicalScore = evaluateLexical(cleanDomain)

        // 5. Shannon Entropy Analysis (Detects algorithmic/pseudo-random tracker subdomains)
        val entropyScore = evaluateEntropy(cleanDomain)

        // Composite Suspicion Score: evaluate primary suspicion signal and cross-signal reinforcement
        val primarySignal = maxOf(lexicalScore, cadenceScore, entropyScore)
        val multiSignalBoost = if ((lexicalScore >= 0.5f && cadenceScore >= 0.5f) ||
            (lexicalScore >= 0.5f && entropyScore >= 0.5f) ||
            (cadenceScore >= 0.5f && entropyScore >= 0.5f)) 0.15f else 0.0f

        val compositeScore = (primarySignal + multiSignalBoost).coerceAtMost(1.0f)

        // Self-Learning: Update reputation weight in memory
        if (compositeScore >= BLOCK_THRESHOLD) {
            learnedReputations[cleanDomain] = compositeScore

            // Propagate suspicion to parent domain if applicable
            if (cleanDomain.count { it == '.' } >= 2) {
                val parent = cleanDomain.substringAfter('.')
                val currentParent = learnedReputations[parent] ?: 0f
                learnedReputations[parent] = (currentParent + 0.35f).coerceAtMost(1.0f)
            }
        }

        val reason = when {
            lexicalScore >= BLOCK_THRESHOLD -> "Ad/Tracker lexical signature detected"
            cadenceScore >= BLOCK_THRESHOLD -> "High-frequency tracking burst/cadence detected"
            entropyScore >= BLOCK_THRESHOLD -> "Algorithmic tracker subdomain entropy detected"
            compositeScore >= BLOCK_THRESHOLD -> "Autonomous network analysis flagged tracker"
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
    }
}

data class AnalysisScore(
    val score: Float,
    val reason: String
)
