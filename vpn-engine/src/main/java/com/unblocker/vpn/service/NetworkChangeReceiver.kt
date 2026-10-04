package com.unblocker.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors active network changes and notifies the VPN service so it can
 * refresh its upstream DNS server list when the underlying network changes
 * (e.g., switching from Wi-Fi to mobile data).
 *
 * The [onNetworkChanged] callback is invoked on the ConnectivityManager callback thread;
 * callers must dispatch any heavy work to a background coroutine.
 */
@Singleton
class NetworkChangeReceiver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var onNetworkChanged: (() -> Unit)? = null
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onNetworkChanged?.invoke() ?: Unit
        override fun onLost(network: Network) = onNetworkChanged?.invoke() ?: Unit
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            onNetworkChanged?.invoke() ?: Unit
    }

    /** Registers the network callback. [callback] is invoked whenever connectivity changes. */
    fun register(callback: () -> Unit) {
        onNetworkChanged = callback
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    /** Unregisters the network callback. Safe to call even if not registered. */
    fun unregister() {
        onNetworkChanged = null
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }
}
