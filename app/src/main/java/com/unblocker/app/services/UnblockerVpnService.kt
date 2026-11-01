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
import com.unblocker.app.logic.dns.ForwardingDnsRequest
import com.unblocker.app.logic.dns.toForwardingRequest
import com.unblocker.vpn.builder.TrappedDnsServers
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

    private val dnsCache = com.unblocker.app.logic.dns.DnsCache()
    private val bufferPool = com.unblocker.app.logic.dns.ByteArrayPool(4096, 64)
    private val upstreamClient by lazy {
        DnsUpstreamClient(
            protectDatagramSocket = { socket -> protect(socket) },
            protectTcpSocket = { socket -> protect(socket) }
        )
    }

    private lateinit var connectivityManager: ConnectivityManager
    private val networkResolvers = ResolverRegistry<Network> {
        dnsCache.clear()
        upstreamClient.resetPreferredResolver()
    }
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
        // SECURITY: Register passive listener for system network connectivity and LinkProperties changes.
        // This callback is strictly read-only: it detects underlying network switches (e.g. Wi-Fi <-> Cellular)
        // to adapt local DNS resolver pointers, and initiates ZERO outbound telemetry or network flows.
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
                .setSession("Un-Blocker DNS Shield")
                .setMtu(1500)
                .setBlocking(true)
                .addAddress("10.111.222.1", 24)
                .addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32)

            // Trap well-known public DNS resolvers to intercept evasive direct-socket traffic (SEC-01)
            val ipv4DnsRoutes = listOf(
                "10.111.222.2", "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9"
            ) + TrappedDnsServers.IPV4_TRAPPED
            for (ip in ipv4DnsRoutes.distinct()) {
                try {
                    builder.addRoute(ip, 32)
                } catch (_: Exception) {}
            }

            // Intercept IPv6 DNS to eliminate cellular and Wi-Fi dual-stack bypass
            try {
                builder.addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                builder.addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
                builder.addRoute("fd00:1:fd00:1:fd00:1:fd00:2", 128)
                val ipv6DnsRoutes = listOf(
                    "fd00:1:fd00:1:fd00:1:fd00:2",
                    "2001:4860:4860::8888", "2001:4860:4860::8844",
                    "2606:4700:4700::1111", "2606:4700:4700::1001"
                ) + TrappedDnsServers.IPV6_TRAPPED
                for (ip in ipv6DnsRoutes.distinct()) {
                    try {
                        builder.addRoute(ip, 128)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // Graceful fallback on devices with no IPv6 support
            }

            // Exclude our own app process to avoid VPN routing loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            // Automatic Physical Network Tracking across Wi-Fi, 5G, and LTE switches
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    builder.setUnderlyingNetworks(null)
                } catch (_: Exception) {}
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

            val responseWriter = TunResponseWriter(run, session, outputStream)
            run.register(responseWriter)

            val packetBuffer = bufferPool.acquire()
            val coalescer = DnsRequestCoalescer()

            val tcpResponder = com.unblocker.app.logic.dns.TcpDnsResponder(
                executor = run.forwarding,
                emit = { packets ->
                    for (p in packets) {
                        responseWriter.offer(p)
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
                            if (resolvers.isEmpty()) {
                                VpnDiagnostics.record(
                                    code = VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT,
                                    exceptionClass = "NoResolversAvailable",
                                    resolverCount = 0
                                )
                                return@TcpDnsResponder DnsPacketUtil.buildServfailWireResponse(dnsWireBytes)
                            }
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
                            } else {
                                VpnDiagnostics.record(
                                    code = VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT,
                                    exceptionClass = "UpstreamResolutionNull",
                                    resolverCount = resolvers.size
                                )
                                DnsPacketUtil.buildServfailWireResponse(dnsWireBytes)
                            }
                        }
                    }
                }
            )

            try {
                while (isRunning.get()) {
                    val length = try {
                        inputStream!!.read(packetBuffer)
                    } catch (e: Exception) {
                        VpnDiagnostics.record(
                            code = VpnDiagnostics.FailureCode.TUN_READ_ERROR,
                            exceptionClass = e::class.java.name
                        )
                        break
                    }

                    if (length < 0) break
                    if (length == 0) continue

                    try {
                        val query = DnsPacketUtil.parseIpPacket(packetBuffer, length)
                        if (query == null) {
                            val consumed = try { tcpResponder.processPacket(packetBuffer, length) } catch (e: Exception) { false }
                            if (consumed) {
                                continue
                            }
                            val errorPacket = DnsPacketUtil.buildErrorResponseIfApplicable(packetBuffer, length)
                            if (errorPacket != null) {
                                responseWriter.offer(errorPacket)
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
                        responseWriter.offer(responsePacket)
                    } else {
                        // Check local high-speed DNS cache
                        val resolverSnapshot = networkResolvers.snapshot()
                        val cached = dnsCache.getValidated(query)
                        if (cached != null) {
                            val cachedPayload = cached.bytes
                            val metadata = cached.metadata
                            val wrappedResponse = if (filterEngine.blockedAlias(query.domain, metadata.aliases) != null)
                                DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                            else DnsPacketUtil.wrapClientResponse(query, cachedPayload)
                            responseWriter.offer(wrappedResponse)
                        } else {
                            // Check in-flight coalescer before submitting to the executor
                            val coalesceKey = CoalesceKey(query.domain, query.queryType, query.queryClass, query.isIpv6, resolverSnapshot.epoch)
                            when (coalescer.attachOrLead(coalesceKey, query)) {
                                DnsRequestCoalescer.JoinResult.Joined -> {
                                    VpnDiagnostics.recordCoalesced()
                                    // Attached to active in-flight resolution; response delivered when leader completes
                                }
                                DnsRequestCoalescer.JoinResult.Overflow -> {
                                    responseWriter.offer(DnsPacketUtil.buildServfailDnsResponsePacket(query))
                                }
                                DnsRequestCoalescer.JoinResult.Leader -> {
                                    // Bounded forwarding; blocking resolver I/O stays off the TUN loop.
                                    val forwardingRequest = query.toForwardingRequest()
                                    try {
                                        run.forwarding.execute {
                                            try {
                                                resolveAndForward(run, forwardingRequest, responseWriter, filterEngine, resolverSnapshot, coalescer, coalesceKey)
                                            } catch (e: Exception) {
                                                val all = coalescer.complete(coalesceKey)
                                                for (q in all) {
                                                    responseWriter.offer(DnsPacketUtil.buildServfailDnsResponsePacket(q))
                                                }
                                                VpnDiagnostics.record(
                                                    code = VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT,
                                                    exceptionClass = e::class.java.name,
                                                    queueDepth = run.forwarding.queue.size,
                                                    resolverCount = resolverSnapshot.resolvers.size
                                                )
                                            }
                                        }
                                    } catch (e: RejectedExecutionException) {
                                        val all = coalescer.complete(coalesceKey)
                                        VpnDiagnostics.forwardingRejected(
                                            queueDepth = run.forwarding.queue.size,
                                            resolverCount = resolverSnapshot.resolvers.size
                                        )
                                        for (q in all) {
                                            val servfailPacket = DnsPacketUtil.buildServfailDnsResponsePacket(q)
                                            responseWriter.offer(servfailPacket)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    } catch (e: Exception) {
                        VpnDiagnostics.record(
                            code = VpnDiagnostics.FailureCode.PACKET_PROCESSING,
                            exceptionClass = e::class.java.name
                        )
                        continue
                    }
                }
            } finally {
                bufferPool.release(packetBuffer)
                coalescer.clear()
            }
        } catch (e: Exception) {
            VpnDiagnostics.record(
                code = VpnDiagnostics.FailureCode.VPN_LOOP_FAILURE,
                exceptionClass = e::class.java.name
            )
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
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { localInterface?.close() } catch (ignored: Exception) {}
        }
    }

    private fun resolveAndForward(
        run: TunnelRun,
        request: ForwardingDnsRequest,
        responseWriter: TunResponseWriter,
        filterEngine: ContentFilterEngine,
        resolverSnapshot: ResolverRegistry.Snapshot,
        coalescer: DnsRequestCoalescer,
        coalesceKey: CoalesceKey
    ) {
        VpnDiagnostics.forwardingActiveIncrement()
        try {
            val query = request.query
            val dnsPayload = request.dnsPayload
            val isRunning = run.running

            if (!isRunning.get()) {
                coalescer.complete(coalesceKey)
                return
            }
            val resolvers = resolverSnapshot.resolvers
            if (resolvers.isEmpty()) {
                VpnDiagnostics.record(
                    code = VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT,
                    exceptionClass = "NoResolversAvailable",
                    resolverCount = 0
                )
                val allQueries = coalescer.complete(coalesceKey)
                for (q in allQueries) {
                    responseWriter.offer(DnsPacketUtil.buildServfailDnsResponsePacket(q))
                }
                return
            }
            val response = upstreamClient.resolve(run, query, dnsPayload, resolvers,
                outboundGuard = { action ->
                    var allowed = false
                    run.useWhileRunning { allowed = networkResolvers.useCurrent(resolverSnapshot, action) }
                    allowed
                })
            val allQueries = coalescer.complete(coalesceKey)
            if (allQueries.isEmpty()) return

            if (response == null) {
                VpnDiagnostics.record(
                    code = VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT,
                    exceptionClass = "UpstreamResolutionNull",
                    resolverCount = resolvers.size
                )
                for (q in allQueries) {
                    responseWriter.offer(DnsPacketUtil.buildServfailDnsResponsePacket(q))
                }
                return
            }
            if (!isRunning.get() || !session.owns(run.owner)) return

            val aliasBlocked = response.metadata.rcode == 0 &&
                filterEngine.blockedAlias(query.domain, response.metadata.aliases) != null

            for (q in allQueries) {
                val wrappedResponse = if (aliasBlocked) {
                    DnsPacketUtil.buildBlockedDnsResponsePacket(q)
                } else {
                    DnsPacketUtil.wrapClientResponse(q, response.bytes)
                }
                responseWriter.offer(wrappedResponse)
            }
            if (!aliasBlocked) dnsCache.putValidated(query, response)
        } finally {
            VpnDiagnostics.forwardingActiveDecrement()
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
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
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
