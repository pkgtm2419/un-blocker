package com.unblocker.app.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lightweight descriptor for an installed launcher application discoverable under `<queries>`.
 *
 * @property packageName Unique Android package identifier.
 * @property label Localized user-facing application label.
 */
data class LauncherAppInfo(
    val packageName: String,
    val label: String,
)

/**
 * Queries installed launcher applications on [Dispatchers.IO], excluding Un-Blocker itself.
 */
object LauncherAppCatalog {
    /**
     * Loads and alphabetically sorts all visible launcher applications except [context]'s package.
     */
    suspend fun loadInstalledApps(context: Context): List<LauncherAppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val ownPackage = context.packageName
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(intent, 0)
                }

            resolveInfos
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    if (packageName == ownPackage) return@mapNotNull null
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    LauncherAppInfo(packageName = packageName, label = label)
                }.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase(Locale.ROOT) }
        }
}
