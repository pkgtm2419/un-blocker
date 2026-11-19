package com.unblocker.app.services

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Diagnostic snapshot of device system settings affecting VPN persistence, battery life, and DNS leakage.
 *
 * @property isBatteryOptimizationIgnored Whether the application is exempt from battery optimization killers.
 * @property isPrivateDnsActive Whether Android system Private DNS (DoT) is actively intercepting queries.
 * @property privateDnsServerName Hostname of the configured system Private DNS provider, if active.
 * @property manufacturer Normalized device manufacturer name for OEM-specific guidance.
 */
data class HardeningPosture(
    val isBatteryOptimizationIgnored: Boolean,
    val isPrivateDnsActive: Boolean,
    val privateDnsServerName: String?,
    val manufacturer: String,
) {
    /**
     * Whether all critical system security settings are aligned for leak-free, persistent filtering.
     */
    val isFullyHardened: Boolean
        get() = isBatteryOptimizationIgnored && !isPrivateDnsActive
}

/**
 * Inspects device posture and provides safe navigation intents to Android system configuration panels.
 */
object SystemHardeningChecker {
    /**
     * Intent action directed to the system VPN settings page for Always-On configuration.
     */
    const val ACTION_VPN_SETTINGS = "android.net.vpn.SETTINGS"

    /**
     * Intent action directed to Google Ad Privacy settings for Advertising ID deletion.
     */
    const val ACTION_AD_PRIVACY = "com.google.android.gms.settings.ADS_PRIVACY"

    /**
     * Builds the Google Play Store web or deep link URL for an application package.
     *
     * @param packageName Target application package identifier.
     * @return Canonical Play Store URL string.
     */
    fun playStoreUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    /**
     * Inspects active system settings and returns the current [HardeningPosture].
     *
     * @param context Application context used to query system power and connectivity services.
     * @return Current system hardening posture snapshot.
     */
    fun checkPosture(context: Context): HardeningPosture {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryOptimizationIgnored =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                true
            }

        var isPrivateDnsActive = UnblockerVpnService.isPrivateDnsActive.value
        var privateDnsServerName: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val linkProperties = connectivityManager?.getLinkProperties(activeNetwork)
            if (linkProperties != null) {
                if (linkProperties.isPrivateDnsActive) {
                    isPrivateDnsActive = true
                }
                privateDnsServerName = linkProperties.privateDnsServerName
            }
        }

        return HardeningPosture(
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            isPrivateDnsActive = isPrivateDnsActive,
            privateDnsServerName = privateDnsServerName,
            manufacturer = Build.MANUFACTURER,
        )
    }

    /**
     * Creates an intent navigating the user to battery optimization exemption settings.
     *
     * @param context Context providing the package name for targeted system dialogs.
     * @return Intent directed to application-specific battery settings or system list.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

    /**
     * Creates an intent opening Android's system VPN settings to configure Always-on VPN.
     *
     * @return Intent directed to the system VPN settings page.
     */
    fun createVpnSettingsIntent(): Intent =
        Intent(ACTION_VPN_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Creates an intent opening network settings where Private DNS can be toggled off.
     *
     * @return Intent directed to wireless or network settings.
     */
    fun createNetworkSettingsIntent(): Intent =
        Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Creates an intent opening Google Ad Privacy settings to delete the Advertising ID.
     *
     * @return Intent directed to ad privacy settings or general privacy settings.
     */
    fun createAdPrivacyIntent(): Intent =
        Intent(ACTION_AD_PRIVACY).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    /**
     * Creates a navigation intent directed to an application's listing in the Google Play Store.
     *
     * @param packageName Target application package identifier.
     * @return Intent directed to the Play Store or browser fallback.
     */
    fun createPlayStoreIntent(packageName: String): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(playStoreUrl(packageName)),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
