package com.unblocker.data.cache

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.Locale

/**
 * High-performance off-heap compiler for ad/tracker rules.
 *
 * Normalizes multi-syntax rule definitions (Hosts, Adblock/uBlock, plain domains)
 * into a sorted, deduplicated 64-bit FNV-1a binary index (`rules.bin`).
 */
object RuleCompiler {

    const val FNV64_OFFSET_BASIS: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325L
    const val FNV64_PRIME: Long = 0x100000001b3L

    val DOH_BOOTSTRAP_DOMAINS: List<String> = listOf(
        "use-application-dns.net",
        "dns.google",
        "cloudflare-dns.com",
        "dns.quad9.net",
        "doh.opendns.com"
    )

    private const val BUFFER_CAPACITY = 64 * 1024 // 64 KB direct buffer

    /**
     * Extracts a normalized domain name from a raw rule line across multiple syntaxes:
     * - Adblock/uBlock rules (`||domain^$third-party`)
     * - Hosts file rules (`0.0.0.0 domain.com`, `127.0.0.1 domain.com`, `::1 domain.com`)
     * - Plain domain entries
     *
     * Returns null for comments, exceptions, cosmetic filters, localhost, or invalid lines.
     */
    fun extractCleanDomain(rawLine: String): String? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null

        // Ignore comments, adblock exceptions (@@), and cosmetic rules (##, #@#)
        val firstChar = trimmed[0]
        if (firstChar == '#' || firstChar == '!' || firstChar == '[') return null
        if (trimmed.startsWith("@@")) return null
        if (trimmed.contains("##") || trimmed.contains("#@#")) return null

        // Strip inline comments (#...)
        val lineWithoutComment = if (trimmed.contains('#')) {
            trimmed.substringBefore('#').trim()
        } else {
            trimmed
        }
        if (lineWithoutComment.isEmpty()) return null

        var candidate = lineWithoutComment

        // Handle Adblock / uBlock syntax (||domain^$third-party)
        if (candidate.startsWith("||")) {
            candidate = candidate.substring(2)
            val termIdx = candidate.indexOfAny(charArrayOf('^', '$', '/'))
            if (termIdx != -1) {
                candidate = candidate.substring(0, termIdx)
            }
        } else {
            // Handle Hosts syntax: 0.0.0.0 domain.com, 127.0.0.1 domain.com, ::1 domain.com
            if (candidate.startsWith("0.0.0.0") || candidate.startsWith("127.0.0.1") || candidate.startsWith("::1")) {
                val tokens = candidate.split(Regex("\\s+"))
                if (tokens.size >= 2) {
                    candidate = tokens[1]
                } else {
                    return null
                }
            } else if (candidate.contains(Regex("\\s+"))) {
                // If there are other whitespace-separated tokens, extract the first domain candidate
                val tokens = candidate.split(Regex("\\s+"))
                candidate = tokens[0]
            }
        }

        // Strip leading/trailing dots and whitespace
        candidate = candidate.trim().trim('.')
        if (candidate.isEmpty() || candidate.length > 253) return null

        // Lowercase ASCII normalization
        val domain = candidate.lowercase(Locale.ROOT)

        // Reject localhost or non-FQDN (must contain at least one dot)
        if (domain == "localhost" || !domain.contains('.') || domain.contains("..")) {
            return null
        }

        // Validate character set (RFC 1035 / DNS valid characters: a-z, 0-9, ., -, _)
        for (i in 0 until domain.length) {
            val ch = domain[i]
            if (ch !in 'a'..'z' && ch !in '0'..'9' && ch != '.' && ch != '-' && ch != '_') {
                return null
            }
        }

        return domain
    }

    /**
     * Computes the 64-bit FNV-1a hash over [domain] in index range `[start, end)`.
     * Operates with zero allocations by reading characters directly and converting
     * uppercase ASCII (65..90) to lowercase (+32) on the fly.
     */
    fun fnv1a64(domain: CharSequence, start: Int = 0, end: Int = domain.length): Long {
        require(start in 0..end && end <= domain.length) {
            "Invalid bounds: start=$start, end=$end, length=${domain.length}"
        }
        var hash = FNV64_OFFSET_BASIS
        for (i in start until end) {
            val c = domain[i].code
            val lower = if (c in 65..90) (c + 32) else c
            hash = (hash xor (lower.toLong() and 0xFFL)) * FNV64_PRIME
        }
        return hash
    }

    /**
     * Streams rules from [readers], normalizes them, and writes a sorted, deduplicated
     * 64-bit binary index file directly to [outputFile] using off-heap buffers.
     *
     * Pre-seeds DoH bootstrap domains to ensure browsers/WebViews fall back to UDP 53.
     *
     * @return The count of unique 64-bit rule hashes written to [outputFile].
     */
    fun compileStreamsToBinaryFile(readers: List<BufferedReader>, outputFile: File): Int {
        var hashes = LongArray(262_144)
        var count = 0

        fun addHash(hash: Long) {
            if (count == hashes.size) {
                hashes = hashes.copyOf(hashes.size * 2)
            }
            hashes[count++] = hash
        }

        // 1. Pre-seed mandatory DoH bootstrap domains
        for (doh in DOH_BOOTSTRAP_DOMAINS) {
            addHash(fnv1a64(doh))
        }

        // 2. Stream all readers into primitive array
        for (reader in readers) {
            var line = reader.readLine()
            while (line != null) {
                val clean = extractCleanDomain(line)
                if (clean != null) {
                    addHash(fnv1a64(clean))
                }
                line = reader.readLine()
            }
        }

        if (count == 0) {
            if (outputFile.exists()) outputFile.delete()
            outputFile.createNewFile()
            return 0
        }

        // 3. Sort in-place
        Arrays.sort(hashes, 0, count)

        // 4. Deduplicate adjacent identical hashes in-place
        var uniqueCount = 1
        for (i in 1 until count) {
            if (hashes[i] != hashes[uniqueCount - 1]) {
                hashes[uniqueCount] = hashes[i]
                uniqueCount++
            }
        }

        // 5. Write to temporary file with a 64 KB direct ByteBuffer
        val parentDir = outputFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()
        val tmpFile = File(parentDir, "${outputFile.name}.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        val directBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY).order(ByteOrder.BIG_ENDIAN)

        FileOutputStream(tmpFile).use { fos ->
            val channel = fos.channel
            for (i in 0 until uniqueCount) {
                if (directBuffer.remaining() < 8) {
                    directBuffer.flip()
                    while (directBuffer.hasRemaining()) {
                        channel.write(directBuffer)
                    }
                    directBuffer.clear()
                }
                directBuffer.putLong(hashes[i])
            }
            if (directBuffer.position() > 0) {
                directBuffer.flip()
                while (directBuffer.hasRemaining()) {
                    channel.write(directBuffer)
                }
                directBuffer.clear()
            }
            channel.force(true)
        }

        // 6. Atomically replace target file
        if (outputFile.exists()) {
            outputFile.delete()
        }
        if (!tmpFile.renameTo(outputFile)) {
            tmpFile.copyTo(outputFile, overwrite = true)
            tmpFile.delete()
        }

        return uniqueCount
    }
}
