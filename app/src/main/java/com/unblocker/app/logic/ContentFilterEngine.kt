package com.unblocker.app.logic

import android.content.Context
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.model.ContentType
import com.unblocker.app.data.model.DetectionMethod
import com.unblocker.app.data.model.FilterResult
import com.unblocker.app.data.preferences.FilteringPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ContentFilterEngine(
    private val context: Context,
    private val adDetector: AdDetector = AdDetector(context),
    private val adultContentDetector: AdultContentDetector = AdultContentDetector(context),
    private val preferences: FilteringPreferences = FilteringPreferences.getInstance(context),
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {

    private val customBlacklist = ConcurrentHashMap<String, String>()
    private val customWhitelist = ConcurrentHashMap<String, String>()

    // Short-lived decision cache for high traffic performance
    private val decisionCache = ConcurrentHashMap<String, FilterResult>()
    private val maxCacheSize = 2000

    init {
        refreshCustomLists()
    }

    fun refreshCustomLists() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val blacklisted = database.blockedDomainDao().getAll()
                customBlacklist.clear()
                for (b in blacklisted) {
                    customBlacklist[b.domain.lowercase()] = b.reason
                }

                val whitelisted = database.whitelistDao().getAll()
                customWhitelist.clear()
                for (w in whitelisted) {
                    customWhitelist[w.domain.lowercase()] = w.notes
                }

                decisionCache.clear()
            } catch (e: Exception) {
                // Ignore if DB not ready
            }
        }
    }

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

        // Check in-memory decision cache
        val cached = decisionCache[domain]
        if (cached != null) {
            // Verify cache matches current toggle states
            val isAdBlocking = preferences.adBlockingEnabled.value
            val isAdultBlocking = preferences.adultBlockingEnabled.value
            if (cached.contentType == ContentType.AD && !isAdBlocking && cached.shouldBlock) {
                // Toggle changed, re-evaluate
            } else if (cached.contentType == ContentType.ADULT_CONTENT && !isAdultBlocking && cached.shouldBlock) {
                // Toggle changed, re-evaluate
            } else {
                return cached
            }
        }

        val result = evaluateDomain(domain)

        if (decisionCache.size < maxCacheSize) {
            decisionCache[domain] = result
        }

        return result
    }

    private fun evaluateDomain(domain: String): FilterResult {
        // 1. Whitelist Check (Highest priority)
        if (isWhitelisted(domain)) {
            return FilterResult(
                domain = domain,
                contentType = ContentType.NORMAL,
                shouldBlock = false,
                reason = "Whitelisted by user",
                detectionMethod = DetectionMethod.WHITELIST,
                confidence = 1.0f
            )
        }

        // 2. Custom Blacklist Check
        val customReason = getCustomBlacklistReason(domain)
        if (customReason != null) {
            return FilterResult(
                domain = domain,
                contentType = ContentType.AD,
                shouldBlock = true,
                reason = "Blocked by custom rule: $customReason",
                detectionMethod = DetectionMethod.CUSTOM_BLACKLIST,
                confidence = 1.0f
            )
        }

        val isAdBlocking = preferences.adBlockingEnabled.value
        val isAdultBlocking = preferences.adultBlockingEnabled.value
        val isParentalControl = preferences.parentalControlEnabled.value

        // 3. Ad Detection
        val (isAd, adReason) = adDetector.isAdDomain(domain)
        if (isAd) {
            val shouldBlock = isAdBlocking
            return FilterResult(
                domain = domain,
                contentType = ContentType.AD,
                shouldBlock = shouldBlock,
                reason = if (shouldBlock) adReason else "Ad detected (blocking disabled in settings)",
                detectionMethod = if (adReason.contains("Pattern")) DetectionMethod.PATTERN_MATCH else DetectionMethod.STATIC_LIST,
                confidence = 0.95f
            )
        }

        // 4. Adult Content Detection
        val (isAdult, adultReason) = adultContentDetector.isAdultContent(domain)
        if (isAdult) {
            val shouldBlock = isAdultBlocking || isParentalControl
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
                shouldBlock = shouldBlock,
                reason = if (shouldBlock) adultReason else "Adult content detected (blocking disabled in settings)",
                detectionMethod = method,
                confidence = 0.92f
            )
        }

        // 5. Allowed / Normal Content
        return FilterResult(
            domain = domain,
            contentType = ContentType.NORMAL,
            shouldBlock = false,
            reason = "Allowed normal traffic",
            detectionMethod = DetectionMethod.NONE,
            confidence = 1.0f
        )
    }

    private fun isWhitelisted(domain: String): Boolean {
        if (customWhitelist.containsKey(domain)) return true
        var parent = domain
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            if (customWhitelist.containsKey(parent)) return true
        }
        return false
    }

    private fun getCustomBlacklistReason(domain: String): String? {
        val direct = customBlacklist[domain]
        if (direct != null) return direct

        var parent = domain
        while (parent.contains('.')) {
            parent = parent.substringAfter('.')
            val reason = customBlacklist[parent]
            if (reason != null) return reason
        }
        return null
    }

    fun clearCache() {
        decisionCache.clear()
    }

    fun getAdDetector(): AdDetector = adDetector
    fun getAdultContentDetector(): AdultContentDetector = adultContentDetector
}
