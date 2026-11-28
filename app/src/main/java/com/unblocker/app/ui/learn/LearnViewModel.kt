package com.unblocker.app.ui.learn

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.services.RecordingSessionManager
import com.unblocker.app.services.SessionDomain
import com.unblocker.app.services.UnblockerVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Launcher application available for a local DNS recording session.
 *
 * @property packageName Android package identifier used for lookup and launch.
 * @property label Display label from the package manager.
 * @property icon Optional launcher drawable; discovery currently leaves it unset.
 */
data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)

/**
 * Heuristic review grouping inferred from a host name and selected package name.
 */
enum class DomainCategory {
    FIRST_PARTY,
    CDN_INFRA,
    TRACKER_AD,
    OTHER,
}

/**
 * User-selected override to apply after a DNS recording session.
 */
enum class ReviewAction {
    ALLOW,
    BLOCK,
    KEEP_DEFAULT,
}

/**
 * Observed host and proposed rule action for human review.
 *
 * @property domain Readable host recorded during the session.
 * @property count Number of observations reported by the session.
 * @property category Heuristic display grouping, not a verified ownership classification.
 * @property action Requested rule action; keeping the default leaves existing policy untouched.
 */
data class ReviewedDomain(
    val domain: String,
    val count: Int,
    val category: DomainCategory,
    val action: ReviewAction = ReviewAction.KEEP_DEFAULT,
)

/**
 * Screen states for app selection, transient DNS recording, and human rule review.
 */
sealed interface LearnUiState {
    /**
     * App discovery and search state.
     *
     * @property installedApps Complete discovered launcher-app list.
     * @property filteredApps Apps matching the current search.
     * @property query Current app-label or package-name search text.
     * @property isLoading Whether launcher app discovery is in progress.
     */
    data class SelectingApp(
        val installedApps: List<AppItem>,
        val filteredApps: List<AppItem>,
        val query: String = "",
        val isLoading: Boolean = false,
    ) : LearnUiState

    /**
     * Active local recording presentation.
     *
     * @property app Application selected for the session.
     * @property elapsedSeconds Number of one-second timer updates since recording started.
     * @property recordedDomains Current readable domain observations reported by the session.
     */
    data class Recording(
        val app: AppItem,
        val elapsedSeconds: Int,
        val recordedDomains: List<SessionDomain>,
    ) : LearnUiState

    /**
     * Recorded domains and proposed override actions awaiting review.
     *
     * @property app Application associated with the captured observations.
     * @property domains Recorded hosts and their current proposed actions.
     * @property appliedSuccess Whether rule application finished iterating; individual results are not checked here.
     */
    data class Reviewing(
        val app: AppItem,
        val domains: List<ReviewedDomain>,
        val appliedSuccess: Boolean = false,
    ) : LearnUiState
}

/**
 * Owns launcher-app discovery, a local recording session, and the resulting rule-review state.
 *
 * @param application Application providing package metadata.
 * @param userRuleGateway Destination for reviewed exact and whole-site overrides.
 */
