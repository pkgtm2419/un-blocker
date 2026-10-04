package com.unblocker.app.ui

import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.screens.UnblockerScreen

@Composable
fun MainScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager
) {
    val context = LocalContext.current

    // Launcher for Android System VPN Permission Consent
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        } else {
            // User cancelled or denied VPN permission
            preferences.setAdBlockingEnabled(false)
        }
    }

    // Optional Notification Permission on Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue regardless of notification permission result
    }

    val requestPermissionsAndStart = {
        // 1. Request notification permission on Android 13+ if not already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // 2. Request VPN Service permission
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Already prepared/granted
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        }
    }

    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            androidx.compose.material3.NavigationBar {
                androidx.compose.material3.NavigationBarItem(
                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Security, contentDescription = "Home") },
                    label = { androidx.compose.material3.Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                androidx.compose.material3.NavigationBarItem(
                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.List, contentDescription = "Logs") },
                    label = { androidx.compose.material3.Text("Logs") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().androidx.compose.foundation.layout.padding(paddingValues)) {
            if (selectedTab == 0) {
                UnblockerScreen(
                    preferences = preferences,
                    quickStartManager = quickStartManager,
                    onRequestVpnPermission = requestPermissionsAndStart
                )
            } else {
                com.unblocker.app.ui.logs.LogViewerScreen()
            }
        }
    }
}
