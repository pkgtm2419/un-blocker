package com.unblocker.app.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnDiagnosticsTest {

    @Before
    fun setUp() {
        VpnDiagnostics.resetForTesting()
    }

    @Test
    fun testCountersIncrementCorrectly() {
        assertEquals(0L, VpnDiagnostics.forwardingRejectedCount)
        assertEquals(0L, VpnDiagnostics.upstreamTimeoutCount)
        assertEquals(0L, VpnDiagnostics.tunReadErrorCount)
        assertEquals(0L, VpnDiagnostics.tunWriteErrorCount)
        assertEquals(0L, VpnDiagnostics.packetProcessingErrorCount)

        VpnDiagnostics.forwardingRejected(queueDepth = 64, resolverCount = 2)
        assertEquals(1L, VpnDiagnostics.forwardingRejectedCount)

        VpnDiagnostics.record(VpnDiagnostics.FailureCode.UPSTREAM_TIMEOUT, "SocketTimeoutException")
        assertEquals(1L, VpnDiagnostics.upstreamTimeoutCount)

        VpnDiagnostics.record(VpnDiagnostics.FailureCode.TUN_READ_ERROR, "IOException")
        assertEquals(1L, VpnDiagnostics.tunReadErrorCount)

        VpnDiagnostics.record(VpnDiagnostics.FailureCode.TUN_WRITE_ERROR, "IOException")
        assertEquals(1L, VpnDiagnostics.tunWriteErrorCount)

        VpnDiagnostics.record(VpnDiagnostics.FailureCode.PACKET_PROCESSING, "IllegalArgumentException")
        VpnDiagnostics.record(VpnDiagnostics.FailureCode.DNS_PARSE_ERROR, "MalformedDnsException")
        assertEquals(2L, VpnDiagnostics.packetProcessingErrorCount)
    }

    @Test
    fun testForwardingActiveTracking() {
        assertEquals(0L, VpnDiagnostics.forwardingActiveCount)
        assertEquals(1L, VpnDiagnostics.forwardingActiveIncrement())
        assertEquals(2L, VpnDiagnostics.forwardingActiveIncrement())
        assertEquals(2L, VpnDiagnostics.forwardingActiveCount)
        assertEquals(1L, VpnDiagnostics.forwardingActiveDecrement())
        assertEquals(1L, VpnDiagnostics.forwardingActiveCount)
        assertEquals(0L, VpnDiagnostics.forwardingActiveDecrement())
        assertEquals(0L, VpnDiagnostics.forwardingActiveCount)
    }

    @Test
    fun testRingBufferBoundedAt200Events() {
        for (i in 1..250) {
            VpnDiagnostics.record(
                code = VpnDiagnostics.FailureCode.PACKET_PROCESSING,
                exceptionClass = "Error_$i"
            )
        }

        val events = VpnDiagnostics.getRecentEvents()
        assertEquals("Ring buffer must be bounded at 200 events", 200, events.size)
        // First 50 should have been pruned
        assertEquals("Error_51", events.first().exceptionClass)
        assertEquals("Error_250", events.last().exceptionClass)
    }

    @Test
    fun testPrivacyInvariantZeroPiiOrDomainLeakage() {
        val testDomain = "super-secret-user-domain.example.com"
        val testIp = "192.168.1.105"
        val testUrl = "https://sensitive-site.com/login"

        VpnDiagnostics.record(
            code = VpnDiagnostics.FailureCode.FORWARDING_REJECTED,
            exceptionClass = "java.util.concurrent.RejectedExecutionException",
            queueDepth = 64,
            resolverCount = 2
        )

        val events = VpnDiagnostics.getRecentEvents()
        for (event in events) {
            val eventString = event.toString()
            assertFalse("DiagnosticEvent must not contain domain", eventString.contains(testDomain))
            assertFalse("DiagnosticEvent must not contain IP", eventString.contains(testIp))
            assertFalse("DiagnosticEvent must not contain URL", eventString.contains(testUrl))
            assertTrue(event.queueDepth >= 0)
        }
    }
}
