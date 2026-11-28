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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.ui.components.FloatingPillNavBar
import com.unblocker.app.ui.components.NavItem
import com.unblocker.app.ui.extensions.ExtensionsScreen
import com.unblocker.app.ui.learn.LearnAppScreen
import com.unblocker.app.ui.logs.LogViewerScreen
import com.unblocker.app.ui.navigation.AppRoute
import com.unblocker.app.ui.navigation.DeepLinkParser
import com.unblocker.app.ui.navigation.DeepLinkResult
import com.unblocker.app.ui.navigation.UnblockerDestinations
import com.unblocker.app.ui.screens.AboutScreen
import com.unblocker.app.ui.screens.ExceptionsScreen
import com.unblocker.app.ui.screens.OnboardingScreen
import com.unblocker.app.ui.screens.StatisticsScreen
import com.unblocker.app.ui.screens.UnblockerScreen
import com.unblocker.app.ui.settings.SettingsScreen

/**
 * Hosts Jetpack Navigation Compose graph, internal `unblocker://` deep-link routing,
 * floating pill navigation bar, and Android VPN / notification permission launchers (WP-11 / F26).
 *
 * @param preferences Persisted filtering preferences used by screens and service-start actions.
 * @param quickStartManager Service lifecycle controller and status source.
 * @param pendingDeepLinkUri Optional unconsumed deep link URI received by `MainActivity`.
 * @param onConsumeDeepLink Callback invoked after [pendingDeepLinkUri] is handled or rejected.
 */
@Composable
fun MainScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    pendingDeepLinkUri: String? = null,
    onConsumeDeepLink: () -> Unit = {},
) {
    val context = LocalContext.current

    // Launcher for Android System VPN Permission Consent
    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                preferences.setAdBlockingEnabled(true)
                quickStartManager.startBlockingServices(onPermissionRequired = {})
            } else {
                preferences.setProtectionEnabled(false)
            }
        }

    val launchVpn = {
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            preferences.setAdBlockingEnabled(true)
            quickStartManager.startBlockingServices(onPermissionRequired = {})
        }
    }

    // Optional Notification Permission on Android 13+ (API 33+)
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ ->
            launchVpn()
        }

    val requestPermissionsAndStart = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpn()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoutePattern = backStackEntry?.destination?.route
    val selectedTabIndex = UnblockerDestinations.selectedTabIndex(currentRoutePattern)
    val showBottomBar = selectedTabIndex >= 0

    val navigateToTopLevelTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(AppRoute.Home.pattern) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(pendingDeepLinkUri) {
        if (!pendingDeepLinkUri.isNullOrBlank()) {
            when (val parsed = DeepLinkParser.parse(pendingDeepLinkUri)) {
                is DeepLinkResult.Matched -> {
                    val dest = parsed.route
                    val target = dest.toNavRoute()
                    val isTopLevelTarget =
                        when (dest) {
                            AppRoute.Home, AppRoute.Learn -> true
                            is AppRoute.Logs -> dest.domain == null
                            is AppRoute.Settings -> dest.section == null
                            else -> false
                        }
                    if (isTopLevelTarget) {
                        navigateToTopLevelTab(target)
                    } else {
                        navController.navigate(target) {
                            launchSingleTop = true
                        }
                    }
                }

                is DeepLinkResult.Invalid -> {
                    // Reject malformed/invalid URIs silently; leave current navigation state untouched.
                }
            }
            onConsumeDeepLink()
        }
    }

    val navItems =
        remember {
            listOf(
                NavItem(title = "Home", icon = Icons.Filled.Security),
                NavItem(title = "Learn", icon = Icons.Filled.Apps),
                NavItem(title = "Logs", icon = Icons.AutoMirrored.Filled.List),
                NavItem(title = "Settings", icon = Icons.Filled.Settings),
            )
        }

    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = 64.dp + 16.dp + navBarBottomInset)

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Home.pattern,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(route = AppRoute.Home.pattern) {
                    UnblockerScreen(
                        preferences = preferences,
                        quickStartManager = quickStartManager,
                        onRequestVpnPermission = requestPermissionsAndStart,
                        contentPadding = contentPadding,
                        onOpenSettings = { navigateToTopLevelTab(AppRoute.Settings().toNavRoute()) },
                        onNavigateToLogs = { navigateToTopLevelTab(AppRoute.Logs().toNavRoute()) },
                    )
                }

                composable(route = AppRoute.Learn.pattern) {
                    LearnAppScreen(
                        onNavigateBack = { navController.popBackStack() },
                        contentPadding = contentPadding,
                    )
                }

                composable(
                    route = AppRoute.Logs.PATTERN,
                    arguments =
                        listOf(
                            navArgument(AppRoute.Logs.ARG_DOMAIN) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                ) { entry ->
                    val domainArg = entry.arguments?.getString(AppRoute.Logs.ARG_DOMAIN)
                    LogViewerScreen(
                        contentPadding = contentPadding,
                        initialDomain = domainArg,
                    )
                }

                composable(
                    route = AppRoute.Settings.PATTERN,
                    arguments =
                        listOf(
                            navArgument(AppRoute.Settings.ARG_SECTION) {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                ) { entry ->
                    val sectionArg = entry.arguments?.getString(AppRoute.Settings.ARG_SECTION)
                    SettingsScreen(
                        preferences = preferences,
                        quickStartManager = quickStartManager,
                        onNavigateToExtensions = {
                            navController.navigate(AppRoute.Extensions.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                        initialSection = sectionArg,
                        onNavigateToExceptions = {
                            navController.navigate(AppRoute.Exceptions.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToStatistics = {
                            navController.navigate(AppRoute.Statistics.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToOnboarding = {
                            navController.navigate(AppRoute.Onboarding.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAbout = {
                            navController.navigate(AppRoute.About.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                        contentPadding = contentPadding,
                    )
                }

                composable(route = AppRoute.Extensions.pattern) {
                    ExtensionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }

                composable(route = AppRoute.Exceptions.pattern) {
                    ExceptionsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenLogs = { navigateToTopLevelTab(AppRoute.Logs().toNavRoute()) },
                    )
                }

                composable(route = AppRoute.Statistics.pattern) {
                    StatisticsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenLogs = { navigateToTopLevelTab(AppRoute.Logs().toNavRoute()) },
                    )
                }

                composable(route = AppRoute.Onboarding.pattern) {
                    OnboardingScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenExtensions = {
                            navController.navigate(AppRoute.Extensions.toNavRoute()) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(route = AppRoute.About.pattern) {
                    AboutScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }

        if (showBottomBar) {
            FloatingPillNavBar(
                items = navItems,
                selectedItem = selectedTabIndex,
                onItemSelected = { index ->
                    val dest = UnblockerDestinations.topLevelTabs.getOrNull(index)
                    if (dest != null) {
                        navigateToTopLevelTab(dest.toNavRoute())
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
