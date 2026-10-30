package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsQuery
import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsResponseMetadata
import com.unblocker.app.logic.dns.DnsResponseValidator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

internal data class ValidatedDnsResponse(
    val bytes: ByteArray,
    val metadata: DnsResponseMetadata
)

internal class DnsUpstreamClient(
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    private val protectTcpSocket: (Socket) -> Boolean,
    private val port: Int = 53,
    private val udpTimeoutMillis: Int = 700,
    private val tcpConnectTimeoutMillis: Int = 1_000,
    private val tcpReadTimeoutMillis: Int = 1_000,
    private val maxDnsMessageBytes: Int = 0xffff,
    private val txIdGenerator: () -> Short = { secureRandom.nextInt(0x10000).toShort() }
) {
    companion object {
        private val secureRandom = java.security.SecureRandom()
    }

    init {
        require(port in 1..0xffff)
        require(udpTimeoutMillis > 0 && tcpConnectTimeoutMillis > 0 && tcpReadTimeoutMillis > 0)
        require(maxDnsMessageBytes in 12..0xffff)
    }

    @Volatile
    private var preferredResolver: InetAddress? = null

    fun resolve(
        run: TunnelRun,
        query: DnsQuery,
        dnsPayload: ByteArray,
        resolvers: List<InetAddress>,
        outboundGuard: ((()->Unit)->Boolean) = { action -> action();true }
    ): ValidatedDnsResponse? {
        if (!run.running.get() || dnsPayload.isEmpty() || dnsPayload.size > 0xffff || resolvers.isEmpty()) return null
        if (dnsPayload.size < 12) return null
        val responseLimit = minOf(maxDnsMessageBytes, DnsPacketUtil.maxDnsPayloadLength(query))

        // Cryptographically randomize outbound upstream transaction ID (SEC-02, RFC 5452 anti-spoofing)
        val upstreamTxId = txIdGenerator()
        val upstreamPayload = dnsPayload.clone()
        upstreamPayload[0] = ((upstreamTxId.toInt() ushr 8) and 0xff).toByte()
        upstreamPayload[1] = (upstreamTxId.toInt() and 0xff).toByte()
        val upstreamQuery = query.copy(transactionId = upstreamTxId)

        // Prioritize last known responsive resolver to eliminate redundant timeout delays
        val orderedResolvers = preferredResolver?.let { pref ->
            if (pref in resolvers) listOf(pref) + resolvers.filter { it != pref } else resolvers
        } ?: resolvers

        for (resolver in orderedResolvers) {
            if (!run.running.get() || Thread.currentThread().isInterrupted) return null
            val udpResponse = resolveUdp(run, upstreamQuery, upstreamPayload, resolver, responseLimit, outboundGuard)
            if (udpResponse != null) {
                if (!udpResponse.metadata.truncated) {
                    preferredResolver = resolver
                    return restoreClientTxId(udpResponse, query.transactionId)
                }
                val tcpResponse = resolveTcp(run, upstreamQuery, upstreamPayload, resolver, responseLimit, outboundGuard)
                if (tcpResponse != null) {
                    preferredResolver = resolver
                    return restoreClientTxId(tcpResponse, query.transactionId)
                }
            } else if (resolver == preferredResolver) {
                // Preferred resolver failed to respond; clear preference to prevent timeout cascades
                preferredResolver = null
            }
        }
        preferredResolver = null
        return null
    }

    private fun restoreClientTxId(response: ValidatedDnsResponse, clientTxId: Short): ValidatedDnsResponse {
        val clientTxIdInt = clientTxId.toInt() and 0xffff
        val restoredBytes = response.bytes.clone()
        if (restoredBytes.size >= 2) {
            restoredBytes[0] = ((clientTxIdInt ushr 8) and 0xff).toByte()
            restoredBytes[1] = (clientTxIdInt and 0xff).toByte()
        }
        val restoredMetadata = response.metadata.copy(transactionId = clientTxIdInt)
        return ValidatedDnsResponse(restoredBytes, restoredMetadata)
    }

    fun resetPreferredResolver() {
        preferredResolver = null
    }

    private fun resolveUdp(
        run: TunnelRun,
        query: DnsQuery,
        dnsPayload: ByteArray,
        resolver: InetAddress,
        responseLimit: Int,
        outboundGuard: ((()->Unit)->Boolean)
    ): ValidatedDnsResponse? {
        val socket = try { DatagramSocket() } catch (_: Exception) { return null }
        if (!run.register(socket)) return null
        return try {
            if (!run.running.get() || !protectDatagramSocket(socket)) return null
            socket.soTimeout = udpTimeoutMillis
            if (!outboundGuard {
                socket.connect(resolver, port)
                socket.send(DatagramPacket(dnsPayload, dnsPayload.size, resolver, port))
            }) return null

            val buffer = ByteArray(responseLimit)
            val incoming = DatagramPacket(buffer, buffer.size)
            socket.receive(incoming)
            if (!run.running.get()) return null
            val bytes = buffer.copyOf(incoming.length)
            val metadata = DnsResponseValidator.parseAndValidate(bytes, bytes.size, query) ?: return null
            ValidatedDnsResponse(bytes, metadata)
        } catch (_: Exception) {
            null
        } finally {
            if (run.unregister(socket)) runCatching { socket.close() }
        }
    }

    private fun resolveTcp(
        run: TunnelRun,
        query: DnsQuery,
        dnsPayload: ByteArray,
        resolver: InetAddress,
        responseLimit: Int,
        outboundGuard: ((()->Unit)->Boolean)
    ): ValidatedDnsResponse? {
        val socket = Socket()
        if (!run.register(socket)) return null
        return try {
            if (!run.running.get() || !protectTcpSocket(socket)) return null
            socket.soTimeout = tcpReadTimeoutMillis
            if (!outboundGuard {
                socket.connect(InetSocketAddress(resolver, port), tcpConnectTimeoutMillis)
            }) return null
            if (!outboundGuard {
                DataOutputStream(socket.getOutputStream()).apply {
                    writeShort(dnsPayload.size)
                    write(dnsPayload)
                    flush()
                }
            }) return null

            val input = DataInputStream(socket.getInputStream())
            val responseLength = input.readUnsignedShort()
            if (responseLength !in 1..responseLimit) return null
            val bytes = ByteArray(responseLength)
            input.readFully(bytes)
            if (!run.running.get()) return null
            val metadata = DnsResponseValidator.parseAndValidate(bytes, bytes.size, query) ?: return null
            if (metadata.truncated) return null
            ValidatedDnsResponse(bytes, metadata)
        } catch (_: Exception) {
            null
        } finally {
            if (run.unregister(socket)) runCatching { socket.close() }
        }
    }
}
