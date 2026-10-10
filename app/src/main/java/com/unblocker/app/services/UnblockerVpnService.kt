package com.unblocker.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.unblocker.app.MainActivity
import com.unblocker.app.R
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.logic.ContentFilterEngine
import com.unblocker.app.logic.dns.DnsPacketUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/**
 * On-device local VPN service.
 * Intercepts DNS queries on port 53, evaluates with ContentFilterEngine locally,
 * and drops unwanted ad/tracker and adult traffic without logging any user browsing history.
 */
class UnblockerVpnService : VpnService() {

    private var activeRun: TunnelRun? = null
    private val writeLock = Any()

    private val dnsCache = com.unblocker.app.logic.dns.DnsCache()
    private val bufferPool = com.unblocker.app.logic.dns.ByteArrayPool(4096, 64)
    private val upstreamClient by lazy {
        DnsUpstreamClient(
            protectDatagramSocket = { socket -> protect(socket) },
            protectTcpSocket = { socket -> protect(socket) }
        )
    }

    private lateinit var connectivityManager: ConnectivityManager
    private val networkResolvers = ResolverRegistry<Network> { dnsCache.clear() }
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkResolvers(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (isUsableUnderlyingNetwork(capabilities)) refreshNetworkResolvers(network)
            else {
                networkResolvers.remove(network)
                if (network == connectivityManager.activeNetwork) {
                    updatePrivateDnsState(null)
                }
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && isUsableUnderlyingNetwork(capabilities)) {
                networkResolvers.update(network, linkProperties.dnsServers)
                if (network == connectivityManager.activeNetwork) {
                    updatePrivateDnsState(linkProperties)
                }
            } else {
                networkResolvers.remove(network)
                if (network == connectivityManager.activeNetwork) {
                    updatePrivateDnsState(null)
                }
            }
        }

