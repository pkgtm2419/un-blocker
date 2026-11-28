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
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.analysis.PublicSuffixRules
import com.unblocker.app.logic.rules.RuleAction
import com.unblocker.app.logic.rules.RuleSetHolder
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
 * @property action Requested rule action; defaults to [ReviewAction.KEEP_DEFAULT].
 * @property allowWholeSite Whether an ALLOW applies to the whole registrable site (explicit toggle, off by default).
 * @property canAllowWholeSite False for shared multi-tenant/CDN/ad platforms where broad allows are unsafe.
 * @property pendingAllowConfirmation True when first ALLOW tap on an ad-guarded domain triggered a warning.
 * @property confirmedAdAllow True after a second explicit tap confirms allowing a flagged ad domain.
 * @property allowWarningMessage Explanation shown when ALLOW is blocked pending second explicit confirmation.
 * @property alsoSeenIn Other app display labels/packages that observed this domain in prior Learn sessions.
 */
data class ReviewedDomain(
    val domain: String,
    val count: Int,
    val category: DomainCategory,
    val action: ReviewAction = ReviewAction.KEEP_DEFAULT,
    val allowWholeSite: Boolean = false,
    val canAllowWholeSite: Boolean = true,
    val pendingAllowConfirmation: Boolean = false,
    val confirmedAdAllow: Boolean = false,
    val allowWarningMessage: String? = null,
    val alsoSeenIn: List<String> = emptyList(),
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
        private val publicSuffixRules: PublicSuffixRules by lazy {
            PublicSuffixRules.from(getApplication())
        }
        private val adDetector: AdDetector by lazy {
            val ctx = getApplication<Application>()
            AdDetector(ctx, ruleSet = RuleSetHolder.get(ctx))
        }
        private val adBlockMatcher: (String) -> Boolean = { domain ->
            adDetector.matchRule(domain)?.action == RuleAction.BLOCK
        }

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
            val labelByPkg = allApps.associate { it.packageName to it.label }
            val reviewed =
                LearnReviewPolicy.buildInitialReviewList(
                    recorded = recorded,
                    targetPackage = app.packageName,
                    publicSuffixRules = publicSuffixRules,
                    isBlockedByRules = adBlockMatcher,
                    alsoSeenInLookup = { host ->
                        sessionManager
                            .getOtherAppsSeeingDomain(host, app.packageName)
                            .map { pkg -> labelByPkg[pkg] ?: pkg }
                    },
                )

            _uiState.value = LearnUiState.Reviewing(app, reviewed)
        }

        /**
         * Changes the proposed action for a host while in the review state.
         *
         * Allowing a host that matches active block rules or shipped ad seeds requires
         * two explicit taps: the first tap shows a refusal warning without enabling ALLOW,
         * and the second tap confirms it.
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
                    current.domains.map { item ->
                        if (item.domain != domain) {
                            item
                        } else if (action == ReviewAction.ALLOW) {
                            val warning =
                                LearnReviewPolicy.adAllowRiskWarning(
                                    domain = item.domain,
                                    publicSuffixRules = publicSuffixRules,
                                    isBlockedByRules = adBlockMatcher,
                                )
                            if (warning != null && !item.pendingAllowConfirmation) {
                                item.copy(
                                    pendingAllowConfirmation = true,
                                    confirmedAdAllow = false,
                                    allowWarningMessage = warning,
                                )
                            } else {
                                item.copy(
                                    action = ReviewAction.ALLOW,
                                    pendingAllowConfirmation = false,
                                    confirmedAdAllow = warning != null,
                                    allowWarningMessage = null,
                                )
                            }
                        } else {
                            item.copy(
                                action = action,
                                pendingAllowConfirmation = false,
                                confirmedAdAllow = false,
                                allowWarningMessage = null,
                            )
                        }
                    }
                _uiState.value = current.copy(domains = updated, appliedSuccess = false)
            }
        }

        /**
         * Toggles exact-host vs whole-site scope for a review row when allowed by shared-platform safety.
         *
         * @param domain Hostname to update.
         * @param wholeSite Whether whole-site scope was requested; ignored when [ReviewedDomain.canAllowWholeSite] is false.
         */
        fun setAllowWholeSite(
            domain: String,
            wholeSite: Boolean,
        ) {
            val current = _uiState.value
            if (current is LearnUiState.Reviewing) {
                val updated =
                    current.domains.map { item ->
                        if (item.domain == domain) {
                            item.copy(allowWholeSite = wholeSite && item.canAllowWholeSite)
                        } else {
                            item
                        }
                    }
                _uiState.value = current.copy(domains = updated, appliedSuccess = false)
            }
        }

        /**
         * Explicitly selects [ReviewAction.BLOCK] for all captured domains categorized as [DomainCategory.TRACKER_AD].
         */
        fun selectAllListMatches() {
            val current = _uiState.value
            if (current is LearnUiState.Reviewing) {
                val updated =
                    current.domains.map { item ->
                        if (item.category == DomainCategory.TRACKER_AD) {
                            item.copy(
                                action = ReviewAction.BLOCK,
                                pendingAllowConfirmation = false,
                                confirmedAdAllow = false,
                                allowWarningMessage = null,
                            )
                        } else {
                            item
                        }
                    }
                _uiState.value = current.copy(domains = updated, appliedSuccess = false)
            }
        }

        /**
         * Asynchronously submits reviewed rules: ALLOW defaults to exact host unless whole-site is toggled
         * on a non-shared domain; BLOCK adds exact-host blocks; KEEP_DEFAULT is untouched.
         */
        fun applyRules() {
            val current = _uiState.value
            if (current is LearnUiState.Reviewing) {
                viewModelScope.launch {
                    LearnReviewPolicy.applyReviewedRules(
                        domains = current.domains,
                        userRuleGateway = userRuleGateway,
                        publicSuffixRules = publicSuffixRules,
                        isBlockedByRules = adBlockMatcher,
                    )
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
    }
