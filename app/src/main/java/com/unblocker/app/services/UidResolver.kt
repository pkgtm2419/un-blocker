package com.unblocker.app.services

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import java.net.InetSocketAddress

class UidResolver(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

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

    fun getPackageName(uid: Int): String? {
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
    }

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
