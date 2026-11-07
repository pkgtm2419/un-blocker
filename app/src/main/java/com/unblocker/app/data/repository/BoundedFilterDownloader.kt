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

sealed class DownloadResult {
    data class Success(val bytesRead: Long, val validRuleCount: Int) : DownloadResult()
    data class HttpFailure(val statusCode: Int, val message: String) : DownloadResult()
    data class NetworkFailure(val message: String) : DownloadResult()
    data class InvalidSource(val message: String) : DownloadResult()
    data class TooLarge(val message: String) : DownloadResult()
    data class InvalidRules(val message: String) : DownloadResult()
}

class BoundedFilterDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
) {

    companion object {
        val DEFAULT = BoundedFilterDownloader()
    }

    private fun isPrivateIpAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 10) return true
            if (b0 == 127) return true
            if (b0 == 172 && (b1 in 16..31)) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 0) return true
        } else if (bytes.size == 16) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if ((b0 and 0xFE) == 0xFC) return true // Unique Local Address (fc00::/7)
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true // Link-Local (fe80::/10)
        }
        return false
    }

    private fun validateUrlTarget(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("Insecure protocol: ${url.protocol}. Only HTTPS is permitted.")
        }
        val host = url.host
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not resolve host: $host", e)
        }
        for (addr in addresses) {
            if (isPrivateIpAddress(addr)) {
                throw IllegalArgumentException("Blocked private network URL: $host ($addr)")
            }
        }
    }

    suspend fun download(
        sourceUrl: URL,
        destination: File,
        maxBytes: Long = 15L * 1024L * 1024L,
        maxRules: Int = 300_000,
        maxLineBytes: Int = 4096,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000
    ): DownloadResult = withContext(Dispatchers.IO) {
        downloadInternal(sourceUrl, destination, maxBytes, maxRules, maxLineBytes, connectTimeoutMs, readTimeoutMs)
    }

    private fun downloadInternal(
        sourceUrl: URL,
        destination: File,
        maxBytes: Long,
        maxRules: Int,
        maxLineBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): DownloadResult {
        var currentUrl = sourceUrl
        var redirectCount = 0
        val maxRedirects = 3

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

            val statusCode = try {
                conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                return DownloadResult.NetworkFailure(e.message ?: "Network error connecting to $currentUrl")
            }

            if (statusCode in 300..399) {
                redirectCount++
                if (redirectCount > maxRedirects) {
                    conn.disconnect()
                    return DownloadResult.HttpFailure(statusCode, "Redirect limit exceeded ($maxRedirects)")
                }
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    return DownloadResult.HttpFailure(statusCode, "Redirect missing Location header")
                }
                currentUrl = try {
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
                            var b: Int
                            while (stream.read().also { b = it } != -1) {
                                totalBytesRead++
                                if (totalBytesRead > maxBytes) {
                                    return DownloadResult.TooLarge("Download exceeded limit of $maxBytes bytes")
                                }
                                if (b == '\n'.code) {
                                    val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                    lineBuffer.reset()
                                    currentLineBytes = 0
                                    val parsed = ExtensionRuleStore.parseLine(line, com.unblocker.app.logic.rules.ParseContext())
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
                                                    parsed.why != SkipReason.UNSUPPORTED_OPTIONS) {
                                                    anomalousSkippedCount++
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    currentLineBytes++
                                    if (currentLineBytes > maxLineBytes) {
                                        return DownloadResult.TooLarge("Line length exceeded limit of $maxLineBytes bytes")
                                    }
                                    lineBuffer.write(b)
                                }
                            }

                            if (lineBuffer.size() > 0) {
                                val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                lineBuffer.reset()
                                val parsed = ExtensionRuleStore.parseLine(line, com.unblocker.app.logic.rules.ParseContext())
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
                                                parsed.why != SkipReason.UNSUPPORTED_OPTIONS) {
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
                    if (anomalousRatio > 0.02) {
                        return DownloadResult.InvalidRules(
                            "Filter rejected: anomalous skip rate ${(anomalousRatio * 100).toInt()}% exceeds 2% threshold"
                        )
                    }
                }

                try {
                    Files.move(
                        tempFile.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
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
