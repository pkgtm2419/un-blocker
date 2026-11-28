package com.unblocker.app.ui.learn

import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.services.SessionDomain
import java.util.Locale

/**
 * Clean, deterministic domain categorization, whole-site safety guards, and review rule application
 * policies for the Learn-an-App review flow (WP-03).
 */
object LearnReviewPolicy {
    private val ignoredPackageSegments: Set<String> =
        setOf(
            "com",
            "org",
            "net",
            "edu",
            "gov",
            "app",
            "android",
            "mobile",
            "client",
            "free",
            "pro",
            "lite",
            "official",
        )

    private val cdnSuffixes: Set<String> =
        setOf(
            "cloudfront.net",
            "akamaihd.net",
            "akamaiedge.net",
            "edgekey.net",
            "fastly.net",
            "cloudflare.com",
            "googleapis.com",
            "gstatic.com",
            "amazonaws.com",
            "1e100.net",
            "azureedge.net",
            "trafficmanager.net",
            "edgesuite.net",
            "akadns.net",
            "cdn77.org",
            "jsdelivr.net",
            "unpkg.com",
        )

    private val sharedPlatformSuffixes: Set<String> =
        setOf(
            "amazonaws.com",
            "s3.amazonaws.com",
            "cloudfront.net",
            "googleapis.com",
            "gstatic.com",
            "github.io",
            "gitlab.io",
            "herokuapp.com",
            "appspot.com",
            "azureedge.net",
            "azurewebsites.net",
            "blogspot.com",
            "wordpress.com",
            "firebaseapp.com",
            "web.app",
            "workers.dev",
            "pages.dev",
            "netlify.app",
            "vercel.app",
            "cloudflare.net",
            "fastly.net",
            "akamaihd.net",
            "akamaiedge.net",
            "1e100.net",
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "2mdn.net",
            "adnxs.com",
        )

    private val trackerLabels: Set<String> =
        setOf(
            "telemetry",
            "analytics",
            "crashlytics",
            "tracker",
            "trackers",
            "tracking",
            "metrics",
            "ads",
            "ad",
            "adservice",
            "adserver",
            "adsystem",
            "adtech",
            "advertising",
            "pixel",
            "beacon",
            "doubleclick",
            "applovin",
            "unityads",
            "vungle",
            "ironsrc",
            "ironsource",
            "inmobi",
            "mintegral",
            "chartboost",
            "mopub",
            "tapjoy",
            "fyber",
            "criteo",
            "taboola",
            "outbrain",
            "adnxs",
            "adjust",
            "appsflyer",
            "appsflyersdk",
            "moloco",
            "branch",
            "kochava",
            "flurry",
            "mixpanel",
            "hotjar",
            "clarity",
        )

    /**
     * Explicit shipped ad/tracker seed domains guarded against unconfirmed review ALLOW overrides.
     */
    val shippedAdSeeds: Set<String> =
        (
            AdDetector.defaultSeedRules +
                listOf(
                    "doubleclick.net",
                    "googlesyndication.com",
                    "googleadservices.com",
                    "2mdn.net",
                    "adnxs.com",
                    "fundingchoicesmessages.google.com",
                    "ogads-pa.clients6.google.com",
                    "imasdk.googleapis.com",
                    "applovin.com",
                    "adjust.com",
                    "mixpanel.com",
                    "segment.io",
                    "amplitude.com",
                    "hotjar.com",
                    "pubmatic.com",
                    "rubiconproject.com",
                    "casalemedia.com",
                    "scorecardresearch.com",
                )
        ).mapNotNull { DomainName.normalize(it) }.toSet()

