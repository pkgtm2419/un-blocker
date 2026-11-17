package com.unblocker.app.logic.analysis

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.math.pow

/**
 * Legacy device-keyed score store with week-based decay and no readable domain persistence.
 *
 * @param key HMAC-SHA256 key for identifiers.
 * @param file Optional persistence file; null keeps records volatile.
 * @param capacity Maximum retained record count; must be positive.
 * @param nowMillis Wall clock in Unix epoch milliseconds for score aging.
 * @throws IllegalArgumentException If capacity is not positive.
 */
class PrivateReputationStore(
    private val key: SecretKey,
    private val file: File? = null,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Reputation(
        val score: Float,
        val confirmedAtMillis: Long,
    )

    private val records = LinkedHashMap<String, Reputation>()
    private val mac = Mac.getInstance(ALGORITHM)
    private val keyCheck = identifier("unblocker-key-check")
    private val header = "v2:$keyCheck"
    private val legacyHeader = "v1:$keyCheck"

    init {
        require(capacity > 0)
        if (file?.isFile == true) {
            runCatching {
                val loadedAt = nowMillis()
                file!!.bufferedReader().use { reader ->
                    val storedHeader = reader.readLine()
                    if (storedHeader == header || storedHeader == legacyHeader) {
                        reader.lineSequence().take(capacity).forEach { line ->
                            val parts = line.split(':')
                            val score = parts.getOrNull(1)?.toFloatOrNull()
                            val confirmedAt =
                                if (storedHeader == header) {
                                    parts.getOrNull(2)?.toLongOrNull()?.coerceAtMost(loadedAt)
                                } else {
                                    loadedAt
                                }
                            val expectedParts = if (storedHeader == header) 3 else 2
                            if (parts.size == expectedParts && parts[0].matches(IDENTIFIER_PATTERN) &&
                                valid(score) && confirmedAt != null && confirmedAt >= 0L
                            ) {
                                records[parts[0]] = Reputation(score!!, confirmedAt)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[v ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(chars)
    }

    private fun identifier(domain: String): String {
        mac.init(key)
        val bytes =
            mac.doFinal(
                domain
                    .trim()
                    .trimEnd('.')
                    .lowercase(Locale.ROOT)
                    .toByteArray(Charsets.UTF_8),
            )
        return bytesToHex(bytes)
    }

    /**
     * Returns a stored score after whole-week decay.
     *
     * @param domain Hostname canonicalized for hashing.
     * @return Decayed score, or null when missing or lookup fails.
     */
    @Synchronized fun get(domain: String): Float? =
        runCatching {
            records[identifier(domain)]?.let { decayedScore(it, nowMillis()) }
        }.getOrNull()

    /**
     * Returns the retained record count without removing records whose scores have decayed.
     */
    @Synchronized fun size(): Int = records.size

    /**
     * Stores a valid score at the current time and attempts persistence.
     *
     * @param domain Nonblank hostname used for the opaque identifier.
     * @param score Finite value from zero through one; invalid values are ignored.
     * Hashing and persistence failures are suppressed.
     */
    @Synchronized fun put(
        domain: String,
        score: Float,
    ) {
        if (domain.isBlank() || !valid(score)) return
        // Keystore can become temporarily unavailable after successful initialization.
        val id = runCatching { identifier(domain) }.getOrNull() ?: return
        records.remove(id)
        records[id] = Reputation(score, nowMillis())
        trim()
        // A storage failure must not take down DNS filtering. The in-memory score survives.
        runCatching { save() }
    }

    /**
     * Clears memory records and persists the empty file when file-backed.
     *
     * @throws java.io.IOException If writing or replacing the persistence file fails.
     */
    @Synchronized fun clear() {
        records.clear()
        save()
    }

    /**
     * Imports valid legacy domain/score lines, persists identifiers and deletes the readable source.
     *
     * @param legacy Readable legacy file; missing files are ignored.
     * @throws java.io.IOException If reading or saving fails.
     * @throws IllegalStateException If the legacy file cannot be deleted after saving.
     */
    @Synchronized fun migrate(legacy: File) {
        if (!legacy.exists()) return
        legacy.useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(':')
                val domain = parts.firstOrNull().orEmpty()
                val score = if (parts.size == 1) 0.85f else parts.getOrNull(1)?.toFloatOrNull()
                if (domain.length <= 253 && domain.contains('.') && !domain.startsWith('#') && valid(score)) {
                    records[identifier(domain)] = Reputation(score!!, nowMillis())
                    trim()
                }
            }
        }
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
        val snapshot =
            buildString {
                append(header).append('\n')
                records.forEach { (id, reputation) ->
                    append(id)
                        .append(':')
                        .append(reputation.score)
                        .append(':')
                        .append(reputation.confirmedAtMillis)
                        .append('\n')
                }
            }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(snapshot.toByteArray(Charsets.UTF_8))
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun decayedScore(
        reputation: Reputation,
        now: Long,
    ): Float {
        val elapsedWeeks = ((now - reputation.confirmedAtMillis).coerceAtLeast(0L) / WEEK_MILLIS).toInt()
        return (reputation.score * DECAY_PER_WEEK.toDouble().pow(elapsedWeeks.toDouble())).toFloat()
    }

    private fun valid(score: Float?) = score != null && score.isFinite() && score in 0f..1f

    /**
     * Default bound for the legacy score store.
     */
    companion object {
        /**
         * Default maximum number of retained opaque reputation records.
         */
        const val DEFAULT_CAPACITY = 20_000
        private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1_000
        private const val DECAY_PER_WEEK = 0.95f
        private const val ALGORITHM = "HmacSHA256"
        private val IDENTIFIER_PATTERN = Regex("[0-9a-f]{64}")
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
