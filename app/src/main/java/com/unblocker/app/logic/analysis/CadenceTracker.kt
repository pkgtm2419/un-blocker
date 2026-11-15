package com.unblocker.app.logic.analysis

import kotlin.math.sqrt

/**
 * Keeps raw names and millisecond query samples only in bounded volatile memory.
 *
 * @param capacity Maximum number of domain windows; must be positive.
 * @throws IllegalArgumentException If capacity is not positive.
 */
class CadenceTracker(private val capacity: Int = 2000) {
    private val windows = LinkedHashMap<String, ArrayDeque<Long>>(16, .75f, true)
    init { require(capacity > 0) }
    /**
     * Returns the number of retained domain windows, including windows with expired samples.
     */
    /**
     * Records a timestamp and scores burst or periodic query cadence.
     *
     * @param domain In-memory key for the observed hostname.
     * @param now Monotonic timestamp in milliseconds.
     * @return Cadence score from zero through 0.85; regressing time resets that domain window.
     */
    @Synchronized fun size() = windows.size
    @Synchronized fun observe(domain: String, now: Long): Float {
        val window = windows.getOrPut(domain) { ArrayDeque() }
        if (window.isNotEmpty() && now < window.last()) window.clear()
        window.addLast(now)
        while (window.size > 15 || (window.isNotEmpty() && now - window.first() > 60_000)) window.removeFirst()
        while (windows.size > capacity) windows.remove(windows.keys.first())
        return score(window.toList(), now)
    }
    /**
     * Scores retained samples without adding a query observation.
     *
     * @param domain In-memory hostname key.
     * @param now Monotonic timestamp in milliseconds used to exclude expired samples.
     * @return Cadence score, or zero when insufficient current samples exist.
     */
    @Synchronized fun peek(domain: String, now: Long): Float = score(windows[domain]?.toList().orEmpty(), now)
    private fun score(samples: List<Long>, now: Long): Float {
        val current = samples.filter { now >= it && now - it <= 60_000 }
        if (current.count { now - it < 600 } >= 4) return .85f
        val intervals = current.zipWithNext { a, b -> b - a }
        if (intervals.size >= 4) {
            val avg = intervals.average()
            if (avg in 2000.0..30000.0 && sqrt(intervals.map { (it-avg)*(it-avg) }.average()) < avg*.25) return .75f
        }
        return (current.size / 15f).coerceAtMost(.4f).takeIf { current.size >= 3 } ?: 0f
    }
}
