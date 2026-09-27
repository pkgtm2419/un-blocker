package com.unblocker.app.logic.analysis

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.math.pow

/** Device-keyed identifiers and scores only. No query history or raw domain persistence. */
class PrivateReputationStore(
    private val key: SecretKey,
    private val file: File? = null,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class Reputation(val score: Float, val confirmedAtMillis: Long)

    private val records = LinkedHashMap<String, Reputation>()
    private val keyCheck = identifier("unblocker-key-check")
    private val header = "v2:$keyCheck"
    private val legacyHeader = "v1:$keyCheck"

    init {
        require(capacity > 0)
        if (file?.isFile == true) runCatching {
            val loadedAt = nowMillis()
            file!!.bufferedReader().use { reader ->
                val storedHeader = reader.readLine()
                if (storedHeader == header || storedHeader == legacyHeader) {
                    reader.lineSequence().take(capacity).forEach { line ->
                        val parts = line.split(':')
                        val score = parts.getOrNull(1)?.toFloatOrNull()
                        val confirmedAt = if (storedHeader == header) {
                            parts.getOrNull(2)?.toLongOrNull()?.coerceAtMost(loadedAt)
                        } else loadedAt
                        val expectedParts = if (storedHeader == header) 3 else 2
                        if (parts.size == expectedParts && parts[0].matches(IDENTIFIER_PATTERN) &&
                            valid(score) && confirmedAt != null && confirmedAt >= 0L) {
                            records[parts[0]] = Reputation(score!!, confirmedAt)
                        }
                    }
                }
            }
        }
    }

    private fun identifier(domain: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(domain.trim().trimEnd('.').lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Synchronized fun get(domain: String): Float? = runCatching {
        records[identifier(domain)]?.let { decayedScore(it, nowMillis()) }
    }.getOrNull()
    @Synchronized fun size(): Int = records.size

    @Synchronized fun put(domain: String, score: Float) {
        if (domain.isBlank() || !valid(score)) return
        // Keystore can become temporarily unavailable after successful initialization.
        val id = runCatching { identifier(domain) }.getOrNull() ?: return
        records.remove(id)
        records[id] = Reputation(score, nowMillis())
        trim()
        // A storage failure must not take down DNS filtering. The in-memory score survives.
        runCatching { save() }
    }

    @Synchronized fun clear() {
        records.clear()
        save()
    }

    @Synchronized fun migrate(legacy: File) {
        if (!legacy.exists()) return
        legacy.useLines { lines -> lines.forEach { line ->
            val parts = line.trim().split(':')
            val domain = parts.firstOrNull().orEmpty()
            val score = if (parts.size == 1) 0.85f else parts.getOrNull(1)?.toFloatOrNull()
            if (domain.length <= 253 && domain.contains('.') && !domain.startsWith('#') && valid(score)) {
                records[identifier(domain)] = Reputation(score!!, nowMillis())
                trim()
            }
        } }
        save()
        check(legacy.delete()) { "Legacy learning cleanup failed" }
    }

    private fun trim() {
        while (records.size > capacity) records.remove(records.keys.first())
    }

    private fun save() {
        val destination = file ?: return
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        val snapshot = buildString {
            append(header).append('\n')
            records.forEach { (id, reputation) ->
                append(id).append(':').append(reputation.score).append(':')
                    .append(reputation.confirmedAtMillis).append('\n')
            }
        }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(snapshot.toByteArray(Charsets.UTF_8))
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }

    private fun decayedScore(reputation: Reputation, now: Long): Float {
        val elapsedWeeks = ((now - reputation.confirmedAtMillis).coerceAtLeast(0L) / WEEK_MILLIS).toInt()
        return (reputation.score * DECAY_PER_WEEK.toDouble().pow(elapsedWeeks.toDouble())).toFloat()
    }

    private fun valid(score: Float?) = score != null && score.isFinite() && score in 0f..1f

    companion object {
        const val DEFAULT_CAPACITY = 20_000
        private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1_000
        private const val DECAY_PER_WEEK = 0.95f
        private val IDENTIFIER_PATTERN = Regex("[0-9a-f]{64}")
    }
}
