package com.unblocker.app.ui.navigation

import com.unblocker.app.logic.DomainName
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Type-safe destination specifications for Un-Blocker's Navigation Compose graph (WP-11 / F26).
 */
sealed class AppRoute(
    val pattern: String,
) {
    /** Builds the concrete navigation string passed to `NavController.navigate(...)`. */
    open fun toNavRoute(): String = pattern

    /** Home protection dashboard tab. */
    data object Home : AppRoute("home")

    /** App-isolated traffic learning tab. */
    data object Learn : AppRoute("learn")

    /**
     * Local query log viewer tab with optional initial domain filter.
     *
     * @property domain Normalized domain filter passed via argument or deep link.
     */
    data class Logs(
        val domain: String? = null,
    ) : AppRoute(PATTERN) {
        override fun toNavRoute(): String =
            if (domain.isNullOrBlank()) {
                "logs"
            } else {
                val encoded = URLEncoder.encode(domain, StandardCharsets.UTF_8.name())
                "logs?domain=$encoded"
            }

        companion object {
            const val ARG_DOMAIN: String = "domain"
            const val PATTERN: String = "logs?domain={$ARG_DOMAIN}"
        }
    }

    /**
     * Settings root tab with optional target section slug for direct scrolling/focus.
     *
     * @property section Validated section slug (e.g. `privacy`, `health`, `about`).
     */
    data class Settings(
        val section: String? = null,
    ) : AppRoute(PATTERN) {
        override fun toNavRoute(): String =
            if (section.isNullOrBlank()) {
                "settings"
            } else {
                "settings?section=$section"
            }

        companion object {
            const val ARG_SECTION: String = "section"
            const val PATTERN: String = "settings?section={$ARG_SECTION}"
        }
    }

    /** Filter extensions catalog screen. */
    data object Extensions : AppRoute("extensions")

    /** User exceptions and rule tester destination (expanded in WP-20). */
    data object Exceptions : AppRoute("exceptions")

    /** Local query statistics dashboard destination (expanded in WP-40). */
    data object Statistics : AppRoute("stats")

    /** Guided setup onboarding flow destination (expanded in WP-41). */
    data object Onboarding : AppRoute("onboarding")

    /** Application version, privacy guarantees, and repository info destination. */
    data object About : AppRoute("about")
}

/**
 * Navigation route registry and top-level tab helpers.
 */
object UnblockerDestinations {
    /** Ordered bottom navigation tabs matching [com.unblocker.app.ui.components.FloatingPillNavBar]. */
    val topLevelTabs: List<AppRoute> =
        listOf(
            AppRoute.Home,
            AppRoute.Learn,
            AppRoute.Logs(),
            AppRoute.Settings(),
        )

    /** All registered destination patterns in the app NavGraph. */
    val allPatterns: List<String> =
        listOf(
            AppRoute.Home.pattern,
            AppRoute.Learn.pattern,
            AppRoute.Logs.PATTERN,
            AppRoute.Settings.PATTERN,
            AppRoute.Extensions.pattern,
            AppRoute.Exceptions.pattern,
            AppRoute.Statistics.pattern,
            AppRoute.Onboarding.pattern,
            AppRoute.About.pattern,
        )

    /**
     * Returns whether [routePattern] corresponds to one of the 4 top-level bottom-bar tabs.
     */
    fun isTopLevelPattern(routePattern: String?): Boolean = selectedTabIndex(routePattern) >= 0

    /**
     * Maps a Navigation Compose current destination pattern to a 0-based bottom tab index,
     * or `-1` when on a secondary destination (e.g., Extensions, Exceptions, About).
     */
    fun selectedTabIndex(routePattern: String?): Int {
        if (routePattern == null) return 0
        return when {
            routePattern == AppRoute.Home.pattern -> 0
            routePattern == AppRoute.Learn.pattern -> 1
            routePattern == AppRoute.Logs.PATTERN || routePattern.startsWith("logs") -> 2
            routePattern == AppRoute.Settings.PATTERN || routePattern.startsWith("settings") -> 3
            else -> -1
        }
    }
}

/**
 * Validated outcome of parsing an internal `unblocker://` deep link URI.
 */
sealed interface DeepLinkResult {
    /**
     * Valid deep link mapped to a destination.
     *
     * @property route Target Navigation Compose route.
     * @property autoStartVpn Always `false`: deep links may open the app, never silently start VPN protection.
     */
    data class Matched(
        val route: AppRoute,
        val autoStartVpn: Boolean = false,
    ) : DeepLinkResult

    /**
     * Rejected, malformed, oversized, or disallowed URI.
     *
     * @property reason Diagnostic explanation for testing; do not execute or log sensitive payloads.
     */
    data class Invalid(
        val reason: String,
    ) : DeepLinkResult
}

/**
 * Strictly validates and parses internal `unblocker://` URIs without Android framework dependencies.
 */
object DeepLinkParser {
    /** Maximum permitted raw URI length to guard against oversized Intent payloads. */
    const val MAX_URI_LENGTH: Int = 512

    /** Allowed `unblocker://settings/<section>` section identifiers. */
    val allowedSettingsSections: Set<String> =
        setOf(
            "protection",
            "filters",
            "rules",
            "learning",
            "privacy",
            "automation",
            "appearance",
            "health",
            "about",
        )

