package com.unblocker.app.logic.dns

import java.util.concurrent.ArrayBlockingQueue

/**
 * Reusable object pool for packet byte arrays.
 * Bounds retained packet buffers and reduces allocation churn in the TUN loop.
 */
class ByteArrayPool(
    val arraySize: Int = 4096,
    val maxPoolSize: Int = 64
) {
    private val pool = ArrayBlockingQueue<ByteArray>(maxPoolSize)

    init {
        val initialSize = maxPoolSize / 2
        repeat(initialSize) {
            pool.offer(ByteArray(arraySize))
        }
    }

    fun acquire(): ByteArray {
        return pool.poll() ?: ByteArray(arraySize)
    }

    fun release(array: ByteArray) {
        if (array.size == arraySize) {
            pool.offer(array)
        }
    }

    fun currentPoolSize(): Int = pool.size
}