@HiltViewModel
class LearnViewModel
    @Inject
    constructor(
        application: Application,
        private val userRuleGateway: UserRuleGateway,
    ) : AndroidViewModel(application) {
        private val sessionManager = RecordingSessionManager.instance
        private val _uiState =
            MutableStateFlow<LearnUiState>(
                LearnUiState.SelectingApp(emptyList(), emptyList(), isLoading = true),
            )

        /**
         * Current app-selection, recording, or review state exposed as a read-only flow.
         */
        val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

        private var timerJob: Job? = null
        private var allApps: List<AppItem> = emptyList()

        init {
            loadInstalledLauncherApps()
        }

        /**
         * Asynchronously discovers launcher apps, excluding this app, and publishes a sorted selection list.
         */
        fun loadInstalledLauncherApps() {
            viewModelScope.launch(Dispatchers.IO) {
                val packageManager = getApplication<Application>().packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.queryIntentActivities(intent, 0)
                    }

                val appList =
                    resolveInfos
                        .mapNotNull { resolveInfo ->
                            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                            // Don't include self
                            if (packageName == getApplication<Application>().packageName) return@mapNotNull null
                            val label = resolveInfo.loadLabel(packageManager).toString()
                            AppItem(packageName = packageName, label = label)
                        }.distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase(Locale.ROOT) }

                allApps = appList
                _uiState.value =
                    LearnUiState.SelectingApp(
                        installedApps = appList,
                        filteredApps = appList,
                        isLoading = false,
                    )
            }
        }

        /**
         * Publishes app selection filtered by case-insensitive label or package-name matching.
         *
         * @param query Search text; blank input shows all discovered apps.
         */
        fun searchApps(query: String) {
            val filtered =
                if (query.isBlank()) {
                    allApps
                } else {
                    allApps.filter {
                        it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                    }
                }
            _uiState.value = LearnUiState.SelectingApp(allApps, filtered, query, isLoading = false)
        }

        /**
         * Starts a session for the selected package and updates its presentation once per second.
         *
         * @param app Selected launcher app; package lookup supplies the target UID or minus one on failure.
         */
        fun startRecording(app: AppItem) {
            val context = getApplication<Application>()
            val packageManager = context.packageManager
            val uid =
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager
                            .getApplicationInfo(
                                app.packageName,
                                PackageManager.ApplicationInfoFlags.of(0),
                            ).uid
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(app.packageName, 0).uid
                    }
                }.getOrDefault(-1)

            val prefs = FilteringPreferences.getInstance(context)
            prefs.setIsolatedSession(true, app.packageName)
            sessionManager.startSession(app.packageName, uid)
            if (UnblockerVpnService.isServiceActive.value) {
                UnblockerVpnService.rebuild(context)
            } else {
                runCatching { UnblockerVpnService.start(context) }
            }
            _uiState.value = LearnUiState.Recording(app, 0, emptyList())

            timerJob?.cancel()
            timerJob =
                viewModelScope.launch {
                    var elapsed = 0
                    while (sessionManager.isRecordingActive()) {
                        delay(1000L)
                        elapsed++
                        val domains = sessionManager.getRecordedDomains()
                        val current = _uiState.value
                        if (current is LearnUiState.Recording) {
                            _uiState.value = current.copy(elapsedSeconds = elapsed, recordedDomains = domains)
                        }
                    }
                    stopRecording()
                }
        }

        /**
         * Stops the current session and builds heuristic review actions for its recorded hosts.
         */
        fun stopRecording() {
            timerJob?.cancel()
            val context = getApplication<Application>()
            val prefs = FilteringPreferences.getInstance(context)
            prefs.setIsolatedSession(false, null)
            val current = _uiState.value
            val app =
                when (current) {
                    is LearnUiState.Recording -> current.app
                    is LearnUiState.Reviewing -> current.app
                    else -> return
                }

            val recorded = sessionManager.stopSession()
            if (prefs.protectionEnabled.value) {
                UnblockerVpnService.rebuild(context)
            } else {
                UnblockerVpnService.stop(context)
            }
            val reviewed =
                recorded.map { domainItem ->
                    val category = categorizeDomain(domainItem.domain, app.packageName)
                    val defaultAction =
                        when (category) {
                            DomainCategory.FIRST_PARTY -> ReviewAction.ALLOW
                            DomainCategory.CDN_INFRA -> ReviewAction.ALLOW
                            DomainCategory.TRACKER_AD -> ReviewAction.BLOCK
                            DomainCategory.OTHER -> ReviewAction.KEEP_DEFAULT
                        }
                    ReviewedDomain(
                        domain = domainItem.domain,
                        count = domainItem.count,
                        category = category,
                        action = defaultAction,
                    )
                }

            _uiState.value = LearnUiState.Reviewing(app, reviewed)
        }

        /**
         * Changes the proposed action for a host while in the review state.
         *
         * @param domain Recorded host whose proposed action should change.
         * @param action Action to apply when review is submitted.
         */
        fun setDomainAction(
            domain: String,
            action: ReviewAction,
        ) {
            val current = _uiState.value
            if (current is LearnUiState.Reviewing) {
                val updated =
                    current.domains.map {
                        if (it.domain == domain) it.copy(action = action) else it
                    }
                _uiState.value = current.copy(domains = updated)
            }
        }

        /**
         * Asynchronously submits reviewed allow actions as whole-site rules and blocks as exact rules.
         *
         * Keep-default entries are skipped. The completion flag is set after submission without checking
         * individual gateway results.
         */
        fun applyRules() {
            val current = _uiState.value
            if (current is LearnUiState.Reviewing) {
                viewModelScope.launch {
                    current.domains.forEach { reviewed ->
                        when (reviewed.action) {
                            ReviewAction.ALLOW -> {
                                userRuleGateway.setWholeSite(reviewed.domain, UserFeedback.ALLOW)
                            }

                            ReviewAction.BLOCK -> {
                                userRuleGateway.set(reviewed.domain, UserFeedback.BLOCK)
                            }

                            ReviewAction.KEEP_DEFAULT -> {
                                // No custom override applied
                            }
                        }
                    }
                    _uiState.value = current.copy(appliedSuccess = true)
                }
            }
        }

        /**
         * Stops recording, cancels timer updates, and returns to the complete app-selection list.
         */
        fun cancelSession() {
            val context = getApplication<Application>()
            val prefs = FilteringPreferences.getInstance(context)
            prefs.setIsolatedSession(false, null)
            sessionManager.stopSession()
            if (prefs.protectionEnabled.value) {
                UnblockerVpnService.rebuild(context)
            } else {
                UnblockerVpnService.stop(context)
            }
            timerJob?.cancel()
            _uiState.value = LearnUiState.SelectingApp(allApps, allApps)
        }

        override fun onCleared() {
            super.onCleared()
            val context = getApplication<Application>()
            val prefs = FilteringPreferences.getInstance(context)
            if (prefs.isolatedSessionActive.value) {
                prefs.setIsolatedSession(false, null)
                sessionManager.stopSession()
                if (prefs.protectionEnabled.value) {
                    UnblockerVpnService.rebuild(context)
                } else {
                    UnblockerVpnService.stop(context)
                }
            }
        }

        private fun categorizeDomain(
            domain: String,
            targetPackage: String,
        ): DomainCategory {
            val lower = domain.lowercase(Locale.ROOT)
            // 1. Check known CDN / Infrastructure
            val cdnSuffixes =
                listOf(
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
                )
            if (cdnSuffixes.any { lower.endsWith(it) }) {
                return DomainCategory.CDN_INFRA
            }

            // 2. Check First Party from package name segments
            val packageSegments =
                targetPackage.split('.').filter {
                    it.length > 2 &&
                        it !in setOf("com", "org", "net", "app", "android")
                }
            if (packageSegments.any { lower.contains(it) }) {
                return DomainCategory.FIRST_PARTY
            }

            // 3. Check known tracking / telemetry patterns
            val trackerPatterns =
                listOf(
                    "telemetry",
                    "analytics",
                    "crashlytics",
                    "track",
                    "metrics",
                    "ads",
                    "doubleclick",
                    "applovin",
                    "unityads",
                    "vungle",
                )
            if (trackerPatterns.any { lower.contains(it) }) {
                return DomainCategory.TRACKER_AD
            }

            return DomainCategory.OTHER
        }
    }
