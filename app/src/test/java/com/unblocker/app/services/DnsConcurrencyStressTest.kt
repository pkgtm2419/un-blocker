package com.unblocker.app.services

import com.unblocker.app.logic.dns.DnsQuery
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsConcurrencyStressTest {

    private val loopback = InetAddress.getByName("127.0.0.1")

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
            out.write(queryBytes, 0, 2)
            out.writeShort(0x8180)
            out.writeShort(1)
            out.writeShort(1)
            out.writeShort(0)
            out.writeShort(0)
            val questionLen = queryBytes.size - 12
            out.write(queryBytes, 12, questionLen)
            out.writeShort(0xc00c)
            out.writeShort(1)
            out.writeShort(1)
            out.writeInt(60)
            out.writeShort(4)
            out.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
        }
        return bos.toByteArray()
    }

    private class StressDnsServer(address: InetAddress) : Closeable {
        private val socket = DatagramSocket(0, address)
        val port: Int = socket.localPort
        private val running = java.util.concurrent.atomic.AtomicBoolean(true)
        private val worker = Thread {
            val buf = ByteArray(4096)
            while (running.get()) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val req = packet.data.copyOf(packet.length)
                    val resp = buildResponse(req)
                    socket.send(DatagramPacket(resp, resp.size, packet.address, packet.port))
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun buildResponse(queryBytes: ByteArray): ByteArray {
            val bos = ByteArrayOutputStream()
            DataOutputStream(bos).use { out ->
                out.write(queryBytes, 0, 2)
                out.writeShort(0x8180)
                out.writeShort(1)
                out.writeShort(1)
                out.writeShort(0)
                out.writeShort(0)
                val questionLen = queryBytes.size - 12
                out.write(queryBytes, 12, questionLen)
                out.writeShort(0xc00c)
                out.writeShort(1)
                out.writeShort(1)
                out.writeInt(60)
                out.writeShort(4)
                out.write(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
            }
            return bos.toByteArray()
        }

        override fun close() {
            running.set(false)
            socket.close()
            worker.join(1000)
        }
    }

    @Test
    fun testTenConcurrentThreadsNoRejectionOrTimeouts() {
        runConcurrencyTest(threadCount = 10, totalQueries = 100)
    }

    @Test
    fun testTwentyConcurrentThreadsNoRejectionOrTimeouts() {
        runConcurrencyTest(threadCount = 20, totalQueries = 200)
    }

    @Test
    fun testFiftyConcurrentBurstsNoSaturation() {
        runConcurrencyTest(threadCount = 50, totalQueries = 250)
    }

    private fun runConcurrencyTest(threadCount: Int, totalQueries: Int) {
        StressDnsServer(loopback).use { server ->
            val run = TunnelRun(100)
            val client = DnsUpstreamClient(
                protectDatagramSocket = { true },
                protectTcpSocket = { true },
                port = server.port,
                udpTimeoutMillis = 500,
                tcpConnectTimeoutMillis = 750,
                tcpReadTimeoutMillis = 750
            )

            val clientExecutor = Executors.newFixedThreadPool(threadCount)
            val startGate = CountDownLatch(1)
            val finishGate = CountDownLatch(totalQueries)
            val successCount = AtomicInteger(0)
            val rejectedCount = AtomicInteger(0)
            val errorCount = AtomicInteger(0)

            for (i in 0 until totalQueries) {
                clientExecutor.submit {
                    startGate.await()
                    val domain = "host-$i.test.local"
                    val txId = (i % 65535).toShort()
                    val queryBytes = buildDnsQueryPayload(domain, txId.toInt())
                    val query = DnsQuery(
                        transactionId = txId,
                        domain = domain,
                        queryType = 1,
                        queryClass = 1,
                        rawPacket = queryBytes,
                        dnsOffset = 0,
                        dnsLength = queryBytes.size,
                        srcIp = byteArrayOf(10, 0, 0, 2),
                        dstIp = byteArrayOf(10, 0, 0, 1),
                        srcPort = 50000 + (i % 10000),
                        dstPort = 53
                    )

                    try {
                        run.forwarding.execute {
                            try {
                                val res = client.resolve(run, query, queryBytes, listOf(loopback))
                                if (res != null) {
                                    successCount.incrementAndGet()
                                } else {
                                    errorCount.incrementAndGet()
                                }
                            } catch (_: Exception) {
                                errorCount.incrementAndGet()
                            } finally {
                                finishGate.countDown()
                            }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        rejectedCount.incrementAndGet()
                        finishGate.countDown()
                    }
                }
            }

            startGate.countDown()
            val finished = finishGate.await(15, TimeUnit.SECONDS)
            clientExecutor.shutdownNow()
            run.close()

            assertTrue("All queries should complete within timeout", finished)
            assertEquals("Rejected execution count must be 0", 0, rejectedCount.get())
            assertEquals("Error count must be 0", 0, errorCount.get())
            assertEquals("All queries must succeed", totalQueries, successCount.get())
        }
    }
}
