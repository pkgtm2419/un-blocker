package com.unblocker.app.ui

import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    val launchVpn = {
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        }
    }

    // Optional Notification Permission on Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue regardless of notification permission result
        launchVpn()
    }

    val requestPermissionsAndStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Wait for notification permission callback to launch VPN
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpn()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFF333333), androidx.compose.foundation.shape.RoundedCornerShape(32.dp)),
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Security, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.List, contentDescription = "Logs") },
                        label = { Text("Logs") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
