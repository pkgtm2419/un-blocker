package com.unblocker.app.ui

import android.app.Activity
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.components.FloatingPillNavBar
import com.unblocker.app.ui.components.NavItem
import com.unblocker.app.ui.logs.LogViewerScreen
import com.unblocker.app.ui.screens.UnblockerScreen

/**
 * Main application host container with Samsung One UI 9 style floating pill navigation.
 */
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
        launchVpn()
    }

    val requestPermissionsAndStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpn()
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val navItems = remember {
        listOf(
            NavItem(title = "Home", icon = Icons.Filled.Security),
            NavItem(title = "Logs", icon = Icons.AutoMirrored.Filled.List)
        )
    }

    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = 64.dp + 16.dp + navBarBottomInset)

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                UnblockerScreen(
                    preferences = preferences,
                    quickStartManager = quickStartManager,
                    onRequestVpnPermission = requestPermissionsAndStart,
                    contentPadding = contentPadding
                )
            } else {
                LogViewerScreen(
                    contentPadding = contentPadding
                )
            }
        }

        FloatingPillNavBar(
            items = navItems,
            selectedItem = selectedTab,
            onItemSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
