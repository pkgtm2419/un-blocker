package com.unblocker.app.services

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TunnelRunTest {
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
}
