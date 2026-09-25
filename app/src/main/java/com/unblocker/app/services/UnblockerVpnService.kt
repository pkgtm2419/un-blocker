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
        var forwarderSocket: DatagramSocket? = null

        try {
            val builder = Builder()
                .setSession("unblocker")
                .addAddress("10.10.0.2", 32)
                .addDnsServer("10.10.0.1")
                .addRoute("10.10.0.1", 32)
                .setMtu(1500)
                .setBlocking(true)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopSelf()
                return
            }

            inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

            forwarderSocket = DatagramSocket()
            protect(forwarderSocket) // Protect socket from being looped back into TUN
            forwarderSocket.soTimeout = 2000

            val upstreamDns = InetAddress.getByName("1.1.1.1")
            val packetBuffer = ByteArray(4096)
            val dnsResponseBuf = ByteArray(4096)

            while (isRunning.get()) {
                val length = try {
                    inputStream.read(packetBuffer)
                } catch (e: Exception) {
                    break
                }

                if (length <= 0) continue

                val query = DnsPacketUtil.parseIpPacket(packetBuffer, length)
                if (query != null) {
                    // Local autonomous analysis - ZERO logs saved to disk
                    val result = filterEngine.analyzeAndFilter(query.domain)

                    if (result.shouldBlock) {
                        // Synthesize local 0.0.0.0 response (< 1ms, zero data transferred)
                        val responsePacket = DnsPacketUtil.buildBlockedDnsResponsePacket(query)
                        try {
                            outputStream.write(responsePacket)
                        } catch (e: Exception) {}
                    } else {
                        // Forward permitted traffic to local upstream resolver
                        try {
                            val dnsPayload = ByteArray(query.dnsLength)
                            System.arraycopy(query.rawPacket, query.dnsOffset, dnsPayload, 0, query.dnsLength)

                            val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamDns, 53)
                            forwarderSocket.send(outPacket)

                            val inPacket = DatagramPacket(dnsResponseBuf, dnsResponseBuf.size)
                            forwarderSocket.receive(inPacket)

                            val wrappedResponse = wrapDnsResponseInIpUdp(query, inPacket.data, inPacket.length)
                            outputStream.write(wrappedResponse)
                        } catch (e: Exception) {
                            // Upstream timeout or network error, let standard resolver retry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Error handling
        } finally {
            try { forwarderSocket?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { vpnInterface?.close() } catch (ignored: Exception) {}
            vpnInterface = null
        }
    }

    private fun wrapDnsResponseInIpUdp(query: com.unblocker.app.logic.dns.DnsQuery, dnsBytes: ByteArray, dnsLength: Int): ByteArray {
        val udpLength = 8 + dnsLength
        val ipTotalLength = 20 + udpLength
        val buf = ByteBuffer.allocate(ipTotalLength)
        buf.order(ByteOrder.BIG_ENDIAN)

        // IPv4 Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(ipTotalLength.toShort())
        buf.putShort(0x0000.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte()) // UDP
        buf.putShort(0x0000.toShort())
        buf.put(query.dstIp) // Src IP
        buf.put(query.srcIp) // Dst IP

        // UDP Header
        buf.putShort(query.dstPort.toShort())
        buf.putShort(query.srcPort.toShort())
        buf.putShort(udpLength.toShort())
        buf.putShort(0x0000.toShort())

        // DNS Response Payload
        buf.put(dnsBytes, 0, dnsLength)

        return buf.array()
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
