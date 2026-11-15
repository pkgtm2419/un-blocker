package com.unblocker.app.services

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import java.net.InetSocketAddress

/**
 * Best-effort Android connection-owner and package metadata lookup.
 *
 * @param context Context supplying connectivity and package managers.
 */
class UidResolver(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Looks up connection ownership on Android 10 or later.
     *
     * @param protocol IP transport protocol number, normally UDP or TCP.
     * @param localAddress Client-side socket address including port.
     * @param remoteAddress Destination socket address including port.
     * @return Android UID, or null on older APIs, invalid ownership or lookup failure.
     */
    fun resolveUid(
        protocol: Int,
        localAddress: InetSocketAddress,
        remoteAddress: InetSocketAddress
    ): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cm = connectivityManager ?: return null
            return runCatching {
                val uid = cm.getConnectionOwnerUid(protocol, localAddress, remoteAddress)
                if (uid != Process.INVALID_UID) uid else null
            }.getOrNull()
        }
        return null
    }

    /**
     * Finds the first package associated with a UID.
     *
     * @param uid Android UID to inspect.
     * @return Package name, or null when unavailable or lookup fails.
     */
    fun getPackageName(uid: Int): String? {
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

    /**
     * Finds a user-visible application label.
     *
     * @param packageName Package identifier to inspect.
     * @return Application label, falling back to the package identifier on lookup failure.
     */
    fun getAppLabel(packageName: String): String {
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }
}
