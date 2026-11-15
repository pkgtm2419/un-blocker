package com.unblocker.vpn.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers Android internet-capable network callbacks and invokes a caller-owned listener.
 *
 * Callbacks run on ConnectivityManager callback threads, so the listener must dispatch heavy work.
 * Registration failures are caught; registration does not guarantee an initial notification.
 *
 * @param context Application context providing ConnectivityManager.
 */
@Singleton
class NetworkChangeReceiver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var onNetworkChanged: (() -> Unit)? = null
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                /**
                 * Notifies the listener that a matching network became available.
                 *
                 * @param network Network reported by Android.
                 */
                override fun onAvailable(network: Network) = onNetworkChanged?.invoke() ?: Unit

                /**
                 * Notifies the listener that a matching network was lost.
                 *
                 * @param network Network reported by Android.
                 */
                override fun onLost(network: Network) = onNetworkChanged?.invoke() ?: Unit

                /**
                 * Notifies the listener that network capabilities changed.
                 *
                 * @param network Network reported by Android.
                 * @param caps Latest capabilities supplied by Android.
                 */
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) = onNetworkChanged?.invoke() ?: Unit
            }

        /**
         * Replaces the listener and attempts to register the shared network callback.
         *
         * Registration exceptions are ignored. Repeated calls are not deduplicated here.
         *
         * @param callback Listener invoked for availability, loss, and capability changes.
         */
        fun register(callback: () -> Unit) {
            onNetworkChanged = callback
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            runCatching {
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        }

        /**
         * Clears the listener and attempts unregistration, ignoring exceptions from Android.
         */
        fun unregister() {
            onNetworkChanged = null
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
    }
