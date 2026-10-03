package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsPacketUtil
import com.unblocker.app.logic.dns.DnsQuery
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsUpstreamClientTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val request = dnsQuery("ads.example", 0x1234)
    private val query = DnsQuery(
        transactionId = 0x1234,
        domain = "ads.example",
        queryType = DnsPacketUtil.TYPE_A,
        queryClass = DnsPacketUtil.CLASS_IN,
        rawPacket = ByteArray(0),
        dnsOffset = 0,
        dnsLength = request.size,
        srcIp = byteArrayOf(10, 10, 0, 2),
        dstIp = byteArrayOf(10, 10, 0, 1),
        srcPort = 45_678,
        dstPort = 53
    )

    @Test fun queuedOldEpochNeverSendsUdpRequest() {
        TestDnsServer(loopback).use { server ->
            val registry=ResolverRegistry<String> {}
            registry.update("wifi",listOf(InetAddress.getByName("192.0.2.1")))
            val old=registry.snapshot()
            registry.remove("wifi")
            val run=TunnelRun(1)
            try {
                assertNull(client(server).resolve(run,query,request,listOf(loopback),
                    outboundGuard={ action -> registry.useCurrent(old,action) }))
                server.assertNoUdpRequest()
                server.assertNoTcpConnection()
            } finally {run.close()}
        }
    }

    @Test fun replacementAfterTruncationSuppressesTcpFallback() {
        TestDnsServer(loopback).use { server ->
            val registry=ResolverRegistry<String> {}
            registry.update("wifi",listOf(InetAddress.getByName("192.0.2.1")))
            val old=registry.snapshot()
            val response=server.serveUdpOnce {
                registry.remove("wifi")
                dnsResponse("ads.example",flags=0x8380,includeAnswer=false)
            }
            val run=TunnelRun(1)
            try {
                assertNull(client(server).resolve(run,query,request,listOf(loopback),
                    outboundGuard={ action -> registry.useCurrent(old,action) }))
                response.get(2,TimeUnit.SECONDS)
                server.assertNoTcpConnection()
            } finally {run.close()}
        }
    }

    @Test fun returnsValidatedUdpResponseWithoutOpeningTcp() {
        TestDnsServer(loopback).use { server ->
            val expected = dnsResponse("ads.example")
            val udp = server.serveUdpOnce { expected }
            val run = TunnelRun(1)
            try {
                val result = client(server).resolve(run, query, request, listOf(loopback))

                assertArrayEquals(expected, result!!.bytes)
                assertEquals(120L, result.metadata.minPositiveTtlSeconds)
                udp.get(2, TimeUnit.SECONDS)
                server.assertNoTcpConnection()
            } finally { run.close() }
        }
    }

    @Test fun retriesTruncatedUdpResponseOverLengthPrefixedTcp() {
        TestDnsServer(loopback).use { server ->
            val truncated = dnsResponse("ads.example", flags = 0x8380, includeAnswer = false)
            val expected = dnsResponse("ads.example", answerTtl = 45)
            server.serveUdpOnce { truncated }
            val tcpRequest = server.serveTcpOnce { _, output ->
                output.writeShort(expected.size)
                output.write(expected)
            }
            val run = TunnelRun(1)
            try {
                val result = client(server).resolve(run, query, request, listOf(loopback))

                assertArrayEquals(expected, result!!.bytes)
                assertEquals(45L, result.metadata.minPositiveTtlSeconds)
                assertArrayEquals(request, tcpRequest.get(2, TimeUnit.SECONDS))
            } finally { run.close() }
        }
    }

    @Test fun rejectsSameTransactionIdWithMismatchedQuestion() {
        TestDnsServer(loopback).use { server ->
            server.serveUdpOnce { dnsResponse("other.example") }
            val run = TunnelRun(1)
            try {
                assertNull(client(server).resolve(run, query, request, listOf(loopback)))
                server.assertNoTcpConnection()
            } finally { run.close() }
        }
    }

    @Test fun rejectsTcpFrameLargerThanConfiguredBound() {
        TestDnsServer(loopback).use { server ->
            server.serveUdpOnce { dnsResponse("ads.example", flags = 0x8380, includeAnswer = false) }
            server.serveTcpOnce { _, output -> output.writeShort(65) }
            val run = TunnelRun(1)
            try {
                assertNull(client(server, maxDnsMessageBytes = 64)
                    .resolve(run, query, request, listOf(loopback)))
            } finally { run.close() }
        }
    }

    @Test fun rejectsShortTcpFrame() {
        TestDnsServer(loopback).use { server ->
            val expected = dnsResponse("ads.example")
            server.serveUdpOnce { dnsResponse("ads.example", flags = 0x8380, includeAnswer = false) }
            server.serveTcpOnce { _, output ->
                output.writeShort(expected.size)
                output.write(expected, 0, 5)
            }
            val run = TunnelRun(1)
            try {
                assertNull(client(server).resolve(run, query, request, listOf(loopback)))
            } finally { run.close() }
        }
    }

    @Test fun rejectsTcpResponseTooLargeForIpv4TunPacket() {
        TestDnsServer(loopback).use { server ->
            server.serveUdpOnce { dnsResponse("ads.example", flags = 0x8380, includeAnswer = false) }
            val oversized = dnsResponseWithRdataSize(65_467) // 65,508 DNS bytes; IPv4 maximum is 65,507.
            server.serveTcpOnce { _, output ->
                output.writeShort(oversized.size)
                output.write(oversized)
            }
            val run = TunnelRun(1)
            try {
                assertNull(client(server).resolve(run, query, request, listOf(loopback)))
            } finally { run.close() }
        }
    }

    @Test fun closingRunCancelsPendingTcpReadWithoutLateResponse() {
        TestDnsServer(loopback).use { server ->
            val tcpAccepted = CountDownLatch(1)
            val releaseServer = CountDownLatch(1)
            server.serveUdpOnce { dnsResponse("ads.example", flags = 0x8380, includeAnswer = false) }
            server.serveTcpOnce { _, _ ->
                tcpAccepted.countDown()
                releaseServer.await(3, TimeUnit.SECONDS)
            }
            val run = TunnelRun(1)
            val caller = Executors.newSingleThreadExecutor()
            try {
                val result = caller.submit<ValidatedDnsResponse?> {
                    client(server, tcpReadTimeoutMillis = 5_000)
                        .resolve(run, query, request, listOf(loopback))
                }
                assertTrue(tcpAccepted.await(2, TimeUnit.SECONDS))

                run.close()

                assertNull(result.get(2, TimeUnit.SECONDS))
                assertFalse(run.running.get())
            } finally {
                releaseServer.countDown()
                caller.shutdownNow()
                run.close()
            }
        }
    }

    private fun client(
        server: TestDnsServer,
        maxDnsMessageBytes: Int = 0xffff,
        tcpReadTimeoutMillis: Int = 1_000
    ) = DnsUpstreamClient(
        protectDatagramSocket = { true },
        protectTcpSocket = { true },
        port = server.port,
        udpTimeoutMillis = 500,
        tcpConnectTimeoutMillis = 500,
        tcpReadTimeoutMillis = tcpReadTimeoutMillis,
        maxDnsMessageBytes = maxDnsMessageBytes
    )

    private class TestDnsServer(address: InetAddress) : Closeable {
        private val executor = Executors.newCachedThreadPool()
        private val tcpServer = ServerSocket(0, 8, address)
        private val udpServer = DatagramSocket(tcpServer.localPort, address)
        val port: Int = tcpServer.localPort

        fun serveUdpOnce(response: (ByteArray) -> ByteArray): Future<*> = executor.submit {
            val packet = DatagramPacket(ByteArray(4096), 4096)
            udpServer.receive(packet)
            val request = packet.data.copyOf(packet.length)
            val reply = response(request)
            udpServer.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
        }

        fun serveTcpOnce(handler: (ByteArray, DataOutputStream) -> Unit): Future<ByteArray> =
            executor.submit<ByteArray> {
                tcpServer.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val requestLength = input.readUnsignedShort()
                    val request = ByteArray(requestLength)
                    input.readFully(request)
                    DataOutputStream(socket.getOutputStream()).use { output ->
                        handler(request, output)
                        output.flush()
                    }
                    request
                }
            }

        fun assertNoTcpConnection() {
            tcpServer.soTimeout = 250
            assertThrows(SocketTimeoutException::class.java) { tcpServer.accept().close() }
        }

        fun assertNoUdpRequest() {
            udpServer.soTimeout=250
            assertThrows(SocketTimeoutException::class.java) {
                udpServer.receive(DatagramPacket(ByteArray(512),512))
            }
        }

        override fun close() {
            udpServer.close()
            tcpServer.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun dnsQuery(domain: String, transactionId: Int): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(transactionId)
            data.writeShort(0x0100)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.writeShort(0)
            data.write(encodeName(domain))
            data.writeShort(1)
            data.writeShort(1)
        }
        return output.toByteArray()
    }

    private fun dnsResponse(
        domain: String,
        flags: Int = 0x8180,
        answerTtl: Int = 120,
        includeAnswer: Boolean = true
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(0x1234)
            data.writeShort(flags)
            data.writeShort(1)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.write(encodeName(domain))
            data.writeShort(1)
            data.writeShort(1)
            if (includeAnswer) {
                data.writeShort(0xc00c)
                data.writeShort(1)
                data.writeShort(1)
                data.writeInt(answerTtl)
                data.writeShort(4)
                data.write(byteArrayOf(192.toByte(), 0, 2, 1))
            }
        }
        return output.toByteArray()
    }

    private fun dnsResponseWithRdataSize(rdataSize: Int): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeShort(0x1234)
            data.writeShort(0x8180)
            data.writeShort(1)
            data.writeShort(1)
            data.writeShort(0)
            data.writeShort(0)
            data.write(encodeName("ads.example"))
            data.writeShort(1)
            data.writeShort(1)
            data.writeShort(0xc00c)
            data.writeShort(16)
            data.writeShort(1)
            data.writeInt(120)
            data.writeShort(rdataSize)
            data.write(ByteArray(rdataSize))
        }
        return output.toByteArray()
    }

    private fun encodeName(domain: String): ByteArray {
        val output = ByteArrayOutputStream()
        domain.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            output.write(bytes.size)
            output.write(bytes)
        }
        output.write(0)
        return output.toByteArray()
    }
}
