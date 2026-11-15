package com.unblocker.app.logic.dns

import java.util.concurrent.ArrayBlockingQueue

/**
 * Bounded pool retaining fixed-size packet arrays; acquired arrays are not cleared.
 *
 * @property arraySize Length of each pooled array in bytes.
 * @property maxPoolSize Maximum number of arrays retained by the queue.
 * @throws IllegalArgumentException If maxPoolSize is not positive.
 * @throws NegativeArraySizeException If allocation occurs with a negative arraySize.
 */
class ByteArrayPool(
    val arraySize: Int = 4096,
    val maxPoolSize: Int = 64,
) {
    private val pool = ArrayBlockingQueue<ByteArray>(maxPoolSize)

    init {
        val initialSize = maxPoolSize / 2
        repeat(initialSize) {
            pool.offer(ByteArray(arraySize))
        }
    }

    /**
     * Returns a retained or newly allocated array whose exclusive ownership passes to the caller.
     */
    fun acquire(): ByteArray = pool.poll() ?: ByteArray(arraySize)

    /**
     * Returns an exclusively owned buffer when its size matches; full queues discard it.
     *
     * @param array Buffer the caller will no longer read or mutate after release.
     */
    fun release(array: ByteArray) {
        if (array.size == arraySize) {
            pool.offer(array)
        }
    }

    /**
     * Returns the number of arrays currently retained, excluding borrowed buffers.
     */
    fun currentPoolSize(): Int = pool.size
}
