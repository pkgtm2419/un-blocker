package com.unblocker.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.unblocker.app.ui.components.AgentStatusPanel
import com.unblocker.app.ui.components.BeyondDnsHubDialog
import com.unblocker.app.ui.components.FilteringTierCard
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.components.SystemHardeningWizard
import com.unblocker.app.ui.logs.LogViewModel
import com.unblocker.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Renders filtering preferences, DNS status hints, boot settings, and local log controls.
 *
 * @param preferences Source of persisted settings and destination for setting changes.
 * @param quickStartManager Source of current service lifecycle status.
 * @param onNavigateToExtensions Callback opening the extension catalog.
 * @param initialSection Optional highlighted section identifier from a deep link.
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

    val serviceStatus by quickStartManager.status.collectAsState()
    val filteringTier by preferences.filteringTier.collectAsState()
    val autoRestartOnBoot by preferences.autoRestartOnBoot.collectAsState()
    val healthCheckMinutes by preferences.healthCheckIntervalMinutes.collectAsState()
    val isQueryLoggingPaused by preferences.isQueryLoggingPaused.collectAsState()

    val isPrivateDnsActive by UnblockerVpnService.isPrivateDnsActive.collectAsState()
    val privateDnsServerName by UnblockerVpnService.privateDnsServerName.collectAsState()

    var postureRefreshTrigger by remember { mutableStateOf(0) }
    val posture =
        remember(postureRefreshTrigger, isPrivateDnsActive) {
            SystemHardeningChecker.checkPosture(context)
        }
    var showBeyondDnsDialog by remember { mutableStateOf(false) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UnblockerVpnService.checkPrivateDns(context)
    }

    val resources = LocalResources.current

    // Export query logs launcher
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

    // Clear confirmation dialog
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
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
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
        BeyondDnsHubDialog(
            onDismiss = { showBeyondDnsDialog = false },
        )
    }

    val stateColors =
        rememberVpnStateColors(
            serviceStatus = serviceStatus,
            isDarkTheme = isDark,
        )

    CompositionLocalProvider(LocalVpnStateColors provides stateColors) {
        HaikeiAtmosphericBackground {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(
                            horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 20.dp else 40.dp,
                            vertical = 24.dp,
                        ).padding(bottom = contentPadding.calculateBottomPadding() + 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Top Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
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

                // 1. PROTECTION LEVEL (FilteringTierCard)
                FilteringTierCard(
                    currentTier = filteringTier,
                    onTierSelected = { tier ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        preferences.setFilteringTier(tier)
                    },
                    modifier = Modifier.widthIn(max = 480.dp),
                )

                // 2. Hosts and filter list navigation
                SkeuomorphicCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
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
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_hosts_title),
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
                                    text = stringResource(R.string.settings_hosts_badge),
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
                            text = stringResource(R.string.settings_hosts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToExtensions()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_manage_filters),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // 3. NETWORK DIAGNOSTICS & PRIVATE DNS
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AgentStatusPanel(
                        serviceStatus = serviceStatus,
                        isPrivateDnsActive = isPrivateDnsActive,
                        privateDnsServerName = privateDnsServerName,
                        onOpenSettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    },
                                )
                            }
                        },
                    )

                    // Standalone Private DNS Warning Card if detected
                    if (UnblockerVpnService.shouldWarn(isPrivateDnsActive, privateDnsServerName)) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = if (isDark) Color(0xFF451A03) else Color(0xFFFFFBEB),
                                ),
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0xFFD97706) else Color(0xFFF59E0B),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_private_dns_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                },
                                            )
                                        }
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_open_network_settings),
                                        color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Always-on VPN button
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_VPN_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    },
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        border =
                            BorderStroke(
                                1.dp,
                                if (isDark) Slate700.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                            ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (isDark) {
                                        Slate900.copy(
                                            alpha = 0.5f,
                                        )
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_always_on_vpn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // 4. SYSTEM LEAK & HARDENING CHECKLIST
                SystemHardeningWizard(
                    posture = posture,
                    modifier = Modifier.widthIn(max = 480.dp),
                    onRefreshRequested = { postureRefreshTrigger++ },
                )

                // 5. DEFENSE-IN-DEPTH COMPANION (Streams, Feeds & Cosmetic)
                BeyondDnsCompanionCard(
                    onOpenGuide = { showBeyondDnsDialog = true },
                    modifier = Modifier.widthIn(max = 480.dp),
                )

                // 6. SYSTEM AUTOMATION & BACKGROUND HEALTH
                SkeuomorphicCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_automation_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto-start on boot switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_auto_boot_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_auto_boot_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = autoRestartOnBoot,
                                onCheckedChange = { enable ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    preferences.setAutoRestartOnBoot(enable)
                                },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = if (isDark) Slate800 else Color(0xFFE2E8F0),
                        )

                        // DNS Watchdog Interval Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_watchdog_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_watchdog_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Slate800 else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFCBD5E1)),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_watchdog_interval, healthCheckMinutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                // 5. DATA, LOGS & PRIVACY
                SkeuomorphicCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_privacy_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Pause Query Logging Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_pause_logging_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.settings_pause_logging_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isQueryLoggingPaused,
                                onCheckedChange = { paused ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    preferences.setQueryLoggingPaused(paused)
                                },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = TealAccent,
                                    ),
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = if (isDark) Slate800 else Color(0xFFE2E8F0),
                        )

                        // Export CSV & Clear History Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val timestamp =
                                        java.text
                                            .SimpleDateFormat(
                                                "yyyyMMdd_HHmmss",
                                                java.util.Locale.US,
                                            ).format(java.util.Date())
                                    exportLauncher.launch("unblocker_export_$timestamp.csv")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.logs_export_csv),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Button(
                                onClick = { showClearConfirmDialog = true },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.logs_clear_history),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                // 6. ABOUT & SYSTEM INFO
                SkeuomorphicCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.app_brand_version, versionName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.app_privacy_pillars),
                            style = MaterialTheme.typography.labelSmall,
                            color = TealPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_about_desc),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
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
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFCBD5E1)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_github_license),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
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
