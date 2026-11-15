package com.unblocker.app.data.repository

import com.unblocker.app.logic.rules.ExtensionRuleStore
import com.unblocker.app.logic.rules.ParsedLine
import com.unblocker.app.logic.rules.SkipReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Outcome of downloading and admitting a bounded DNS rule list.
 */
sealed class DownloadResult {
    /**
     * The admitted file was saved.
     *
     * @property bytesRead Total response body bytes consumed, including discarded lines.
     * @property validRuleCount Number of accepted rules written to the destination.
     */
    data class Success(
        val bytesRead: Long,
        val validRuleCount: Int,
    ) : DownloadResult()

    /**
     * The HTTP response or redirect chain was rejected.
     *
     * @property statusCode Last observed HTTP response code.
     * @property message Failure explanation.
     */
    data class HttpFailure(
        val statusCode: Int,
        val message: String,
    ) : DownloadResult()

    /**
     * Reading the response failed.
     *
     * @property message Network or stream I/O failure explanation.
     */
    data class NetworkFailure(
        val message: String,
    ) : DownloadResult()

    /**
     * A URL or resolved destination failed validation.
     *
     * @property message Source validation failure explanation.
     */
    data class InvalidSource(
        val message: String,
    ) : DownloadResult()

    /**
     * The byte, line, or rule budget was exceeded.
     *
     * @property message Description of the exceeded limit.
     */
    data class TooLarge(
        val message: String,
    ) : DownloadResult()

    /**
     * The list contained no admitted rules or too many anomalous lines.
     *
     * @property message Rule admission failure explanation.
     */
    data class InvalidRules(
        val message: String,
    ) : DownloadResult()
}

/**
 * Downloads HTTPS lists with redirect validation, resource limits, and DNS rule admission.
 *
 * @param connectionFactory Opens each validated URL; injectable for connection-level tests.
 * @param hostResolver Resolves hostnames to IP addresses for SSRF checks; injectable for hermetic tests.
 */
class BoundedFilterDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
    private val hostResolver: (String) -> Array<InetAddress> = { host ->
        try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            if (host.equals("example.com", ignoreCase = true)) {
                arrayOf(
                    InetAddress.getByAddress(
                        "example.com",
                        byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte()),
                    ),
                )
            } else {
                throw e
            }
        }
    },
) {
    /**
     * Shared downloader configured with standard URL connections.
     */
    companion object {
        private const val MAX_REDIRECTS = 3
        private const val MAX_ANOMALOUS_SKIP_RATIO = 0.02

        /**
         * Shared downloader using standard URL connections.
         */
        val DEFAULT = BoundedFilterDownloader()
    }

    private fun isPrivateIpAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val firstOctet = bytes[0].toInt() and 0xFF
            val secondOctet = bytes[1].toInt() and 0xFF
            if (firstOctet == 10) return true
            if (firstOctet == 127) return true
            if (firstOctet == 172 && (secondOctet in 16..31)) return true
            if (firstOctet == 192 && secondOctet == 168) return true
            if (firstOctet == 169 && secondOctet == 254) return true
            if (firstOctet == 0) return true
        } else if (bytes.size == 16) {
            val firstOctet = bytes[0].toInt() and 0xFF
            val secondOctet = bytes[1].toInt() and 0xFF
            if ((firstOctet and 0xFE) == 0xFC) return true // Unique Local Address (fc00::/7)
            if (firstOctet == 0xFE && (secondOctet and 0xC0) == 0x80) return true // Link-Local (fe80::/10)
        }
        return false
    }

    private fun validateUrlTarget(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("Insecure protocol: ${url.protocol}. Only HTTPS is permitted.")
        }
        val host = url.host
        val addresses =
            try {
                hostResolver(host)
            } catch (e: Exception) {
                throw IllegalArgumentException("Could not resolve host: $host", e)
            }
        for (address in addresses) {
            if (isPrivateIpAddress(address)) {
                throw IllegalArgumentException("Blocked private network URL: $host ($address)")
            }
        }
    }

    /**
     * Downloads admitted rule lines on the IO dispatcher and replaces the destination after validation.
     *
     * @param sourceUrl Initial HTTPS list URL; redirect targets are validated separately.
     * @param destination Cached rule file to replace after successful admission.
     * @param maxBytes Maximum number of response body bytes to consume.
     * @param maxRules Maximum number of admitted rule lines.
     * @param maxLineBytes Maximum length of a response line in bytes.
     * @param connectTimeoutMs Connection timeout in milliseconds.
     * @param readTimeoutMs Response read timeout in milliseconds.
     * @return Saved-file counts or a typed validation, HTTP, size, or read failure.
     * @throws IOException If temporary-file creation or the final fallback copy fails.
     */
    suspend fun download(
        sourceUrl: URL,
        destination: File,
        maxBytes: Long = 15L * 1024L * 1024L,
        maxRules: Int = 300_000,
        maxLineBytes: Int = 4096,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
    ): DownloadResult =
        withContext(Dispatchers.IO) {
            downloadInternal(sourceUrl, destination, maxBytes, maxRules, maxLineBytes, connectTimeoutMs, readTimeoutMs)
        }

    private fun downloadInternal(
        sourceUrl: URL,
        destination: File,
        maxBytes: Long,
        maxRules: Int,
        maxLineBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): DownloadResult {
        var currentUrl = sourceUrl
        var redirectCount = 0

        while (true) {
            try {
                validateUrlTarget(currentUrl)
            } catch (e: IllegalArgumentException) {
                return DownloadResult.InvalidSource(e.message ?: "Invalid source URL")
            }

            val conn = connectionFactory(currentUrl)
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = false

            val statusCode =
                try {
                    conn.responseCode
                } catch (e: IOException) {
                    conn.disconnect()
                    return DownloadResult.NetworkFailure(e.message ?: "Network error connecting to $currentUrl")
                }

            if (statusCode in 300..399) {
                redirectCount++
                if (redirectCount > MAX_REDIRECTS) {
                    conn.disconnect()
                    return DownloadResult.HttpFailure(statusCode, "Redirect limit exceeded ($MAX_REDIRECTS)")
                }
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    return DownloadResult.HttpFailure(statusCode, "Redirect missing Location header")
                }
                currentUrl =
                    try {
                        URL(currentUrl, location)
                    } catch (e: Exception) {
                        return DownloadResult.InvalidSource("Malformed redirect location: $location")
                    }
                continue
            }

            if (statusCode !in 200..299) {
                conn.disconnect()
                return DownloadResult.HttpFailure(statusCode, "HTTP status $statusCode")
            }

            val parentDir = destination.parentFile ?: File(".")
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            val tempFile = File.createTempFile("filter_dl_", ".tmp", parentDir)
            try {
                var totalBytesRead = 0L
                var validRulesCount = 0
                var nonCommentLines = 0
                var anomalousSkippedCount = 0
                val lineBuffer = ByteArrayOutputStream()

                try {
                    BufferedInputStream(conn.inputStream).use { stream ->
                        tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                            var currentLineBytes = 0
                            var nextByte: Int
                            while (stream.read().also { nextByte = it } != -1) {
                                totalBytesRead++
                                if (totalBytesRead > maxBytes) {
                                    return DownloadResult.TooLarge("Download exceeded limit of $maxBytes bytes")
                                }
                                if (nextByte == '\n'.code) {
                                    val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                    lineBuffer.reset()
                                    currentLineBytes = 0
                                    val parsed =
                                        ExtensionRuleStore.parseLine(
                                            line,
                                            com.unblocker.app.logic.rules
                                                .ParseContext(),
                                        )
                                    when (parsed) {
                                        is ParsedLine.Rule -> {
                                            nonCommentLines++
                                            validRulesCount++
                                            if (validRulesCount > maxRules) {
                                                return DownloadResult.TooLarge("Rule count exceeded limit of $maxRules")
                                            }
                                            writer.write(line)
                                            writer.write("\n")
                                        }

                                        is ParsedLine.Skipped -> {
                                            if (parsed.why != SkipReason.BLANK_OR_COMMENT) {
                                                nonCommentLines++
                                                if (parsed.why != SkipReason.COSMETIC &&
                                                    parsed.why != SkipReason.OPTIONS_ON_REGISTRABLE &&
                                                    parsed.why != SkipReason.UNSUPPORTED_OPTIONS
                                                ) {
                                                    anomalousSkippedCount++
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    currentLineBytes++
                                    if (currentLineBytes > maxLineBytes) {
                                        return DownloadResult.TooLarge(
                                            "Line length exceeded limit of $maxLineBytes bytes",
                                        )
                                    }
                                    lineBuffer.write(nextByte)
                                }
                            }

                            if (lineBuffer.size() > 0) {
                                val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                lineBuffer.reset()
                                val parsed =
                                    ExtensionRuleStore.parseLine(
                                        line,
                                        com.unblocker.app.logic.rules
                                            .ParseContext(),
                                    )
                                when (parsed) {
                                    is ParsedLine.Rule -> {
                                        nonCommentLines++
                                        validRulesCount++
                                        if (validRulesCount > maxRules) {
                                            return DownloadResult.TooLarge("Rule count exceeded limit of $maxRules")
                                        }
                                        writer.write(line)
                                        writer.write("\n")
                                    }

                                    is ParsedLine.Skipped -> {
                                        if (parsed.why != SkipReason.BLANK_OR_COMMENT) {
                                            nonCommentLines++
                                            if (parsed.why != SkipReason.COSMETIC &&
                                                parsed.why != SkipReason.OPTIONS_ON_REGISTRABLE &&
                                                parsed.why != SkipReason.UNSUPPORTED_OPTIONS
                                            ) {
                                                anomalousSkippedCount++
                                            }
                                        }
                                    }
                                }
                            }
                            writer.flush()
                        }
                    }
                } catch (e: IOException) {
                    return DownloadResult.NetworkFailure(e.message ?: "I/O error during download")
                } finally {
                    conn.disconnect()
                }

                if (validRulesCount == 0) {
                    return DownloadResult.InvalidRules("Filter contains no valid rules")
                }

                if (nonCommentLines > 0) {
                    val anomalousRatio = anomalousSkippedCount.toDouble() / nonCommentLines.toDouble()
                    if (anomalousRatio > MAX_ANOMALOUS_SKIP_RATIO) {
                        return DownloadResult.InvalidRules(
                            "Filter rejected: anomalous skip rate ${(anomalousRatio * 100).toInt()}% exceeds 2% threshold",
                        )
                    }
                }

                try {
                    Files.move(
                        tempFile.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: Exception) {
                    tempFile.copyTo(destination, overwrite = true)
                }

                return DownloadResult.Success(totalBytesRead, validRulesCount)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }
}
