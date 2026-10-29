package com.unblocker.app.services

import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TunResponseWriterTest {

    @Before
    fun setUp() {
        VpnDiagnostics.resetForTesting()
    }

    @Test
    fun testConcurrentEnqueueWritesToOutputStream() {
        val session = VpnSessionState()
        val owner = session.begin()
        session.established(owner)
        val run = TunnelRun(owner)

        val outputStream = ByteArrayOutputStream()
        val writer = TunResponseWriter(run, session, outputStream, queueCapacity = 500)
        run.register(writer)

        val threadCount = 8
        val packetsPerThread = 50
        val packetSize = 32
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until packetsPerThread) {
                        val packet = ByteArray(packetSize) { (t + 1).toByte() }
                        assertTrue(writer.offer(packet))
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("All producer threads should finish within timeout", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        // Wait briefly for single writer thread to drain queue
        val deadline = System.currentTimeMillis() + 3000
        while (writer.writtenPackets < (threadCount * packetsPerThread) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        assertEquals((threadCount * packetsPerThread).toLong(), writer.writtenPackets)
        assertEquals((threadCount * packetsPerThread * packetSize), outputStream.size())

        writer.close()
        run.close()
    }

    @Test
    fun testQueueOverflowDropsPacketsAndRecordsDiagnosticsWithoutBlocking() {
        val session = VpnSessionState()
        val owner = session.begin()
        session.established(owner)
        val run = TunnelRun(owner)

        // Stalled stream to simulate extreme backpressure
        val blockedStream = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray) {
                // Sleep to simulate very slow TUN sink
                Thread.sleep(500)
                super.write(b)
            }
        }

        val smallCapacity = 10
        val writer = TunResponseWriter(run, session, blockedStream, queueCapacity = smallCapacity)
        run.register(writer)

        var offered = 0
        var rejected = 0
        for (i in 0 until 50) {
            val packet = ByteArray(16) { 0x5A }
            if (writer.offer(packet)) {
                offered++
            } else {
                rejected++
            }
        }

        assertTrue("Some packets should be rejected under backpressure", rejected > 0)
        assertEquals(rejected.toLong(), writer.droppedPackets)
        assertTrue("Diagnostics should capture write/drop events", VpnDiagnostics.tunWriteErrorCount > 0)

        writer.close()
        run.close()
    }

    @Test
    fun testOwnershipRevocationPreventsFurtherWrites() {
        val session = VpnSessionState()
        val owner = session.begin()
        session.established(owner)
        val run = TunnelRun(owner)

        val outputStream = ByteArrayOutputStream()
        val writer = TunResponseWriter(run, session, outputStream, queueCapacity = 64)
        run.register(writer)

        // Revoke ownership by beginning a new generation
        val newOwner = session.begin()
        assertFalse("Session should no longer own previous run", session.owns(owner))
        assertTrue("Session should own new generation", session.owns(newOwner))

        val packet = ByteArray(20) { 0x11 }
        val accepted = writer.offer(packet)
        assertFalse("Offer must be rejected when session does not own run", accepted)

        writer.close()
        run.close()
    }
}
