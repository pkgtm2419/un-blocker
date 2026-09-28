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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
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

    private lateinit var connectivityManager: ConnectivityManager
    private val networkResolvers = ConcurrentHashMap<Network, List<InetAddress>>()
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkResolvers(network)

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (isUsableUnderlyingNetwork(capabilities)) refreshNetworkResolvers(network)
            else networkResolvers.remove(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && isUsableUnderlyingNetwork(capabilities)) {
                networkResolvers[network] = DnsResolverPolicy.sanitize(linkProperties.dnsServers)
            } else networkResolvers.remove(network)
        }

        override fun onLost(network: Network) {
            networkResolvers.remove(network)
        }
    }

    private lateinit var preferences: FilteringPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = FilteringPreferences.getInstance(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.activeNetwork?.let(::refreshNetworkResolvers)
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
            try { preferences.setProtectionEnabled(false) } finally {
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

            try {
                while (isRunning.get()) {
                    val length = try {
                        inputStream!!.read(packetBuffer)
                    } catch (e: Exception) {
                        break
                    }

                    if (length < 0) break
                    if (length == 0) continue

                    val packetCopy = packetBuffer.copyOf(length)
                    val query = DnsPacketUtil.parseIpPacket(packetCopy, length) ?: continue

                    // Autonomous local analysis
                    val result = synchronized(lifecycleLock) {
                        if (!isRunning.get()) return
                        filterEngine.analyzeAndFilter(query.domain)
                    }

                    if (result.shouldBlock) {
                        // Local blocked response.
                        val responsePacket = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                        synchronized(writeLock) {
                            outputStream?.write(responsePacket)
                        }
                    } else {
                        // Check local high-speed DNS cache
                        val cachedPayload = dnsCache.get(query.domain, query.queryType, query.transactionId)
                        if (cachedPayload != null) {
                            val wrappedResponse = wrapDnsResponseInIpUdp(query, cachedPayload, cachedPayload.size)
                            synchronized(writeLock) {
                                outputStream?.write(wrappedResponse)
                            }
                        } else {
                            // Bounded forwarding; blocking resolver I/O stays off the TUN loop.
                            val currentOut = outputStream
                            try {
                                run.forwarding.execute { resolveAndForward(run, query, currentOut) }
                            } catch (_: RejectedExecutionException) {
                                // Saturation: drop this query; client DNS retries. Memory stays bounded.
                            }
                        }
                    }
                }
            } finally {
                bufferPool.release(packetBuffer)
            }
        } catch (_: Exception) {
            // State below records failure; exception text can contain user domains.
        } finally {
            synchronized(lifecycleLock) {
                val failed = isRunning.getAndSet(false)
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

    private fun resolveAndForward(run: TunnelRun, query: com.unblocker.app.logic.dns.DnsQuery, outputStream: FileOutputStream?) {
        val isRunning = run.running
        val dnsPayload = ByteArray(query.dnsLength)
        System.arraycopy(query.rawPacket, query.dnsOffset, dnsPayload, 0, query.dnsLength)

        if (!isRunning.get()) return
        val socket = try { DatagramSocket() } catch (_: Exception) { return }
        run.sockets.add(socket)

        val receiveBuffer = bufferPool.acquire()
        try {
            if (!isRunning.get() || !protect(socket)) return
            socket.soTimeout = 700
            for (upstream in configuredResolvers()) {
                if (!isRunning.get() || Thread.currentThread().isInterrupted) return
                try {
                    // Safe disconnect before reconnecting across upstreams
                    runCatching { socket.disconnect() }
                    // A connected UDP socket accepts only this resolver's replies.
                    socket.connect(upstream, 53)
                    val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstream, 53)
                    socket.send(outPacket)

                    val inPacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(inPacket)

                    if (inPacket.length >= 12) {
                        val respTxId = ((receiveBuffer[0].toInt() and 0xFF) shl 8) or (receiveBuffer[1].toInt() and 0xFF)
                        if (respTxId == (query.transactionId.toInt() and 0xFFFF)) {
                            val responseData = receiveBuffer.copyOf(inPacket.length)
                            dnsCache.put(query.domain, query.queryType, responseData)

                            val wrappedResponse = wrapDnsResponseInIpUdp(query, responseData, inPacket.length)
                            synchronized(writeLock) {
                                outputStream?.write(wrappedResponse)
                            }
                            break
                        }
                    }
                } catch (timeoutOrIo: Exception) {
                    // Failover to next upstream DNS resolver
                }
            }
        } catch (e: Exception) {
            // Network failure
        } finally {
            bufferPool.release(receiveBuffer)
            try { socket.close() } catch (ignored: Exception) {}
            run.sockets.remove(socket)
        }
    }

    private fun configuredResolvers(): List<InetAddress> {
        return DnsResolverPolicy.sanitize(networkResolvers.values.flatten())
    }

    private fun refreshNetworkResolvers(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)
        if (capabilities != null && linkProperties != null && isUsableUnderlyingNetwork(capabilities)) {
            networkResolvers[network] = DnsResolverPolicy.sanitize(linkProperties.dnsServers)
        } else networkResolvers.remove(network)
    }

    private fun isUsableUnderlyingNetwork(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun wrapDnsResponseInIpUdp(query: com.unblocker.app.logic.dns.DnsQuery, dnsBytes: ByteArray, dnsLength: Int): ByteArray {
        val udpLength = 8 + dnsLength
        val ipTotalLength = 20 + udpLength
        val buf = ByteBuffer.allocate(ipTotalLength)
        buf.order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header (20 bytes)
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(ipTotalLength.toShort())
        buf.putShort(0x0000.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0x0000.toShort()) // Placeholder for checksum
        buf.put(query.dstIp) // Src IP
        buf.put(query.srcIp) // Dst IP

        // Compute and inject mandatory IPv4 Header Checksum
        val ipChecksum = computeIpChecksum(buf.array(), 0, 20)
        buf.putShort(10, ipChecksum)

        // UDP Header (8 bytes)
        buf.position(20)
        buf.putShort(query.dstPort.toShort())
        buf.putShort(query.srcPort.toShort())
        buf.putShort(udpLength.toShort())
        buf.putShort(0x0000.toShort()) // Optional for IPv4 UDP

        // DNS Response Payload
        buf.put(dnsBytes, 0, dnsLength)

        return buf.array()
    }

    private fun computeIpChecksum(buf: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        for (i in offset until offset + length step 2) {
            val word = ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            sum += word
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
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
        }

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
        try { preferences.setProtectionEnabled(false) } finally {
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