    /**
     * Classifies a domain using whole DNS labels, public-suffix registrable boundaries,
     * and optional blocklist matching — never raw substring containment.
     *
     * @param domain Hostname observed during recording.
     * @param targetPackage Android package name of the learned app.
     * @param publicSuffixRules Loaded public suffix rules for registrable domain boundary calculation.
     * @param isBlockedByRules Optional callback reporting whether the host matches active block rules.
     * @return Display classification for review.
     */
    fun categorizeDomain(
        domain: String,
        targetPackage: String,
        publicSuffixRules: PublicSuffixRules = PublicSuffixRules.fallback,
        isBlockedByRules: (String) -> Boolean = { false },
    ): DomainCategory {
        val normalized = DomainName.normalize(domain) ?: return DomainCategory.OTHER
        val labels = normalized.split('.')
        val registrable = publicSuffixRules.registrableDomain(normalized) ?: normalized

        // 1. Check active block rules, shipped ad seeds, or whole tracker DNS labels
        if (isBlockedByRules(normalized) ||
            isMatchesAdSeed(normalized, registrable) ||
            labels.any { it in trackerLabels }
        ) {
            return DomainCategory.TRACKER_AD
        }

        // 2. Check known CDN / Infrastructure by domain suffix (exact or subdomain)
        if (cdnSuffixes.any { suffix -> normalized == suffix || normalized.endsWith(".$suffix") }) {
            return DomainCategory.CDN_INFRA
        }

        // 3. Check First Party from package name segments using whole DNS label equality
        val packageSegments =
            targetPackage
                .lowercase(Locale.ROOT)
                .split('.')
                .filter { segment ->
                    segment.length > 2 && segment !in ignoredPackageSegments
                }.toSet()
        if (packageSegments.isNotEmpty() && labels.any { it in packageSegments }) {
            return DomainCategory.FIRST_PARTY
        }

        return DomainCategory.OTHER
    }

