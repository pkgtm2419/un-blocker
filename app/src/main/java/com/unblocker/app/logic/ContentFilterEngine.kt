package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.data.model.ContentType
import com.unblocker.app.data.model.DetectionMethod
import com.unblocker.app.data.model.FilterResult
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import java.util.concurrent.ConcurrentHashMap

/**
 * Autonomous local content filter engine.
 * Combines on-device self-learning network analysis, seed heuristics, and adult content classifier.
 * Operates 100% on-device with zero logs, zero history, and zero cloud dependency.
 */
class ContentFilterEngine(
    private val context: Context,
    private val adDetector: AdDetector = AdDetector(context),
    private val adultContentDetector: AdultContentDetector = AdultContentDetector(context),
    private val networkLearner: LocalNetworkLearner = LocalNetworkLearner(),
    private val preferences: FilteringPreferences = FilteringPreferences.getInstance(context)
) {

    // Ephemeral in-memory evaluation cache for instant sub-millisecond response
    private val decisionCache = ConcurrentHashMap<String, FilterResult>()
    private val maxCacheSize = 3000

    fun analyzeAndFilter(rawDomain: String): FilterResult {
        val domain = rawDomain.trim().lowercase()
        if (domain.isBlank()) {
            return FilterResult(
                domain = domain,
                contentType = ContentType.NORMAL,
                shouldBlock = false,
                reason = "Empty domain",
                detectionMethod = DetectionMethod.NONE,
                confidence = 1.0f
            )
        }

        val isAdBlocking = preferences.adBlockingEnabled.value
        val isAdultBlocking = preferences.adultBlockingEnabled.value

        // Check in-memory decision cache
        val cached = decisionCache[domain]
        if (cached != null) {
            // Verify cached result matches current toggle states
            val matchesAdToggle = cached.contentType != ContentType.AD || cached.shouldBlock == isAdBlocking
            val matchesAdultToggle = cached.contentType != ContentType.ADULT_CONTENT || cached.shouldBlock == isAdultBlocking
            if (matchesAdToggle && matchesAdultToggle) {
                return cached
            }
        }

        val result = evaluateDomain(domain, isAdBlocking, isAdultBlocking)

        if (decisionCache.size < maxCacheSize) {
            decisionCache[domain] = result
        }

        return result
    }

    private fun evaluateDomain(
        domain: String,
        isAdBlocking: Boolean,
        isAdultBlocking: Boolean
    ): FilterResult {
        // 1. Check Ad / Tracker Detection (Seed lists + On-device self-learning network analysis)
        if (isAdBlocking) {
            // Check static seed heuristics
            val (isAdSeed, seedReason) = adDetector.isAdDomain(domain)
            if (isAdSeed) {
                return FilterResult(
                    domain = domain,
                    contentType = ContentType.AD,
                    shouldBlock = true,
                    reason = seedReason,
                    detectionMethod = DetectionMethod.STATIC_LIST,
                    confidence = 0.95f
                )
            }

            // Check On-device Self-learning Network Analysis (Cadence, Burst, Entropy)
            val score = networkLearner.analyzeQuery(domain)
            if (score.score >= LocalNetworkLearner.BLOCK_THRESHOLD) {
                return FilterResult(
                    domain = domain,
                    contentType = ContentType.AD,
                    shouldBlock = true,
                    reason = score.reason,
                    detectionMethod = DetectionMethod.PATTERN_MATCH,
                    confidence = score.score
                )
            }
        }

        // 2. Check 18+ Adult Content Detection (Option 2)
        if (isAdultBlocking) {
            val (isAdult, adultReason) = adultContentDetector.isAdultContent(domain)
            if (isAdult) {
                val method = if (adultReason.contains("TLD")) {
                    DetectionMethod.TLD_RULE
                } else if (adultReason.contains("Pattern")) {
                    DetectionMethod.PATTERN_MATCH
                } else {
                    DetectionMethod.STATIC_LIST
                }

                return FilterResult(
                    domain = domain,
                    contentType = ContentType.ADULT_CONTENT,
                    shouldBlock = true,
                    reason = adultReason,
                    detectionMethod = method,
                    confidence = 0.92f
                )
            }
        }

        // 3. Normal Allowed Traffic
        return FilterResult(
            domain = domain,
            contentType = ContentType.NORMAL,
            shouldBlock = false,
            reason = "Allowed normal traffic",
            detectionMethod = DetectionMethod.NONE,
            confidence = 1.0f
        )
    }

    fun clearCache() {
        decisionCache.clear()
    }

    fun getAdDetector(): AdDetector = adDetector
    fun getAdultContentDetector(): AdultContentDetector = adultContentDetector
    fun getNetworkLearner(): LocalNetworkLearner = networkLearner
}
