package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import com.unblocker.app.logic.dns.TcpDnsResponder
import com.unblocker.vpn.builder.TrappedDnsServers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DnsUpstreamClientSecurityTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test
    fun randomizesUpstreamTransactionIdAndRestoresClientTxId() {
        val clientTxId: Short = 0x5a5a.toShort()
        val upstreamTxId: Short = 0x3b3b.toShort()
        val capturedUpstreamTxId = AtomicInteger(-1)

        TestEchoDnsServer(loopback).use { server ->
            server.serveUdpEcho { req ->
                val id = ((req[0].toInt() and 0xFF) shl 8) or (req[1].toInt() and 0xFF)
                capturedUpstreamTxId.set(id)
                buildResponseForQuery(req)
            }

            val client = DnsUpstreamClient(
                protectDatagramSocket = { true },
                protectTcpSocket = { true },
                port = server.port,
                udpTimeoutMillis = 500,
                tcpConnectTimeoutMillis = 500,
                tcpReadTimeoutMillis = 500,
                txIdGenerator = { upstreamTxId }
            )

            val queryBytes = buildDnsQueryPayload("secure.example", clientTxId.toInt())
            val query = DnsQuery(
                transactionId = clientTxId,
                domain = "secure.example",
                queryType = 1,
                queryClass = 1,
                rawPacket = queryBytes,
                dnsOffset = 0,
                dnsLength = queryBytes.size,
                srcIp = byteArrayOf(10, 10, 0, 2),
                dstIp = byteArrayOf(10, 10, 0, 1),
                srcPort = 53000,
                dstPort = 53
            )

            val run = TunnelRun(1)
            try {
                val response = client.resolve(run, query, queryBytes, listOf(loopback))
                assertNotNull(response)

                // 1. Upstream wire packet had the randomized TxID
                assertEquals(upstreamTxId.toInt() and 0xffff, capturedUpstreamTxId.get())

                // 2. Response bytes returned to caller had client's TxID restored
                val returnedTxId = ((response!!.bytes[0].toInt() and 0xFF) shl 8) or (response.bytes[1].toInt() and 0xFF)
                assertEquals(clientTxId.toInt() and 0xffff, returnedTxId)

                // 3. Metadata transaction ID matched client's TxID
                assertEquals(clientTxId.toInt() and 0xffff, response.metadata.transactionId)
            } finally {
                run.close()
            }
        }
    }

    @Test
    fun rejectsMismatchedUpstreamTransactionIdAntiSpoofing() {
        val clientTxId: Short = 0x5a5a.toShort()
        val upstreamTxId: Short = 0x3b3b.toShort()
        val attackerSpoofedTxId: Short = 0x1111.toShort()

        TestEchoDnsServer(loopback).use { server ->
            // Server (or attacker) sends back a response with mismatched ID
            server.serveUdpEcho { req ->
                val resp = buildResponseForQuery(req)
                resp[0] = ((attackerSpoofedTxId.toInt() ushr 8) and 0xff).toByte()
                resp[1] = (attackerSpoofedTxId.toInt() and 0xff).toByte()
                resp
            }

            val client = DnsUpstreamClient(
                protectDatagramSocket = { true },
                protectTcpSocket = { true },
                port = server.port,
                udpTimeoutMillis = 500,
                tcpConnectTimeoutMillis = 500,
                tcpReadTimeoutMillis = 500,
                txIdGenerator = { upstreamTxId }
            )

            val queryBytes = buildDnsQueryPayload("spoof.example", clientTxId.toInt())
            val query = DnsQuery(
                transactionId = clientTxId,
                domain = "spoof.example",
                queryType = 1,
                queryClass = 1,
                rawPacket = queryBytes,
                dnsOffset = 0,
                dnsLength = queryBytes.size,
                srcIp = byteArrayOf(10, 10, 0, 2),
                dstIp = byteArrayOf(10, 10, 0, 1),
                srcPort = 53000,
                dstPort = 53
            )

            val run = TunnelRun(1)
            try {
                // Must be rejected as null because transaction ID does not match
                val response = client.resolve(run, query, queryBytes, listOf(loopback))
                assertNull("Spoofed TxID must be rejected by upstream client", response)
            } finally {
                run.close()
            }
        }
    }

    @Test
    fun trappedPublicDnsAddressesRecognized() {
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(8, 8, 8, 8), isIpv6 = false))
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(8, 8, 4, 4), isIpv6 = false))
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(1, 1, 1, 1), isIpv6 = false))
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(1, 0, 0, 1), isIpv6 = false))
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(9, 9, 9, 9), isIpv6 = false))
        assertTrue(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(76, 76, 2, 0), isIpv6 = false))

        assertFalse(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(192.toByte(), 168.toByte(), 1, 1), isIpv6 = false))
        assertFalse(TcpDnsResponder.isTrappedPublicDns(byteArrayOf(10, 0, 0, 1), isIpv6 = false))

        assertTrue(TrappedDnsServers.IPV4_TRAPPED.contains("8.8.8.8"))
        assertTrue(TrappedDnsServers.IPV4_TRAPPED.contains("1.1.1.1"))
        assertTrue(TrappedDnsServers.IPV6_TRAPPED.contains("2001:4860:4860::8888"))
    }

    private fun buildDnsQueryPayload(domain: String, txId: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            out.writeShort(txId)
            out.writeShort(0x0100)
            out.writeShort(1)
            out.writeShort(0)
            out.writeShort(0)
            out.writeShort(0)
            for (label in domain.split(".")) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.writeByte(bytes.size)
                out.write(bytes)
            }
            out.writeByte(0)
            out.writeShort(1)
            out.writeShort(1)
        }
        return bos.toByteArray()
    }

    private fun buildResponseForQuery(queryBytes: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { out ->
            // Copy TxID from query
            out.write(queryBytes, 0, 2)
            out.writeShort(0x8180) // Flags: standard response, no error
            out.writeShort(1)      // 1 question
            out.writeShort(1)      // 1 answer
            out.writeShort(0)
            out.writeShort(0)
            // Copy question section
            val questionLen = queryBytes.size - 12
            out.write(queryBytes, 12, questionLen)
            // Answer: pointer to question name (0xc00c), type A (1), class IN (1), TTL (60), length (4), IP 93.184.216.34
            out.writeShort(0xc00c)
            out.writeShort(1)
            out.writeShort(1)
            out.writeInt(60)
            out.writeShort(4)
            out.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        }
        return bos.toByteArray()
    }

    private class TestEchoDnsServer(address: InetAddress) : Closeable {
        private val executor = Executors.newCachedThreadPool()
        private val udpServer = DatagramSocket(0, address)
        val port: Int = udpServer.localPort

        fun serveUdpEcho(handler: (ByteArray) -> ByteArray): Future<*> = executor.submit {
            val buf = ByteArray(4096)
            val packet = DatagramPacket(buf, buf.size)
            udpServer.receive(packet)
            val request = packet.data.copyOf(packet.length)
            val reply = handler(request)
            udpServer.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
        }

        override fun close() {
            udpServer.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}
