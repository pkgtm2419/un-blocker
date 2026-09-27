package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Bounded device-keyed domain membership with no readable domain persistence. */
class PrivateDomainSet(
    private val key: SecretKey,
    private val file: File? = null,
    private val capacity: Int = DEFAULT_CAPACITY
) {
    private val identifiers = LinkedHashSet<String>()
    private val header = "v1:${identifier("unblocker-key-check")}"

    init {
        require(capacity > 0)
        if (file?.isFile == true) runCatching {
            file!!.bufferedReader().use { reader ->
                if (reader.readLine() == header) {
                    reader.lineSequence().take(capacity)
                        .filter { it.matches(IDENTIFIER_PATTERN) }
                        .forEach(identifiers::add)
                }
            }
        }
    }

    @Synchronized fun contains(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        return runCatching { identifier(normalized) in identifiers }.getOrDefault(false)
    }

    @Synchronized fun add(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = runCatching { identifier(normalized) }.getOrNull() ?: return false
        if (!identifiers.add(id)) return false
        while (identifiers.size > capacity) identifiers.remove(identifiers.first())
        runCatching { save() }
        return true
    }

    @Synchronized fun remove(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = runCatching { identifier(normalized) }.getOrNull() ?: return false
        if (!identifiers.remove(id)) return false
        runCatching { save() }
        return true
    }

    @Synchronized fun clear() {
        identifiers.clear()
        save()
    }

    @Synchronized fun size(): Int = identifiers.size

    private fun identifier(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun save() {
        val destination = file ?: return
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        val snapshot = buildString {
            append(header).append('\n')
            identifiers.forEach { append(it).append('\n') }
        }
        try {
            FileOutputStream(temporary).use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 1_000
        private val IDENTIFIER_PATTERN = Regex("[0-9a-f]{64}")
    }
}
