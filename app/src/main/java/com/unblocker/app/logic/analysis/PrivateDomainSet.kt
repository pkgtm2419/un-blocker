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
        // 1. Exact match
        val exactId = runCatching { identifier(normalized) }.getOrNull()
        if (exactId != null && exactId in identifiers) return true

        // 2. Subtree match on host itself
        val subtreeId = runCatching { identifier("subtree:$normalized") }.getOrNull()
        if (subtreeId != null && subtreeId in identifiers) return true

        // 3. Subtree match on any parent domain
        var dot = normalized.indexOf('.')
        while (dot != -1) {
            val parent = normalized.substring(dot + 1)
            val parentSubtreeId = runCatching { identifier("subtree:$parent") }.getOrNull()
            if (parentSubtreeId != null && parentSubtreeId in identifiers) return true
            dot = normalized.indexOf('.', dot + 1)
        }

        return false
    }

    @Synchronized fun containsExact(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        return runCatching { identifier(normalized) in identifiers }.getOrDefault(false)
    }

    @Synchronized fun containsSubtree(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        return runCatching { identifier("subtree:$normalized") in identifiers }.getOrDefault(false)
    }

    @Synchronized fun add(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = runCatching { identifier(normalized) }.getOrNull() ?: return false
        if (!identifiers.add(id)) return false
        while (identifiers.size > capacity) identifiers.remove(identifiers.first())
        runCatching { save() }
        return true
    }

    @Synchronized fun addSubtree(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = runCatching { identifier("subtree:$normalized") }.getOrNull() ?: return false
        if (!identifiers.add(id)) return false
        while (identifiers.size > capacity) identifiers.remove(identifiers.first())
        runCatching { save() }
        return true
    }

    @Synchronized fun remove(domain: String): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = runCatching { identifier(normalized) }.getOrNull()
        val subtreeId = runCatching { identifier("subtree:$normalized") }.getOrNull()
        var removed = false
        if (id != null && identifiers.remove(id)) removed = true
        if (subtreeId != null && identifiers.remove(subtreeId)) removed = true
        if (removed) {
            runCatching { save() }
        }
        return removed
    }

    @Synchronized fun clear() {
        identifiers.clear()
        save()
    }

    @Synchronized fun size(): Int = identifiers.size

    private fun identifier(value: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun save() {
        if (file == null) return
        val temp = File.createTempFile("pds-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temp).bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.append(header).append('\n')
                identifiers.forEach { id -> writer.append(id).append('\n') }
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 1000
        private const val ALGORITHM = "HmacSHA256"
        private val IDENTIFIER_PATTERN = Regex("[0-9a-f]{64}")
    }
}
