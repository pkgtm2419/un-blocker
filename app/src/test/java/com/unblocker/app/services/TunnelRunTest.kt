package com.unblocker.app.services

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class TunnelRunTest {
    private class RecordingCloseable : Closeable {
        val closeCount = AtomicInteger(0)
        override fun close() { closeCount.incrementAndGet() }
    }

    @Test fun restartGetsFreshForwarderAndOldCleanupCannotCloseIt() {
        val old = TunnelRun(1)
        old.close()
        val current = TunnelRun(2)
        try {
            old.close()
            assertEquals(42, current.forwarding.submit<Int> { 42 }.get(2, TimeUnit.SECONDS))
            assertTrue(current.running.get())
            assertFalse(old.running.get())
        } finally { current.close() }
    }

    @Test fun saturatedForwarderRejectsExcessWorkAndTerminatesOnClose() {
        val run = TunnelRun(1)
        val entered = CountDownLatch(4)
        val release = CountDownLatch(1)
        try {
            repeat(4) { run.forwarding.execute {
                entered.countDown()
                try { release.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            } }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            repeat(64) { run.forwarding.execute {} }
            try {
                run.forwarding.execute {}
                fail("Excess work should be rejected")
            } catch (_: RejectedExecutionException) { }
        } finally {
            run.close()
            release.countDown()
        }
        assertTrue(run.forwarding.awaitTermination(3, TimeUnit.SECONDS))
    }

    @Test fun closeClosesRegisteredUdpAndTcpResourcesExactlyOnce() {
        val run = TunnelRun(1)
        val udpResource = RecordingCloseable()
        val tcpResource = RecordingCloseable()
        assertTrue(run.register(udpResource))
        assertTrue(run.register(tcpResource))

        run.close()
        run.close()

        assertEquals(1, udpResource.closeCount.get())
        assertEquals(1, tcpResource.closeCount.get())
    }

    @Test fun closeStillCleansResourcesAfterRunningFlagWasClearedByLoopFailure() {
        val run = TunnelRun(1)
        val resource = RecordingCloseable()
        assertTrue(run.register(resource))

        run.running.set(false)
        run.close()

        assertEquals(1, resource.closeCount.get())
        assertTrue(run.forwarding.isShutdown)
    }

    @Test fun closeWaitsForInFlightDeliveryAndRejectsLateDelivery() {
        val run = TunnelRun(1)
        val deliveryEntered = CountDownLatch(1)
        val releaseDelivery = CountDownLatch(1)
        val cleanupAttempted = CountDownLatch(1)
        val cleanupFinished = CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        val cleanupThread = Thread {
            cleanupAttempted.countDown()
            run.close()
            cleanupFinished.countDown()
        }
        try {
            val delivery = executor.submit<Boolean> {
                run.useWhileRunning {
                    deliveryEntered.countDown()
                    releaseDelivery.await(2, TimeUnit.SECONDS)
                }
            }
            assertTrue(deliveryEntered.await(2, TimeUnit.SECONDS))

            cleanupThread.start()
            assertTrue(cleanupAttempted.await(2, TimeUnit.SECONDS))
            val blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (cleanupThread.state != Thread.State.BLOCKED && System.nanoTime() < blockedDeadline) {
                Thread.yield()
            }
            assertEquals("cleanup must contend on the delivery lock", Thread.State.BLOCKED, cleanupThread.state)
            assertEquals(1L, cleanupFinished.count)

            releaseDelivery.countDown()
            assertTrue(delivery.get(2, TimeUnit.SECONDS))
            assertTrue(cleanupFinished.await(2, TimeUnit.SECONDS))
            assertFalse(run.useWhileRunning { fail("late delivery must not execute") })
        } finally {
            releaseDelivery.countDown()
            cleanupThread.join(2_000)
            run.close()
            executor.shutdownNow()
        }
    }

    @Test fun registerAfterCloseImmediatelyClosesResource() {
        val run = TunnelRun(1)
        run.close()
        val lateResource = RecordingCloseable()

        assertFalse(run.register(lateResource))

        assertEquals(1, lateResource.closeCount.get())
    }

    @Test fun unregisterPreventsLaterRunOwnership() {
        val run = TunnelRun(1)
        val resource = RecordingCloseable()
        assertTrue(run.register(resource))

        run.unregister(resource)
        run.close()

        assertEquals(0, resource.closeCount.get())
        resource.close()
        assertEquals(1, resource.closeCount.get())
    }
}
