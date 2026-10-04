package com.unblocker.vpn.resolver

import com.unblocker.vpn.builder.UpstreamDnsServer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards validated DNS query payloads to upstream resolvers over protected sockets.
 *
 * Uses UDP first (lower latency); if the UDP response is truncated (TC flag set),
 * automatically retries over TCP per RFC 1035 §4.2.
 *
 * All sockets are protected via [android.net.VpnService.protect] to route their traffic
 * through the real network interface rather than back into the VPN tunnel.
 *
 * @param protectSocket   Lambda wrapping [android.net.VpnService.protect] for DatagramSocket.
 * @param protectTcp      Lambda wrapping [android.net.VpnService.protect] for TCP Socket.
 * @param servers         Ordered list of upstream DNS servers to try.
 */
@Singleton
class DnsForwarder @Inject constructor(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val protectTcp: (Socket) -> Boolean,
    private val servers: List<UpstreamDnsServer> = UpstreamDnsServer.DEFAULTS
) {
    companion object {
        private const val UDP_TIMEOUT_MS = 700
        private const val TCP_CONNECT_TIMEOUT_MS = 1_000
        private const val TCP_READ_TIMEOUT_MS = 1_000
        private const val MAX_RESPONSE_BYTES = 0xFFFF
    }

    /**
     * Forwards a raw DNS payload (wire-format bytes) to the configured upstream resolvers.
     *
     * @param payload  Raw DNS query bytes (without IP/UDP headers).
     * @return         Raw DNS response bytes, or null if all resolvers failed.
     */
    fun forwardToUpstream(payload: ByteArray): ByteArray? {
        for (server in servers) {
            val resolved = tryUdp(server, payload) ?: continue
            val isTruncated = (resolved.getOrNull(2)?.toInt()?.and(0x02) ?: 0) != 0
            if (!isTruncated) return resolved
            // TC bit set — retry over TCP
            tryTcp(server, payload)?.let { return it }
        }
        return null
    }

    private fun tryUdp(server: UpstreamDnsServer, payload: ByteArray): ByteArray? {
        val socket = runCatching { DatagramSocket() }.getOrNull() ?: return null
        return try {
            if (!protectSocket(socket)) return null
            socket.soTimeout = UDP_TIMEOUT_MS
            val address = InetAddress.getByName(server.address)
            socket.send(DatagramPacket(payload, payload.size, address, server.port))
            val buf = ByteArray(MAX_RESPONSE_BYTES)
            val response = DatagramPacket(buf, buf.size)
            socket.receive(response)
            buf.copyOf(response.length)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun tryTcp(server: UpstreamDnsServer, payload: ByteArray): ByteArray? {
        val socket = Socket()
        return try {
            if (!protectTcp(socket)) return null
            socket.soTimeout = TCP_READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(server.address, server.port), TCP_CONNECT_TIMEOUT_MS)
            DataOutputStream(socket.getOutputStream()).apply {
                writeShort(payload.size)
                write(payload)
                flush()
            }
            val input = DataInputStream(socket.getInputStream())
            val len = input.readUnsignedShort()
            if (len < 12 || len > MAX_RESPONSE_BYTES) return null
            ByteArray(len).also { input.readFully(it) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }
}
