package com.unblocker.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
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

/**
 * On-device local VPN service.
 * Intercepts DNS queries on port 53, evaluates with ContentFilterEngine locally,
 * and drops unwanted ad/tracker and adult traffic without logging any user browsing history.
 */
class UnblockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val writeLock = Any()

    private val dnsCache = com.unblocker.app.logic.dns.DnsCache()
    private var forwardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private val upstreamResolvers by lazy {
        listOf(
            InetAddress.getByName("8.8.8.8"),
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("9.9.9.9"),
            InetAddress.getByName("8.8.4.4")
        )
    }

    private lateinit var filterEngine: ContentFilterEngine
    private lateinit var preferences: FilteringPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = FilteringPreferences.getInstance(this)
        filterEngine = ContentFilterEngine(this, preferences = preferences)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (ACTION_STOP == action) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning.get()) {
            startVpn()
        }

        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.getAndSet(true)) return

        forwardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        preferences.setServiceRunning(true)
        _isServiceActive.value = true

        workerThread = Thread({
            runVpnLoop()
        }, "UnblockerVpnWorker").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun runVpnLoop() {
        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null

        try {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setUnderlyingNetworks(null)
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopSelf()
                return
            }

            inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

            val packetBuffer = ByteArray(4096)

            while (isRunning.get()) {
                val length = try {
                    inputStream.read(packetBuffer)
                } catch (e: Exception) {
                    break
                }

                if (length <= 0) continue

                val packetCopy = packetBuffer.copyOf(length)
                val query = DnsPacketUtil.parseIpPacket(packetCopy, length) ?: continue

                // Autonomous local analysis
                val result = filterEngine.analyzeAndFilter(query.domain)

                if (result.shouldBlock) {
                    // Fast local spoof response (0.0.0.0 in <0.05ms)
                    val responsePacket = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                    synchronized(writeLock) {
                        try {
                            outputStream?.write(responsePacket)
                        } catch (ignored: Exception) {}
                    }
                } else {
                    // Check local high-speed DNS cache
                    val cachedPayload = dnsCache.get(query.domain, query.queryType, query.transactionId)
                    if (cachedPayload != null) {
                        val wrappedResponse = wrapDnsResponseInIpUdp(query, cachedPayload, cachedPayload.size)
                        synchronized(writeLock) {
                            try {
                                outputStream?.write(wrappedResponse)
                            } catch (ignored: Exception) {}
                        }
                    } else {
                        // Forward query asynchronously in parallel on Dispatchers.IO - NEVER blocks the TUN loop!
                        val currentOut = outputStream
                        forwardScope.launch {
                            resolveAndForward(query, currentOut)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Error handling
        } finally {
            synchronized(writeLock) {
                try { inputStream?.close() } catch (ignored: Exception) {}
                try { outputStream?.close() } catch (ignored: Exception) {}
                try { vpnInterface?.close() } catch (ignored: Exception) {}
                vpnInterface = null
            }
        }
    }

    private fun resolveAndForward(query: com.unblocker.app.logic.dns.DnsQuery, outputStream: FileOutputStream?) {
        val dnsPayload = ByteArray(query.dnsLength)
        System.arraycopy(query.rawPacket, query.dnsOffset, dnsPayload, 0, query.dnsLength)

        val socket = try {
            DatagramSocket().apply {
                protect(this)
                soTimeout = 1200
            }
        } catch (e: Exception) {
            return
        }

        val receiveBuffer = ByteArray(4096)
        try {
            for (upstream in upstreamResolvers) {
                try {
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
            try { socket.close() } catch (ignored: Exception) {}
        }
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
            .setContentTitle("unblocker Protection Active")
            .setContentText("Analyzing network & blocking unwanted traffic locally")
            .setSubText("Zero-Cloud • Zero-Logs")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_blocker_notification, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    private fun stopVpn() {
        isRunning.set(false)
        preferences.setServiceRunning(false)
        _isServiceActive.value = false

        try {
            forwardScope.cancel()
            dnsCache.clear()
        } catch (ignored: Exception) {}

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {}

        workerThread?.interrupt()
        workerThread = null

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.unblocker.app.START_VPN"
        const val ACTION_STOP = "com.unblocker.app.STOP_VPN"
        private const val CHANNEL_ID = "unblocker_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, UnblockerVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UnblockerVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
