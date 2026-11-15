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
 * Blocking UDP-first DNS forwarding for the separate library resolver path.
 *
 * Sockets must pass the supplied protection callbacks. A truncated UDP answer triggers a TCP
 * retry for that server regardless of its useTcp field. Replies are not matched to the query's
 * transaction ID, question, or UDP sender here; validation belongs to a caller using this path.
 * The active app uses its separate DnsUpstreamClient and Android-network resolver policy.
 *
 * @param protectSocket Protects a UDP socket from VPN routing; false skips that attempt.
 * @param protectTcp Protects a TCP socket from VPN routing; false skips that attempt.
 * @param servers Resolver configurations tried in order, defaulting to library public resolvers.
 */
@Singleton
class DnsForwarder
    @Inject
    constructor(
        private val protectSocket: (DatagramSocket) -> Boolean,
        private val protectTcp: (Socket) -> Boolean,
        private val servers: List<UpstreamDnsServer> = UpstreamDnsServer.DEFAULTS,
    ) {
        companion object {
            private const val UDP_TIMEOUT_MS = 700
            private const val TCP_CONNECT_TIMEOUT_MS = 1_000
            private const val TCP_READ_TIMEOUT_MS = 1_000
            private const val MAX_RESPONSE_BYTES = 0xFFFF
            private const val DNS_HEADER_BYTES = 12
        }

        /**
         * Tries resolvers sequentially and retries truncated UDP responses using TCP.
         *
         * The call blocks for network I/O. Per-attempt network and protection failures are caught.
         *
         * @param payload Caller-validated DNS wire query without IP or UDP headers.
         * @return Unvalidated DNS response bytes, or null when no resolver attempt succeeds.
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

        private fun tryUdp(
            server: UpstreamDnsServer,
            payload: ByteArray,
        ): ByteArray? {
            val socket = runCatching { DatagramSocket() }.getOrNull() ?: return null
            return try {
                if (!protectSocket(socket)) return null
                socket.soTimeout = UDP_TIMEOUT_MS
                val address = InetAddress.getByName(server.address)
                socket.send(DatagramPacket(payload, payload.size, address, server.port))
                val responseBuffer = ByteArray(MAX_RESPONSE_BYTES)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                responseBuffer.copyOf(response.length)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { socket.close() }
            }
        }

        private fun tryTcp(
            server: UpstreamDnsServer,
            payload: ByteArray,
        ): ByteArray? {
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
                val responseLength = input.readUnsignedShort()
                if (responseLength < DNS_HEADER_BYTES || responseLength > MAX_RESPONSE_BYTES) return null
                ByteArray(responseLength).also { input.readFully(it) }
            } catch (_: Exception) {
                null
            } finally {
                runCatching { socket.close() }
            }
        }
    }