        override fun onLost(network: Network) {
            networkResolvers.remove(network)
            if (network == connectivityManager.activeNetwork) {
                updatePrivateDnsState(null)
            }
        }
    }

    private lateinit var preferences: FilteringPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = FilteringPreferences.getInstance(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.activeNetwork?.let { net ->
            refreshNetworkResolvers(net)
            connectivityManager.getLinkProperties(net)?.let(::updatePrivateDnsState)
        }
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback
            )
            networkCallbackRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (ACTION_STOP == action) {
            try { runCatching { preferences.setProtectionEnabled(false) } } finally {
                stopVpn(publishStopped = false)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        if (activeRun?.running?.get() != true) {
            startVpn(startId)
        }

        return START_STICKY
    }

    private fun startVpn(startId: Int) {
        val run = synchronized(lifecycleLock) {
            if (activeRun?.running?.get() == true) return
            activeRun?.close()
            TunnelRun(session.begin(), startId).also { activeRun = it }
        }
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
            session.failed(run.owner)
            stopVpn()
            stopSelf()
            return
        }

        run.thread = Thread({
            runVpnLoop(run)
        }, "UnblockerVpnWorker").apply {
            start()
        }
    }

    private fun runVpnLoop(run: TunnelRun) {
        val isRunning = run.running
        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null
        var localInterface: ParcelFileDescriptor? = null

        try {
            // Asset loading, Keystore and migration stay off the main/foreground deadline path.
            val filterEngine = ContentFilterEngine(this, preferences = preferences)
            val builder = Builder()
                .setSession("unblocker")
                .addAddress("10.10.0.2", 32)
                .addDnsServer("10.10.0.1")
                .addRoute("10.10.0.1", 32)
                .setMtu(1500)
                .setBlocking(true)

            // Intercept IPv6 DNS to eliminate cellular and Wi-Fi dual-stack bypass
            try {
                builder.addAddress("fd00:1::2", 128)
                builder.addDnsServer("fd00:1::1")
                builder.addRoute("fd00:1::1", 128)
            } catch (_: Exception) {
                // Graceful fallback on devices with no IPv6 support
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            synchronized(lifecycleLock) {
                if (!isRunning.get() || !session.owns(run.owner)) return
                localInterface = builder.establish() ?: error("VPN establishment failed")
                run.descriptor = localInterface
                inputStream = FileInputStream(localInterface!!.fileDescriptor)
                outputStream = FileOutputStream(localInterface!!.fileDescriptor)
                if (!session.established(run.owner)) return
                preferences.setServiceRunning(true)
            }

            val packetBuffer = bufferPool.acquire()

            val tcpResponder = com.unblocker.app.logic.dns.TcpDnsResponder(
                executor = run.forwarding,
                emit = { packets ->
                    run.useWhileRunning {
                        if (session.owns(run.owner)) synchronized(writeLock) {
                            try {
                                for (p in packets) outputStream?.write(p)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                },
                queryResolver = { dnsWireBytes, isIpv6, clientIp ->
                    val wireQuery = DnsPacketUtil.parseWireQuery(dnsWireBytes, isIpv6, clientIp) ?: return@TcpDnsResponder null
                    val filterResult = filterEngine.analyzeAndFilter(wireQuery.domain)
                    val wireDecision = if (filterResult.shouldBlock) 1 else 0
                    com.unblocker.app.data.logs.QueryLogSink.record(wireQuery.domain, wireDecision, filterResult.detectionMethod.ordinal)
                    if (filterResult.shouldBlock) {
                        DnsPacketUtil.buildBlockedWireResponse(dnsWireBytes)
                    } else {
                        val cached = dnsCache.getValidated(wireQuery)
                        if (cached != null) {
                            if (filterEngine.blockedAlias(wireQuery.domain, cached.metadata.aliases) != null) {
                                DnsPacketUtil.buildBlockedWireResponse(dnsWireBytes)
                            } else cached.bytes
                        } else {
                            val resolverSnapshot = networkResolvers.snapshot()
                            val resolvers = resolverSnapshot.resolvers
                            if (resolvers.isEmpty()) return@TcpDnsResponder null
                            val response = upstreamClient.resolve(
                                run,
                                wireQuery,
                                dnsWireBytes,
                                resolvers,
                                outboundGuard = { action ->
                                    var allowed = false
                                    run.useWhileRunning { allowed = networkResolvers.useCurrent(resolverSnapshot, action) }
                                    allowed
                                }
                            )
                            if (response != null) {
                                if (filterEngine.blockedAlias(wireQuery.domain, response.metadata.aliases) != null) {
                                    DnsPacketUtil.buildBlockedWireResponse(dnsWireBytes)
                                } else {
                                    dnsCache.putValidated(wireQuery, response)
                                    response.bytes
                                }
                            } else null
                        }
                    }
                }
            )

            try {
                while (isRunning.get()) {
                    val length = try {
                        inputStream!!.read(packetBuffer)
                    } catch (e: Exception) {
                        break
                    }

                    if (length < 0) break
                    if (length == 0) continue

                    try {
                        val packetCopy = packetBuffer.copyOf(length)
                        val query = DnsPacketUtil.parseIpPacket(packetCopy, length)
                        if (query == null) {
                            val consumed = try { tcpResponder.processPacket(packetCopy, length) } catch (e: Exception) { false }
                            if (consumed) {
                                continue
                            }
                            val errorPacket = DnsPacketUtil.buildErrorResponseIfApplicable(packetCopy, length)
                            if (errorPacket != null) {
                                run.useWhileRunning {
                                    if (session.owns(run.owner)) synchronized(writeLock) {
                                        outputStream?.write(errorPacket)
                                    }
                                }
                            }
                            continue
                        }

                        // Autonomous local analysis
                        if (!isRunning.get()) break
                        val result = filterEngine.analyzeAndFilter(query.domain)
                        val queryDecision = if (result.shouldBlock) 1 else 0
                        com.unblocker.app.data.logs.QueryLogSink.record(query.domain, queryDecision, result.detectionMethod.ordinal)

                    if (result.shouldBlock) {
                        // Local blocked response.
                        val responsePacket = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                        run.useWhileRunning {
                            if (session.owns(run.owner)) synchronized(writeLock) {
                                outputStream?.write(responsePacket)
                            }
                        }
                    } else {
                        // Check local high-speed DNS cache
                        val resolverSnapshot = networkResolvers.snapshot()
                        val cached = dnsCache.getValidated(query)
                        if (cached != null) {
                            val cachedPayload = cached.bytes
                            val metadata = cached.metadata
                            run.useWhileRunning {
                                if (session.owns(run.owner)) networkResolvers.useCurrent(resolverSnapshot) { synchronized(writeLock) {
                                    val wrappedResponse = if (filterEngine.blockedAlias(query.domain, metadata.aliases) != null)
                                        DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                                    else DnsPacketUtil.wrapClientResponse(query, cachedPayload)
                                    outputStream?.write(wrappedResponse)
                                } }
                            }
                        } else {
                            // Bounded forwarding; blocking resolver I/O stays off the TUN loop.
                            val currentOut = outputStream
                            try {
                                run.forwarding.execute {
                                    try {
                                        resolveAndForward(run, query, currentOut, filterEngine, resolverSnapshot)
                                    } catch (_: Exception) {
                                        // Closing the run or malformed upstream data fails this query closed.
                                    }
                                }
                            } catch (_: RejectedExecutionException) {
                                // Saturation: drop this query; client DNS retries. Memory stays bounded.
                            }
                        }
                    }
                    } catch (e: Exception) {
                        continue
                    }
                }
            } finally {
                bufferPool.release(packetBuffer)
            }
        } catch (_: Exception) {
            // State below records failure; exception text can contain user domains.
        } finally {
            synchronized(lifecycleLock) {
                val failed = isRunning.get()
                run.close()
                if (failed && session.owns(run.owner)) {
                    session.failed(run.owner)
                    preferences.setServiceRunning(false)
                    stopSelf(run.startId)
                }
            }
            synchronized(writeLock) {
                try { inputStream?.close() } catch (ignored: Exception) {}
                try { outputStream?.close() } catch (ignored: Exception) {}
                try { localInterface?.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun resolveAndForward(run: TunnelRun, query: com.unblocker.app.logic.dns.DnsQuery, outputStream: FileOutputStream?, filterEngine: ContentFilterEngine, resolverSnapshot: ResolverRegistry.Snapshot) {
        val isRunning = run.running
        val dnsPayload = ByteArray(query.dnsLength)
        System.arraycopy(query.rawPacket, query.dnsOffset, dnsPayload, 0, query.dnsLength)

        if (!isRunning.get()) return
        val resolvers = resolverSnapshot.resolvers
        if (resolvers.isEmpty()) return
        val response = upstreamClient.resolve(run, query, dnsPayload, resolvers,
            outboundGuard={ action ->
                var allowed=false
                run.useWhileRunning { allowed=networkResolvers.useCurrent(resolverSnapshot,action) }
                allowed
            }) ?: return
        if (!isRunning.get() || !session.owns(run.owner)) return

        run.useWhileRunning {
            if (!session.owns(run.owner)) return@useWhileRunning
            networkResolvers.useCurrent(resolverSnapshot) {
            val aliasBlocked = response.metadata.rcode == 0 &&
                filterEngine.blockedAlias(query.domain, response.metadata.aliases) != null
            val wrappedResponse = if (aliasBlocked) DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                else DnsPacketUtil.wrapClientResponse(query,response.bytes)
            synchronized(writeLock) {
                outputStream?.write(wrappedResponse)
            }
            if (!aliasBlocked) dnsCache.putValidated(query,response)
            }
        }
    }

    private fun refreshNetworkResolvers(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)
        if (capabilities != null && linkProperties != null && isUsableUnderlyingNetwork(capabilities)) {
            networkResolvers.update(network, linkProperties.dnsServers)
            if (network == connectivityManager.activeNetwork) {
                updatePrivateDnsState(linkProperties)
            }
        } else {
            networkResolvers.remove(network)
            if (network == connectivityManager.activeNetwork) {
                updatePrivateDnsState(null)
            }
        }
    }

    private fun isUsableUnderlyingNetwork(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, UnblockerVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_blocker_notification)
            .setContentTitle("unblocker")
            .setContentText("Local DNS protection service • See app for connection status")
            .setSubText("On-device learning • No telemetry")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_blocker_notification, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "unblocker Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows live status of active local network protection"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun stopVpn(publishStopped: Boolean = true) {
        synchronized(lifecycleLock) {
            val run = activeRun
            run?.close()
            if (run == null || session.owns(run.owner)) {
                preferences.setServiceRunning(false)
                if (session.status.value != ServiceStatus.ERROR) {
                    if (publishStopped) session.stop() else session.stopping()
                }
            }
            dnsCache.clear()
            updatePrivateDnsState(null)
        }
        runCatching { com.unblocker.app.logic.analysis.DeviceLearning.flush() }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_MODERATE) {
            dnsCache.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        dnsCache.clear()
    }

    override fun onDestroy() {
        stopVpn()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        networkResolvers.clear()
        super.onDestroy()
    }

    override fun onRevoke() {
        try { runCatching { preferences.setProtectionEnabled(false) } } finally {
            stopVpn(publishStopped = false)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_START = "com.unblocker.app.START_VPN"
        const val ACTION_STOP = "com.unblocker.app.STOP_VPN"
        private const val CHANNEL_ID = "unblocker_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        // Shared lock serializes establish/teardown even across successive Service instances.
        private val lifecycleLock = Any()
        val session = VpnSessionState()
        val isServiceActive: StateFlow<Boolean> = session.active

        private val _isPrivateDnsActive = MutableStateFlow(false)
        val isPrivateDnsActive: StateFlow<Boolean> = _isPrivateDnsActive.asStateFlow()

        private val _privateDnsServerName = MutableStateFlow<String?>(null)
        val privateDnsServerName: StateFlow<String?> = _privateDnsServerName.asStateFlow()

        fun shouldWarn(active: Boolean, serverName: String?): Boolean {
            return serverName != null
        }

        fun updatePrivateDnsState(active: Boolean, serverName: String?) {
            _isPrivateDnsActive.value = active
            _privateDnsServerName.value = if (active) serverName else null
        }

        fun updatePrivateDnsState(linkProperties: LinkProperties?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val active = linkProperties?.isPrivateDnsActive == true
                val serverName = if (active) linkProperties?.privateDnsServerName else null
                updatePrivateDnsState(active, serverName)
            } else {
                updatePrivateDnsState(false, null)
            }
        }

        fun checkPrivateDns(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val activeNet = cm?.activeNetwork
                val lp = activeNet?.let { cm.getLinkProperties(it) }
                updatePrivateDnsState(lp)
            }
        }

        fun start(context: Context, fromBackground: Boolean = false) {
            val intent = Intent(context, UnblockerVpnService::class.java).apply {
                action = ACTION_START
            }
            if (fromBackground) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try { FilteringPreferences.getInstance(context).setProtectionEnabled(false) } finally {
                val intent = Intent(context, UnblockerVpnService::class.java).apply {
                    action = ACTION_STOP
                }
                val status = session.status.value
                if (status == ServiceStatus.RUNNING || status == ServiceStatus.STARTING) {
                    try {
                        // Deliver teardown to the live VpnService. stopService() alone cannot destroy a
                        // service while Android is still bound to its open VPN interface.
                        context.startService(intent)
                    } catch (_: Exception) {
                        context.stopService(intent)
                        if (!isServiceActive.value) session.stop()
                    }
                } else if (status != ServiceStatus.STOPPING) {
                    context.stopService(intent)
                    session.stop()
                }
            }
        }
    }
}
