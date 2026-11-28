package com.unblocker.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.SystemHardeningChecker
import com.unblocker.app.services.UnblockerVpnService
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.components.BeyondDnsHubDialog
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.logs.LogViewModel
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.TealPrimary
import kotlinx.coroutines.launch

/**
 * Renders grouped, searchable filtering preferences, system diagnostics, and modular sub-screens (WP-13 / F28).
 *
 * @param preferences Source of persisted settings and destination for setting changes.
 * @param quickStartManager Source of current service lifecycle status.
 * @param onNavigateToExtensions Callback opening the extension catalog.
 * @param initialSection Optional section slug routed from deep link (`unblocker://settings/<slug>`).
 * @param onNavigateToExceptions Callback opening user exceptions and rule tester.
 * @param onNavigateToStatistics Callback opening protection statistics.
 * @param onNavigateToOnboarding Callback opening guided onboarding setup.
 * @param onNavigateToAbout Callback opening dedicated About screen.
 * @param contentPadding Parent navigation padding whose bottom inset is applied to scrolling content.
 * @param logViewModel Owner of local log export and history-clearing actions.
 */
@Composable
fun SettingsScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onNavigateToExtensions: () -> Unit,
    initialSection: String? = null,
    onNavigateToExceptions: () -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    logViewModel: LogViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val dimensions = LocalWindowDimensions.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val versionName =
        remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "2.7.2"
        }

    var activeSectionSlug by rememberSaveable {
        mutableStateOf(SettingsSectionCatalog.resolveSection(initialSection)?.slug)
    }
    LaunchedEffect(initialSection) {
        val resolved = SettingsSectionCatalog.resolveSection(initialSection)
        if (resolved != null) {
            activeSectionSlug = resolved.slug
        }
    }
    val activeSection = remember(activeSectionSlug) { SettingsSectionCatalog.resolveSection(activeSectionSlug) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = activeSection != null) {
        activeSectionSlug = null
    }

    val serviceStatus by quickStartManager.status.collectAsState()
    val filteringTier by preferences.filteringTier.collectAsState()
    val adBlockingEnabled by preferences.adBlockingEnabled.collectAsState()
    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()
    val blockingMode by preferences.blockingMode.collectAsState()
    val blockAnalyticsBreakage by preferences.blockAnalyticsBreakage.collectAsState()
    val autoRestartOnBoot by preferences.autoRestartOnBoot.collectAsState()
    val healthCheckMinutes by preferences.healthCheckIntervalMinutes.collectAsState()
    val isQueryLoggingPaused by preferences.isQueryLoggingPaused.collectAsState()

    val isPrivateDnsActive by UnblockerVpnService.isPrivateDnsActive.collectAsState()
    val privateDnsServerName by UnblockerVpnService.privateDnsServerName.collectAsState()

    var postureRefreshTrigger by remember { mutableIntStateOf(0) }
    val posture =
        remember(postureRefreshTrigger, isPrivateDnsActive) {
            SystemHardeningChecker.checkPosture(context)
        }
    var showBeyondDnsDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UnblockerVpnService.checkPrivateDns(context)
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            val count = logViewModel.exportLogsToCsv(os)
                            Toast
                                .makeText(
                                    context,
                                    resources.getString(R.string.settings_export_success_toast, count),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }.onFailure { e ->
                        Toast
                            .makeText(
                                context,
                                resources.getString(R.string.settings_export_failed_toast, e.message.orEmpty()),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }
        }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_clear_dialog_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_clear_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        logViewModel.clearAllLogs()
                        showClearConfirmDialog = false
                        Toast
                            .makeText(
                                context,
                                resources.getString(R.string.settings_history_cleared_toast),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                ) {
                    Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showBeyondDnsDialog) {
        BeyondDnsHubDialog(onDismiss = { showBeyondDnsDialog = false })
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val horizontalPadding = if (dimensions.widthClass != WindowWidthClass.COMPACT) 32.dp else 16.dp

    HaikeiAtmosphericBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = statusBarPadding + 16.dp,
                            bottom = contentPadding.calculateBottomPadding() + 88.dp,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (activeSection == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isDark) Slate800 else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFCBD5E1)),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_version_badge, versionName),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.action_clear_search),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    )

                    val filtered =
                        SettingsSectionCatalog.filterSections(
                            query = searchQuery,
                            titleForSection = { resources.getString(it.titleRes) },
                            subtitleForSection = { resources.getString(it.subtitleRes) },
                        )

                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_empty, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    } else {
                        SettingsSectionGroup.entries.forEach { group ->
                            val groupSections = filtered.filter { it.group == group }
                            if (groupSections.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(group.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )

                                    groupSections.forEach { section ->
                                        SettingsSectionSummaryCard(
                                            section = section,
                                            badgeText =
                                                sectionBadgeText(
                                                    section = section,
                                                    versionName = versionName,
                                                    isQueryLoggingPaused = isQueryLoggingPaused,
                                                    tierName = filteringTier.name,
                                                ),
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                activeSectionSlug = section.slug
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { activeSectionSlug = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(activeSection.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource(activeSection.subtitleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                        when (activeSection) {
                            SettingsSection.PROTECTION -> {
                                ProtectionSectionContent(
                                    filteringTier = filteringTier,
                                    onTierSelected = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        preferences.setFilteringTier(it)
                                    },
                                    adBlockingEnabled = adBlockingEnabled,
                                    onAdBlockingChanged = { preferences.setAdBlockingEnabled(it) },
                                    adultBlockingEnabled = adultBlockingEnabled,
                                    onAdultBlockingChanged = { preferences.setAdultBlockingEnabled(it) },
                                    blockingMode = blockingMode,
                                    onBlockingModeSelected = { preferences.setBlockingMode(it) },
                                )
                            }

                            SettingsSection.FILTERS -> {
                                FiltersSectionContent(onNavigateToExtensions = onNavigateToExtensions)
                            }

                            SettingsSection.RULES -> {
                                RulesSectionContent(onNavigateToExceptions = onNavigateToExceptions)
                            }

                            SettingsSection.LEARNING -> {
                                LearningSectionContent(
                                    daysSinceInstall = preferences.getDaysSinceInstall(),
                                    blockAnalyticsBreakage = blockAnalyticsBreakage,
                                    onBlockAnalyticsBreakageChanged = { preferences.setBlockAnalyticsBreakage(it) },
                                    onNavigateToStatistics = onNavigateToStatistics,
                                )
                            }

                            SettingsSection.PRIVACY -> {
                                PrivacySectionContent(
                                    isQueryLoggingPaused = isQueryLoggingPaused,
                                    onQueryLoggingPausedChanged = { preferences.setQueryLoggingPaused(it) },
                                    onExportCsv = {
                                        val timestamp =
                                            java.text
                                                .SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                                .format(java.util.Date())
                                        exportLauncher.launch("unblocker_export_$timestamp.csv")
                                    },
                                    onRequestClearHistory = { showClearConfirmDialog = true },
                                )
                            }

                            SettingsSection.AUTOMATION -> {
                                AutomationSectionContent(
                                    isDark = isDark,
                                    autoRestartOnBoot = autoRestartOnBoot,
                                    onAutoRestartOnBootChanged = { preferences.setAutoRestartOnBoot(it) },
                                    healthCheckMinutes = healthCheckMinutes,
                                )
                            }

                            SettingsSection.APPEARANCE -> {
                                AppearanceSectionContent(
                                    onOpenBeyondDnsGuide = { showBeyondDnsDialog = true },
                                    onNavigateToOnboarding = onNavigateToOnboarding,
                                )
                            }

                            SettingsSection.HEALTH -> {
                                HealthSectionContent(
                                    isDark = isDark,
                                    serviceStatus = serviceStatus,
                                    isPrivateDnsActive = isPrivateDnsActive,
                                    privateDnsServerName = privateDnsServerName,
                                    posture = posture,
                                    onRefreshPosture = { postureRefreshTrigger++ },
                                    onOpenNetworkSettings = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                },
                                            )
                                        }
                                    },
                                    onOpenVpnSettings = {
                                        runCatching {
                                            context.startActivity(SystemHardeningChecker.createVpnSettingsIntent())
                                        }
                                    },
                                )
                            }

                            SettingsSection.ABOUT -> {
                                AboutSectionContent(
                                    versionName = versionName,
                                    onNavigateToAbout = onNavigateToAbout,
                                    onOpenRepository = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://github.com/pkgtm2419/un-blocker"),
                                                ).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionSummaryCard(
    section: SettingsSection,
    badgeText: String?,
    onClick: () -> Unit,
) {
    SkeuomorphicCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(section.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealPrimary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sectionBadgeText(
    section: SettingsSection,
    versionName: String,
    isQueryLoggingPaused: Boolean,
    tierName: String,
): String? =
    when (section) {
        SettingsSection.PROTECTION -> {
            tierName
        }

        SettingsSection.PRIVACY -> {
            if (isQueryLoggingPaused) {
                stringResource(R.string.settings_badge_paused)
            } else {
                stringResource(R.string.settings_badge_active)
            }
        }

        SettingsSection.ABOUT -> {
            stringResource(R.string.settings_version_badge, versionName)
        }

        else -> {
            null
        }
    }

/**
 * Companion card linking to multi-layered solutions for video streams and in-feed ads.
 *
 * @param onOpenGuide Callback invoked to present the educational companion dialog.
 * @param modifier Layout modifier applied to the root card container.
 */
@Composable
fun BeyondDnsCompanionCard(
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkeuomorphicCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.beyond_dns_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = stringResource(R.string.beyond_dns_card_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.beyond_dns_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.beyond_dns_card_button),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TealPrimary,
                )
            }
        }
    }
}
