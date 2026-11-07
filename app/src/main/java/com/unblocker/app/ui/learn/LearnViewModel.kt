package com.unblocker.app.ui.learn

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.analysis.UserFeedback
import com.unblocker.app.services.RecordingSessionManager
import com.unblocker.app.services.SessionDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
)

enum class DomainCategory {
    FIRST_PARTY,
    CDN_INFRA,
    TRACKER_AD,
    OTHER
}

enum class ReviewAction {
    ALLOW,
    BLOCK,
    KEEP_DEFAULT
}

data class ReviewedDomain(
    val domain: String,
    val count: Int,
    val category: DomainCategory,
    val action: ReviewAction = ReviewAction.KEEP_DEFAULT
)

sealed interface LearnUiState {
    data class SelectingApp(
        val installedApps: List<AppItem>,
        val filteredApps: List<AppItem>,
        val query: String = ""
    ) : LearnUiState

    data class Recording(
        val app: AppItem,
        val elapsedSeconds: Int,
        val recordedDomains: List<SessionDomain>
    ) : LearnUiState

    data class Reviewing(
        val app: AppItem,
        val domains: List<ReviewedDomain>,
        val appliedSuccess: Boolean = false
    ) : LearnUiState
}

@HiltViewModel
class LearnViewModel @Inject constructor(
    application: Application,
    private val userRuleGateway: UserRuleGateway
) : AndroidViewModel(application) {

    private val sessionManager = RecordingSessionManager.instance
    private val _uiState = MutableStateFlow<LearnUiState>(LearnUiState.SelectingApp(emptyList(), emptyList()))
    val uiState: StateFlow<LearnUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var allApps: List<AppItem> = emptyList()

    init {
        loadInstalledLauncherApps()
    }

    fun loadInstalledLauncherApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.ResolveInfoFlags.of(0)
            } else {
                0
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            val appList = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                // Don't include self
                if (pkg == getApplication<Application>().packageName) return@mapNotNull null
                val label = ri.loadLabel(pm).toString()
                val icon = ri.loadIcon(pm)
                AppItem(pkg, label, icon)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.ROOT) }

            allApps = appList
            _uiState.value = LearnUiState.SelectingApp(appList, appList)
        }
    }

    fun searchApps(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = LearnUiState.SelectingApp(allApps, filtered, query)
    }

    fun startRecording(app: AppItem) {
        val pm = getApplication<Application>().packageManager
        val uid = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(app.packageName, PackageManager.ApplicationInfoFlags.of(0)).uid
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(app.packageName, 0).uid
            }
        }.getOrDefault(-1)

        sessionManager.startSession(app.packageName, uid)
        _uiState.value = LearnUiState.Recording(app, 0, emptyList())

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
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

    fun stopRecording() {
        timerJob?.cancel()
        val current = _uiState.value
        val app = when (current) {
            is LearnUiState.Recording -> current.app
            is LearnUiState.Reviewing -> current.app
            else -> return
        }

        val recorded = sessionManager.stopSession()
        val reviewed = recorded.map { domainItem ->
            val category = categorizeDomain(domainItem.domain, app.packageName)
            val defaultAction = when (category) {
                DomainCategory.FIRST_PARTY -> ReviewAction.ALLOW
                DomainCategory.CDN_INFRA -> ReviewAction.ALLOW
                DomainCategory.TRACKER_AD -> ReviewAction.BLOCK
                DomainCategory.OTHER -> ReviewAction.KEEP_DEFAULT
            }
            ReviewedDomain(
                domain = domainItem.domain,
                count = domainItem.count,
                category = category,
                action = defaultAction
            )
        }

        _uiState.value = LearnUiState.Reviewing(app, reviewed)
    }

    fun setDomainAction(domain: String, action: ReviewAction) {
        val current = _uiState.value
        if (current is LearnUiState.Reviewing) {
            val updated = current.domains.map {
                if (it.domain == domain) it.copy(action = action) else it
            }
            _uiState.value = current.copy(domains = updated)
        }
    }

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

    fun cancelSession() {
        sessionManager.stopSession()
        timerJob?.cancel()
        _uiState.value = LearnUiState.SelectingApp(allApps, allApps)
    }

    private fun categorizeDomain(domain: String, targetPackage: String): DomainCategory {
        val lower = domain.lowercase(Locale.ROOT)
        // 1. Check known CDN / Infrastructure
        val cdnSuffixes = listOf(
            "cloudfront.net", "akamaihd.net", "akamaiedge.net", "edgekey.net",
            "fastly.net", "cloudflare.com", "googleapis.com", "gstatic.com",
            "amazonaws.com", "1e100.net", "azureedge.net", "trafficmanager.net"
        )
        if (cdnSuffixes.any { lower.endsWith(it) }) {
            return DomainCategory.CDN_INFRA
        }

        // 2. Check First Party from package name segments
        val pkgSegments = targetPackage.split('.').filter { it.length > 2 && it !in setOf("com", "org", "net", "app", "android") }
        if (pkgSegments.any { lower.contains(it) }) {
            return DomainCategory.FIRST_PARTY
        }

        // 3. Check known tracking / telemetry patterns
        val trackerPatterns = listOf("telemetry", "analytics", "crashlytics", "track", "metrics", "ads", "doubleclick", "applovin", "unityads", "vungle")
        if (trackerPatterns.any { lower.contains(it) }) {
            return DomainCategory.TRACKER_AD
        }

        return DomainCategory.OTHER
    }
}
