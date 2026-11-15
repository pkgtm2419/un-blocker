package com.unblocker.data.cache

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.Locale

/**
 * Compiles supported hosts, Adblock, and plain-domain lines into a signed-sorted 64-bit hash index.
 *
 * Parsing is syntax normalization, not the active app's complete blocklist admission policy.
 */
object RuleCompiler {

    /**
     * Initial FNV-1a 64-bit hash state, represented as a signed Long.
     */
    const val FNV64_OFFSET_BASIS: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325L
    /**
     * FNV-1a multiplier; Long overflow intentionally wraps the hash arithmetic.
     */
    const val FNV64_PRIME: Long = 0x100000001b3L

    /**
     * Domain keys always seeded by this compiler; their presence does not guarantee DoH coverage.
     */
    val DOH_BOOTSTRAP_DOMAINS: List<String> = listOf(
        "use-application-dns.net",
        "dns.google",
        "cloudflare-dns.com",
        "dns.quad9.net",
        "bootstrapDomain.opendns.com"
    )

    private const val BUFFER_CAPACITY = 64 * 1024 // 64 KB direct buffer

    /**
     * Extracts a lowercase candidate from supported hosts, Adblock, or plain-domain syntax.
     *
     * This accepts underscores and does not validate every DNS label or policy safety exception.
     *
     * @param rawLine Untrusted rule line to normalize.
     * @return Candidate with a dot and permitted characters, or null for ignored or rejected input.
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
            val terminatorIndex = candidate.indexOfAny(charArrayOf('^', '$', '/'))
            if (terminatorIndex != -1) {
                candidate = candidate.substring(0, terminatorIndex)
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
            val character = domain[i]
            if (character !in 'a'..'z' && character !in '0'..'9' && character != '.' && character != '-' && character != '_') {
                return null
            }
        }

        return domain
    }

    /**
     * Hashes a character range using FNV-1a, lowercasing ASCII uppercase characters.
     *
     * Only the low eight bits of each character contribute; Long overflow is intentional.
     *
     * @param domain Character sequence to read without copying.
     * @param start Inclusive character index.
     * @param end Exclusive character index.
     * @return Signed Long representation of the 64-bit hash.
     * @throws IllegalArgumentException If the range is reversed, negative, or exceeds [domain].
     */
    fun fnv1a64(domain: CharSequence, start: Int = 0, end: Int = domain.length): Long {
        require(start in 0..end && end <= domain.length) {
            "Invalid bounds: start=$start, end=$end, length=${domain.length}"
        }
        var hash = FNV64_OFFSET_BASIS
        for (i in start until end) {
            val characterCode = domain[i].code
            val lowercaseCode = if (characterCode in 65..90) (characterCode + 32) else characterCode
            hash = (hash xor (lowercaseCode.toLong() and 0xFFL)) * FNV64_PRIME
        }
        return hash
    }

    /**
     * Normalizes input lines and writes sorted, deduplicated hashes in big-endian order.
     *
     * Bootstrap keys are always added. Readers remain owned by the caller. Output uses a temporary
     * file followed by delete/rename, with copy fallback; replacement is not guaranteed atomic.
     *
     * @param readers Open rule streams read sequentially to end-of-file without being closed here.
     * @param outputFile Destination index file; its parent directory is created when needed.
     * @return Number of unique hash entries written.
     * @throws java.io.IOException If reading or writing the index fails.
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
        for (bootstrapDomain in DOH_BOOTSTRAP_DOMAINS) {
            addHash(fnv1a64(bootstrapDomain))
        }

        // 2. Stream all readers into primitive array
        for (reader in readers) {
            var line = reader.readLine()
            while (line != null) {
                val normalizedDomain = extractCleanDomain(line)
                if (normalizedDomain != null) {
                    addHash(fnv1a64(normalizedDomain))
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
        val temporaryFile = File(parentDir, "${outputFile.name}.tmp")
        if (temporaryFile.exists()) temporaryFile.delete()

        val directBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY).order(ByteOrder.BIG_ENDIAN)

        FileOutputStream(temporaryFile).use { outputStream ->
            val channel = outputStream.channel
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

        // 6. Replace the target via rename, falling back to a copy if necessary.
        if (outputFile.exists()) {
            outputFile.delete()
        }
        if (!temporaryFile.renameTo(outputFile)) {
            temporaryFile.copyTo(outputFile, overwrite = true)
            temporaryFile.delete()
        }

        return uniqueCount
    }
}
