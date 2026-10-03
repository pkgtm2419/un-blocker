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
    private val maxDnsMessageBytes: Int = 0xffff
) {
    init {
        require(port in 1..0xffff)
        require(udpTimeoutMillis > 0 && tcpConnectTimeoutMillis > 0 && tcpReadTimeoutMillis > 0)
        require(maxDnsMessageBytes in 12..0xffff)
    }

    fun resolve(
        run: TunnelRun,
        query: DnsQuery,
        dnsPayload: ByteArray,
        resolvers: List<InetAddress>,
        outboundGuard: ((()->Unit)->Boolean) = { action -> action();true }
    ): ValidatedDnsResponse? {
        if (!run.running.get() || dnsPayload.isEmpty() || dnsPayload.size > 0xffff) return null
        val responseLimit = minOf(maxDnsMessageBytes, DnsPacketUtil.maxDnsPayloadLength(query))
        for (resolver in resolvers) {
            if (!run.running.get() || Thread.currentThread().isInterrupted) return null
            val udpResponse = resolveUdp(run, query, dnsPayload, resolver, responseLimit,outboundGuard) ?: continue
            if (!udpResponse.metadata.truncated) return udpResponse
            val tcpResponse = resolveTcp(run, query, dnsPayload, resolver, responseLimit,outboundGuard)
            if (tcpResponse != null) return tcpResponse
        }
        return null
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
