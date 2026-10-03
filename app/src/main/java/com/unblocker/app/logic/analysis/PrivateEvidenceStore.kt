package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.SecretKey

/** v3: opaque ID, score, state, evidence mask, windows, feedback and coarse day only. */
class PrivateEvidenceStore(
    private val key: SecretKey,
    private val file: File? = null,
    private val capacity: Int = 20_000,
    private val nowBucket: () -> Long = { System.currentTimeMillis() / 86_400_000 },
    private val flushIntervalMs: Long = 15_000L
) : Closeable {
    private val records = LinkedHashMap<String, ReputationEvidence>(16, .75f, true)
    private val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
    private val header = "v3:${id("key-check")}"
    private var isDirty = false
    private var lastPruneBucket = -1L
    private val fileWriteLock = Any()
    private val writer: ScheduledExecutorService? = if (file != null && flushIntervalMs > 0) {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "PrivateEvidenceStore-Writer").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay({
                runCatching { flush() }
            }, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS)
        }
    } else null

    init {
        require(capacity > 0)
        // Even many individually short rows must not induce unbounded startup work.
        val maximumBytes = 69L + capacity.toLong() * 2 * 257
        if (file?.isFile == true && file.length() <= maximumBytes) runCatching {
            file!!.bufferedReader().use { reader ->
                if (BoundedEvidenceInput.line(reader, 68) == header) repeat(capacity * 2) {
                    val line = BoundedEvidenceInput.line(reader, 256) ?: return@use
                    val p = line.split(':')
                    if (p.size != 7 || !p[0].matches(Regex("[0-9a-f]{64}"))) return@repeat
                    val score = p[1].toFloatOrNull() ?: return@repeat
                    val state = ReputationState.entries.firstOrNull { it.name == p[2] } ?: return@repeat
                    val mask = p[3].toIntOrNull() ?: return@repeat
                    val windows = p[4].toIntOrNull() ?: return@repeat
                    val feedback = UserFeedback.entries.getOrNull(p[5].toIntOrNull() ?: -1) ?: return@repeat
                    val bucket = p[6].toLongOrNull() ?: return@repeat
                    val record = ReputationEvidence(score, state, mask, windows, feedback, bucket)
                    if (valid(record) && !stale(record)) records[p[0]] = record
                }
            }
            trim()
        }
    }
    fun currentBucket() = nowBucket().coerceAtLeast(0)
    private fun id(domain: String): String {
        val bytes = mac.doFinal(("reputation-v3:" + domain).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun stale(r: ReputationEvidence): Boolean = currentBucket() - r.dayBucket >
        if (r.state == ReputationState.CONFIRMED || r.feedback != UserFeedback.NONE) 30 else 7
    private fun valid(r: ReputationEvidence) = r.score.isFinite() && r.score in 0f..1f &&
        r.mask in 0..31 && r.positiveWindows in 0..255 && r.dayBucket in 0..currentBucket() &&
        (r.state != ReputationState.CONFIRMED || r.mask and 16 != 0 ||
            (r.positiveWindows >= 2 && r.mask and 1 != 0 && Integer.bitCount(r.mask and 15) >= 2))
    @Synchronized fun size(): Int { prune(); return records.size }
    @Synchronized fun confirmedCount(): Int { prune(); return records.values.count { it.confirmed } }
    @Synchronized fun get(raw: String): ReputationEvidence? {
        val domain = DomainName.normalize(raw) ?: return null
        prune()
        return runCatching { records[id(domain)] }.getOrNull()
    }
    @Synchronized fun put(raw: String, record: ReputationEvidence) {
        val domain = DomainName.normalize(raw) ?: return
        if (!valid(record)) return
        val token = runCatching { id(domain) }.getOrNull() ?: return
        prune()
        if (records[token] == record) return
        records[token] = record
        trim()
        isDirty = true
    }
    fun clear() {
        val snapshot = synchronized(this) {
            records.clear()
            isDirty = true
            takeSnapshotIfDirty()
        }
        snapshot?.let { writeSnapshot(it) }
    }
    @Synchronized fun clearFeedback() {
        records.replaceAll { _, r ->
            if (r.feedback == UserFeedback.NONE) r else
                r.copy(feedback = UserFeedback.NONE, state = ReputationPolicy.normalState(r))
        }
        isDirty = true
    }
    private fun prune() {
        val bucket = currentBucket()
        if (bucket == lastPruneBucket) return
        lastPruneBucket = bucket
        records.entries.removeAll { stale(it.value) }
    }
    private fun trim() {
        val overflow = records.size - capacity
        if (overflow <= 0) return
        val it = records.iterator()
        var removed = 0
        while (it.hasNext() && removed < overflow) {
            it.next()
            it.remove()
            removed++
        }
    }

    @Synchronized private fun takeSnapshotIfDirty(): List<String>? {
        if (!isDirty || file == null) return null
        isDirty = false
        val lines = ArrayList<String>(records.size + 1)
        lines.add(header)
        for ((id, r) in records) {
            lines.add("$id:${r.score}:${r.state}:${r.mask}:${r.positiveWindows}:${r.feedback.ordinal}:${r.dayBucket}")
        }
        return lines
    }

    fun flush() {
        val lines = takeSnapshotIfDirty() ?: return
        writeSnapshot(lines)
    }

    private fun writeSnapshot(lines: List<String>) {
        val destination = file ?: return
        synchronized(fileWriteLock) {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            try {
                FileOutputStream(temporary).use { out ->
                    for (line in lines) {
                        out.write(line.toByteArray(Charsets.UTF_8))
                        out.write('\n'.code)
                    }
                    out.fd.sync()
                }
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                synchronized(this) { isDirty = true }
                temporary.delete()
            }
        }
    }

    override fun close() {
        writer?.shutdown()
        flush()
        try {
            writer?.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
