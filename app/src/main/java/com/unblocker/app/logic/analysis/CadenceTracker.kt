package com.unblocker.app.logic.analysis

import kotlin.math.sqrt

/** Raw domains and query times live only in bounded volatile memory. */
class CadenceTracker(private val capacity: Int = 2000) {
    private val windows = LinkedHashMap<String, ArrayDeque<Long>>(16, .75f, true)
    init { require(capacity > 0) }
    @Synchronized fun size() = windows.size
    @Synchronized fun observe(domain: String, now: Long): Float {
        val window = windows.getOrPut(domain) { ArrayDeque() }
        if (window.isNotEmpty() && now < window.last()) window.clear()
        window.addLast(now)
        while (window.size > 15 || (window.isNotEmpty() && now - window.first() > 60_000)) window.removeFirst()
        while (windows.size > capacity) windows.remove(windows.keys.first())
        return score(window.toList(), now)
    }
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
