package com.unblocker.app.dns

import com.unblocker.app.logic.dns.ByteArrayPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ByteArrayPoolTest {

    @Test
    fun testAcquireAndRelease() {
        val pool = ByteArrayPool(arraySize = 4096, maxPoolSize = 10)
        val initialSize = pool.currentPoolSize()
        assertTrue("Pool should pre-warm with buffers", initialSize > 0)

        val buffer = pool.acquire()
        assertNotNull(buffer)
        assertEquals(4096, buffer.size)

        pool.release(buffer)
        assertEquals(initialSize, pool.currentPoolSize())
    }

    @Test
    fun testPoolCapacityBound() {
        val pool = ByteArrayPool(arraySize = 1024, maxPoolSize = 5)

        // Release 10 buffers into a pool with capacity 5
        repeat(10) {
            pool.release(ByteArray(1024))
        }

        assertTrue("Pool size should not exceed maxPoolSize", pool.currentPoolSize() <= 5)
    }

    @Test
    fun testConcurrentAccess() {
        val pool = ByteArrayPool(arraySize = 2048, maxPoolSize = 32)
        val threads = 16
        val iterationsPerThread = 500
        val latch = CountDownLatch(threads)
        val errorCount = AtomicInteger(0)

        repeat(threads) {
            Thread {
                try {
                    repeat(iterationsPerThread) {
                        val buf = pool.acquire()
                        if (buf.size != 2048) {
                            errorCount.incrementAndGet()
                        }
                        // Simulate brief processing
                        buf[0] = (it and 0xFF).toByte()
                        pool.release(buf)
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("All concurrent threads should complete within timeout", completed)
        assertEquals("Zero errors during concurrent buffer pool operations", 0, errorCount.get())
    }
}
