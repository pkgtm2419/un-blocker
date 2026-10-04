package com.unblocker.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.unblocker.vpn.builder.VpnConfiguration
import com.unblocker.vpn.parser.DnsPacketParser
import com.unblocker.vpn.resolver.DnsForwarder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import javax.inject.Inject

/**
 * Core VPN engine service for the `:vpn-engine` module.
 *
 * ## Lifecycle
 * - [onStartCommand] → ACTION_START: establishes the tun interface and launches the
 *   packet-processing coroutine on [Dispatchers.IO].
 * - [onStartCommand] → ACTION_STOP: tears down the tunnel and stops the foreground service.
 * - [onRevoke]: called by Android when the user manually revokes VPN permission.
 * - [onDestroy]: final cleanup.
 *
 * ## Packet-Processing Pipeline
 * ```
 * tun fd (FileInputStream)
 *   → IpPacket.parse()          // identify IPv4 / IPv6
 *   → UdpPacket.parse()         // identify UDP
 *   → DnsPacketParser.extractDnsQuery()   // decode DNS wire-format labels
 *   → bloomFilterCheck (via injected callback)
 *     ├── BLOCKED → DnsPacketParser.buildBlockedDnsResponse()  // synthesize NXDOMAIN
 *     └── ALLOWED → DnsForwarder.forwardToUpstream()           // real resolver
 *   → tun fd (FileOutputStream)  // write response back to app
 * ```
 *
 * ## Zero-Block Logging
 * Every DNS request (blocked or allowed) is logged asynchronously on [Dispatchers.IO]
 * via the injected [logDnsRequest] lambda so the hot network path is never stalled by I/O.
 *
 * ## Thread Safety
 * [isRunning] is guarded by a `@Volatile` flag.  The [serviceScope] uses [SupervisorJob]
 * so an exception in one packet does not cancel the entire coroutine hierarchy.
 */
@AndroidEntryPoint
class UnblockerVpnService : VpnService() {

    // ─── Injected dependencies ────────────────────────────────────────────────

    /**
     * Checks whether a [domain] is blocked by the in-memory Bloom Filter.
     * Injected so the data-store module stays decoupled from the VPN module.
     * This lambda MUST be non-blocking (returns immediately from an AtomicReference read).
     */
    @Inject lateinit var isBlocked: (domain: String) -> Boolean

    /**
     * Asynchronously records each DNS request to the local Room database.
     * Called inside a fire-and-forget coroutine so the packet loop is never blocked.
     */
    @Inject lateinit var logDnsRequest: suspend (domain: String, blocked: Boolean) -> Unit

    /**
     * Initializes the Bloom Filter from the database. Must be called before
     * processing packets to ensure rules are loaded into memory.
     */
    @Inject lateinit var initFilter: suspend () -> Unit

    // ─── Internal state ───────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private var packetJob: Job? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                START_NOT_STICKY
            }
            else -> {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                startTunnel()
                START_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── Tunnel Management ────────────────────────────────────────────────────

    private fun startTunnel() {
        if (isRunning) return
        isRunning = true

        VpnStateHolder.transitionTo(VpnStateHolder.State.STARTING)

        val dnsForwarder = DnsForwarder(
            protectSocket = { socket: DatagramSocket -> protect(socket) },
            protectTcp = { socket: Socket -> protect(socket) }
        )

        val config = VpnConfiguration(service = this)
        vpnInterface = config.establish()

        if (vpnInterface == null) {
            VpnStateHolder.transitionTo(VpnStateHolder.State.ERROR)
            stopSelf()
            return
        }

        VpnStateHolder.transitionTo(VpnStateHolder.State.RUNNING)

        packetJob = serviceScope.launch {
            initFilter()
            processPackets(dnsForwarder)
        }
    }

    private fun stopTunnel() {
        if (!isRunning) return
        isRunning = false
        VpnStateHolder.transitionTo(VpnStateHolder.State.STOPPING)
        packetJob?.cancel()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        VpnStateHolder.transitionTo(VpnStateHolder.State.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ─── Packet-Processing Loop ───────────────────────────────────────────────

    /**
     * Blocking I/O loop that reads raw IP packets from the tun interface, classifies DNS
     * queries via the Bloom Filter, and writes responses back.
     *
     * Runs on [Dispatchers.IO]; cooperative cancellation via [isActive].
     */
    private suspend fun processPackets(forwarder: DnsForwarder) = withContext(Dispatchers.IO) {
        val fd = vpnInterface?.fileDescriptor ?: return@withContext
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buf = ByteArray(32767)

        while (isActive && isRunning) {
            val length = runCatching { input.read(buf) }.getOrDefault(-1)
            if (length <= 0) continue

            val dnsQuery = DnsPacketParser.extractDnsQuery(buf, length)

            if (dnsQuery != null) {
                val blocked = isBlocked(dnsQuery.domain)

                // Fire-and-forget async DB log — must not block this thread
                launch(Dispatchers.IO) {
                    runCatching { logDnsRequest(dnsQuery.domain, blocked) }
                }

                if (blocked) {
                    // Synthesize and return NXDOMAIN response immediately
                    val nxdomain = DnsPacketParser.buildBlockedDnsResponse(dnsQuery)
                    runCatching { output.write(nxdomain) }
                } else {
                    // Extract raw DNS payload and forward upstream
                    val resolved = forwarder.forwardToUpstream(extractDnsPayload(buf, length))
                    if (resolved != null) {
                        val wrapped = DnsPacketParser.buildForwardedResponse(dnsQuery, resolved)
                        runCatching { output.write(wrapped) }
                    }
                }
            } else {
                // Non-DNS traffic — pass through directly (TCP, ICMP, etc.)
                runCatching { output.write(buf, 0, length) }
            }
        }
    }

    /** Extracts the raw DNS wire-format payload from an IP+UDP packet. */
    private fun extractDnsPayload(buf: ByteArray, length: Int): ByteArray {
        // IPv4: skip IP header (IHL*4) + 8-byte UDP header
        val ihl = (buf[0].toInt() and 0x0F) * 4
        val dnsStart = ihl + 8
        return if (dnsStart < length) buf.copyOfRange(dnsStart, length) else ByteArray(0)
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, UnblockerVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Un-Blocker Active")
            .setContentText("On-device DNS protection running. No telemetry.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Protection Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Un-Blocker's local DNS protection is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    // ─── Companion ────────────────────────────────────────────────────────────

    companion object {
        const val ACTION_START = "com.unblocker.vpn.ACTION_START"
        const val ACTION_STOP  = "com.unblocker.vpn.ACTION_STOP"

        private const val CHANNEL_ID      = "unblocker_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, UnblockerVpnService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, UnblockerVpnService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