    private val sectionPattern = Regex("^[a-z0-9_-]{1,32}$")

    /**
     * Parses [rawUri] and returns either [DeepLinkResult.Matched] or [DeepLinkResult.Invalid].
     *
     * Security invariants:
     * - Only the `unblocker` scheme is accepted.
     * - URIs exceeding [MAX_URI_LENGTH], containing control characters, encoded null bytes (`%00`),
     *   or path-traversal sequences (`..`) are rejected.
     * - `domain` arguments are normalized and verified via [DomainName.normalize].
     * - `unblocker://start` opens [AppRoute.Home] with `autoStartVpn = false`.
     */
    fun parse(rawUri: String?): DeepLinkResult {
        if (rawUri.isNullOrBlank()) {
            return DeepLinkResult.Invalid("Empty or null deep link")
        }
        val trimmed = rawUri.trim()
        if (trimmed.length > MAX_URI_LENGTH) {
            return DeepLinkResult.Invalid("Deep link exceeds maximum allowed length")
        }
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) {
            return DeepLinkResult.Invalid("Deep link contains control characters")
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.contains("%00") || lower.contains("..")) {
            return DeepLinkResult.Invalid("Deep link contains disallowed escape or traversal sequence")
        }

        val uri =
            try {
                URI(trimmed)
            } catch (_: Exception) {
                return DeepLinkResult.Invalid("Malformed URI syntax")
            }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "unblocker") {
            return DeepLinkResult.Invalid("Unsupported URI scheme: $scheme")
        }
        if (!uri.fragment.isNullOrEmpty() || !uri.userInfo.isNullOrEmpty() || uri.port != -1) {
            return DeepLinkResult.Invalid("Unexpected userInfo, port, or fragment in deep link")
        }

        val host =
            uri.host?.lowercase(Locale.ROOT)
                ?: return DeepLinkResult.Invalid("Missing destination host in deep link")
        val path = uri.rawPath.orEmpty().trim('/')

        return when (host) {
            "home" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://home")
                } else {
                    DeepLinkResult.Matched(AppRoute.Home, autoStartVpn = false)
                }
            }

            "start" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://start")
                } else {
                    // Never silently start VPN from a deep link; only open Home.
                    DeepLinkResult.Matched(AppRoute.Home, autoStartVpn = false)
                }
            }

            "learn" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://learn")
                } else {
                    DeepLinkResult.Matched(AppRoute.Learn)
                }
            }

            "logs" -> {
                parseLogsLink(path, uri.rawQuery)
            }

            "settings" -> {
                parseSettingsLink(path, uri.rawQuery)
            }

            "exceptions" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://exceptions")
                } else {
                    DeepLinkResult.Matched(AppRoute.Exceptions)
                }
            }

            "stats" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://stats")
                } else {
                    DeepLinkResult.Matched(AppRoute.Statistics)
                }
            }

            "extensions" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://extensions")
                } else {
                    DeepLinkResult.Matched(AppRoute.Extensions)
                }
            }

            "onboarding" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://onboarding")
                } else {
                    DeepLinkResult.Matched(AppRoute.Onboarding)
                }
            }

            "about" -> {
                if (path.isNotEmpty() || !uri.rawQuery.isNullOrEmpty()) {
                    DeepLinkResult.Invalid("Unexpected path or query for unblocker://about")
                } else {
                    DeepLinkResult.Matched(AppRoute.About)
                }
            }

            else -> {
                DeepLinkResult.Invalid("Unknown deep link destination: $host")
            }
        }
    }

    private fun parseLogsLink(
        path: String,
        rawQuery: String?,
    ): DeepLinkResult {
        if (path.isNotEmpty()) {
            return DeepLinkResult.Invalid("Unexpected path on logs deep link")
        }
        if (rawQuery.isNullOrEmpty()) {
            return DeepLinkResult.Matched(AppRoute.Logs(domain = null))
        }
        val params =
            rawQuery.split('&').mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return DeepLinkResult.Invalid("Malformed query parameter")
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                key to value
            }
        if (params.size != 1 || params[0].first != "domain") {
            return DeepLinkResult.Invalid("Unsupported query parameters on logs deep link")
        }
        val rawDomain = params[0].second
        if (rawDomain.length > 253) {
            return DeepLinkResult.Invalid("Domain parameter too long")
        }
        val normalized =
            DomainName.normalize(rawDomain)
                ?: return DeepLinkResult.Invalid("Invalid domain parameter")
        return DeepLinkResult.Matched(AppRoute.Logs(domain = normalized))
    }

    private fun parseSettingsLink(
        path: String,
        rawQuery: String?,
    ): DeepLinkResult {
        if (!rawQuery.isNullOrEmpty()) {
            return DeepLinkResult.Invalid("Unexpected query parameters on settings deep link")
        }
        if (path.isEmpty()) {
            return DeepLinkResult.Matched(AppRoute.Settings(section = null))
        }
        if (path.contains('/')) {
            return DeepLinkResult.Invalid("Nested path segments are not permitted in settings deep links")
        }
        val slug = path.lowercase(Locale.ROOT)
        if (!sectionPattern.matches(slug) || slug !in allowedSettingsSections) {
            return DeepLinkResult.Invalid("Unknown settings section: $slug")
        }
        return DeepLinkResult.Matched(AppRoute.Settings(section = slug))
    }
}
