package com.unblocker.app.logic.analysis

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Device-keyed identifiers and scores only. No query history or raw domain persistence. */
class PrivateReputationStore(
    private val key: SecretKey,
    private val file: File? = null,
    private val capacity: Int = 5000
) {
    private val records = LinkedHashMap<String, Float>()
    private val header = "v1:${identifier("unblocker-key-check")}"

    init {
        require(capacity > 0)
        if (file?.isFile == true) runCatching {
            file!!.bufferedReader().use { reader ->
                if (reader.readLine() == header) reader.lineSequence().take(capacity).forEach { line ->
                    val parts = line.split(':')
                    val score = parts.getOrNull(1)?.toFloatOrNull()
                    if (parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}")) && valid(score)) {
                        records[parts[0]] = score!!
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

    @Synchronized fun get(domain: String): Float? = runCatching { records[identifier(domain)] }.getOrNull()
    @Synchronized fun size(): Int = records.size

    @Synchronized fun put(domain: String, score: Float) {
        if (domain.isBlank() || !valid(score)) return
        // Keystore can become temporarily unavailable after successful initialization.
        val id = runCatching { identifier(domain) }.getOrNull() ?: return
        if (records[id] == score) return
        records.remove(id)
        records[id] = score
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
                records[identifier(domain)] = score!!
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
            records.forEach { (id, score) -> append(id).append(':').append(score).append('\n') }
        }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(snapshot.toByteArray(Charsets.UTF_8))
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }

    private fun valid(score: Float?) = score != null && score.isFinite() && score in 0f..1f
}
