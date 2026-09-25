package com.unblocker.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unblocker.app.data.database.AppDatabase
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.components.HealthCheckDialog
import com.unblocker.app.ui.screens.DashboardScreen
import com.unblocker.app.ui.screens.LogsScreen
import com.unblocker.app.ui.screens.SettingsScreen
import com.unblocker.app.ui.screens.SplashScreen
import com.unblocker.app.ui.screens.StatisticsScreen
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent

enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    START("Quick Start", Icons.Default.PlayArrow),
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    LOGS("Logs", Icons.Default.FormatListBulleted),
    STATS("Stats", Icons.Default.BarChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    database: AppDatabase,
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager
) {
    val context = LocalContext.current
    val dimensions = LocalWindowDimensions.current
    val serviceStatus by quickStartManager.status.collectAsState()
    val isRunning = serviceStatus == ServiceStatus.RUNNING

    var currentDestination by remember { mutableStateOf(AppDestination.START) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    // VPN Permission Launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        }
    }

    val requestVpnPermission = {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        }
    }

    // Adaptive Navigation Layout
    if (dimensions.widthClass == WindowWidthClass.COMPACT) {
        // Phone Layout: Bottom Navigation Bar
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Slate900,
                    tonalElevation = 8.dp
                ) {
                    AppDestination.values().forEach { destination ->
                        val selected = currentDestination == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentDestination = destination },
                            icon = {
                                if (destination == AppDestination.START && isRunning) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = AllowedGreen,
                                                modifier = Modifier.size(6.dp)
                                            )
                                        }
                                    ) {
                                        Icon(imageVector = destination.icon, contentDescription = destination.label)
                                    }
                                } else {
                                    Icon(imageVector = destination.icon, contentDescription = destination.label)
                                }
                            },
                            label = { Text(text = destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = TealAccent,
                                indicatorColor = TealAccent,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate400
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScreenContent(
                    destination = currentDestination,
                    database = database,
                    preferences = preferences,
                    quickStartManager = quickStartManager,
                    onNavigateToDashboard = { currentDestination = AppDestination.DASHBOARD },
                    onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                    onNavigateToLogs = { currentDestination = AppDestination.LOGS },
                    onOpenDiagnostics = { showDiagnosticsDialog = true },
                    onVpnPermissionNeeded = requestVpnPermission
                )
            }
        }
    } else {
        // Tablet / Foldable / Landscape Layout: Navigation Rail on Left
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = Slate900,
                header = {
                    Spacer(modifier = Modifier.height(16.dp))
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "unblocker",
                        tint = if (isRunning) AllowedGreen else CyanGlow,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            ) {
                AppDestination.values().forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationRailItem(
                        selected = selected,
                        onClick = { currentDestination = destination },
                        icon = {
                            if (destination == AppDestination.START && isRunning) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AllowedGreen,
                                            modifier = Modifier.size(6.dp)
                                        )
                                    }
                                ) {
                                    Icon(imageVector = destination.icon, contentDescription = destination.label)
                                }
                            } else {
                                Icon(imageVector = destination.icon, contentDescription = destination.label)
                            }
                        },
                        label = { Text(text = destination.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = TealAccent,
                            indicatorColor = TealAccent,
                            unselectedIconColor = Slate400,
                            unselectedTextColor = Slate400
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ScreenContent(
                    destination = currentDestination,
                    database = database,
                    preferences = preferences,
                    quickStartManager = quickStartManager,
                    onNavigateToDashboard = { currentDestination = AppDestination.DASHBOARD },
                    onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                    onNavigateToLogs = { currentDestination = AppDestination.LOGS },
                    onOpenDiagnostics = { showDiagnosticsDialog = true },
                    onVpnPermissionNeeded = requestVpnPermission
                )
            }
        }
    }

    if (showDiagnosticsDialog) {
        HealthCheckDialog(onDismiss = { showDiagnosticsDialog = false })
    }
}

@Composable
private fun ScreenContent(
    destination: AppDestination,
    database: AppDatabase,
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onVpnPermissionNeeded: () -> Unit
) {
    when (destination) {
        AppDestination.START -> SplashScreen(
            quickStartManager = quickStartManager,
            onNavigateToDashboard = onNavigateToDashboard,
            onNavigateToSettings = onNavigateToSettings,
            onVpnPermissionNeeded = onVpnPermissionNeeded
        )
        AppDestination.DASHBOARD -> DashboardScreen(
            database = database,
            preferences = preferences,
            quickStartManager = quickStartManager,
            onNavigateToLogs = onNavigateToLogs,
            onOpenDiagnostics = onOpenDiagnostics,
            onVpnPermissionNeeded = onVpnPermissionNeeded
        )
        AppDestination.LOGS -> LogsScreen(database = database)
        AppDestination.STATS -> StatisticsScreen(database = database)
        AppDestination.SETTINGS -> SettingsScreen(preferences = preferences, database = database)
    }
}