    /**
     * Checks whether allowing "whole site" (registrable domain wildcard) for [domain] is safe.
     * Shared multi-tenant hosting, CDN, and ad-network platforms disallow whole-site promotion.
     *
     * @param domain Target hostname to check.
     * @param publicSuffixRules Public suffix helper for registrable boundary checks.
     * @return True if whole-site toggle may be enabled; false for shared platforms or invalid/TLD-like hosts.
     */
    fun canAllowWholeSite(
        domain: String,
        publicSuffixRules: PublicSuffixRules = PublicSuffixRules.fallback,
    ): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val registrable = publicSuffixRules.registrableDomain(normalized) ?: return false
        if (sharedPlatformSuffixes.any { suffix ->
                normalized == suffix || normalized.endsWith(".$suffix") || registrable == suffix
            }
        ) {
            return false
        }
        return true
    }

    /**
     * Checks whether [domain] or its registrable site matches an active block rule or shipped ad seed.
     *
     * @param domain Hostname requested for ALLOW.
     * @param publicSuffixRules Public suffix helper for registrable boundary checks.
     * @param isBlockedByRules Optional active blocklist matcher callback.
     * @return Warning explanation if confirmation is required, or null when safe.
     */
    fun adAllowRiskWarning(
        domain: String,
        publicSuffixRules: PublicSuffixRules = PublicSuffixRules.fallback,
        isBlockedByRules: (String) -> Boolean = { false },
    ): String? {
        val normalized = DomainName.normalize(domain) ?: return null
        val registrable = publicSuffixRules.registrableDomain(normalized) ?: normalized

        val matchesActiveRule = isBlockedByRules(normalized) || isBlockedByRules(registrable)
        val matchesSeed = isMatchesAdSeed(normalized, registrable)
        if (!matchesActiveRule && !matchesSeed) return null

        return "Warning: $normalized matches known ad/tracking block rules or seeds. " +
            "Allowing it will permit ad requests across apps. Tap Allow again to confirm."
    }

    /**
     * Converts recorded session domains into initial review rows where nothing is pre-selected.
     *
     * @param recorded Session domain aggregates.
     * @param targetPackage Learned app package identifier.
     * @param publicSuffixRules Public suffix rules instance.
     * @param isBlockedByRules Active blocklist lookup.
     * @param alsoSeenInLookup Lookup returning other app labels/packages where the domain was seen.
     * @return Review items initialized cleanly to [ReviewAction.KEEP_DEFAULT] and exact scope.
     */
    fun buildInitialReviewList(
        recorded: List<SessionDomain>,
        targetPackage: String,
        publicSuffixRules: PublicSuffixRules = PublicSuffixRules.fallback,
        isBlockedByRules: (String) -> Boolean = { false },
        alsoSeenInLookup: (String) -> List<String> = { emptyList() },
    ): List<ReviewedDomain> =
        recorded.map { domainItem ->
            val category =
                categorizeDomain(
                    domain = domainItem.domain,
                    targetPackage = targetPackage,
                    publicSuffixRules = publicSuffixRules,
                    isBlockedByRules = isBlockedByRules,
                )
            ReviewedDomain(
                domain = domainItem.domain,
                count = domainItem.count,
                category = category,
                action = ReviewAction.KEEP_DEFAULT,
                allowWholeSite = false,
                canAllowWholeSite = canAllowWholeSite(domainItem.domain, publicSuffixRules),
                alsoSeenIn = alsoSeenInLookup(domainItem.domain),
            )
        }

    /**
     * Submits reviewed domain actions to [userRuleGateway] safely.
     *
     * - [ReviewAction.ALLOW] defaults to exact-host [UserRuleGateway.set], using
     *   [UserRuleGateway.setWholeSite] ONLY when both `allowWholeSite` and `canAllowWholeSite` are true.
     * - Ad-guarded domains whose `confirmedAdAllow` is false are refused and skipped.
     * - [ReviewAction.BLOCK] submits an exact-host block rule only when explicitly selected.
     * - [ReviewAction.KEEP_DEFAULT] leaves policy untouched.
     *
     * @param domains Reviewed domain states.
     * @param userRuleGateway Gateway persisting user overrides.
     * @param publicSuffixRules Public suffix helper for verification.
     * @param isBlockedByRules Active blocklist lookup for final ad-allow refusal guard.
     * @return Number of rules applied and any domains refused due to unconfirmed ad risk.
     */
    suspend fun applyReviewedRules(
        domains: List<ReviewedDomain>,
        userRuleGateway: UserRuleGateway,
        publicSuffixRules: PublicSuffixRules = PublicSuffixRules.fallback,
        isBlockedByRules: (String) -> Boolean = { false },
    ): ApplyReviewOutcome {
        var appliedCount = 0
        val refusedAdDomains = mutableListOf<String>()

        for (reviewed in domains) {
            when (reviewed.action) {
                ReviewAction.ALLOW -> {
                    val riskWarning =
                        adAllowRiskWarning(
                            domain = reviewed.domain,
                            publicSuffixRules = publicSuffixRules,
                            isBlockedByRules = isBlockedByRules,
                        )
                    if (riskWarning != null && !reviewed.confirmedAdAllow) {
                        refusedAdDomains.add(reviewed.domain)
                        continue
                    }
                    if (reviewed.allowWholeSite && reviewed.canAllowWholeSite) {
                        userRuleGateway.setWholeSite(reviewed.domain, UserFeedback.ALLOW)
                    } else {
                        userRuleGateway.set(reviewed.domain, UserFeedback.ALLOW)
                    }
                    appliedCount++
                }

                ReviewAction.BLOCK -> {
                    userRuleGateway.set(reviewed.domain, UserFeedback.BLOCK)
                    appliedCount++
                }

                ReviewAction.KEEP_DEFAULT -> {
                    // Explicit user approval required; untouched by default
                }
            }
        }
        return ApplyReviewOutcome(appliedCount = appliedCount, refusedAdDomains = refusedAdDomains)
    }

    private fun isMatchesAdSeed(
        normalized: String,
        registrable: String,
    ): Boolean =
        shippedAdSeeds.any { seed ->
            normalized == seed || normalized.endsWith(".$seed") || registrable == seed
        }
}

/**
 * Summary of applying reviewed Learn-an-App rules.
 *
 * @property appliedCount Total number of rules submitted to the gateway.
 * @property refusedAdDomains Domains skipped because an ad-risk ALLOW lacked explicit second confirmation.
 */
data class ApplyReviewOutcome(
    val appliedCount: Int,
    val refusedAdDomains: List<String>,
)
