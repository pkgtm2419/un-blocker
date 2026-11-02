package com.unblocker.app.data.repository

import com.unblocker.app.logic.rules.ExtensionRuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface DownloadResult {
    data class Success(val validRuleCount: Int, val bytesRead: Long) : DownloadResult
    data class TooLarge(val message: String) : DownloadResult
    data class InvalidSource(val message: String) : DownloadResult
    data class HttpFailure(val statusCode: Int, val message: String) : DownloadResult
    data class InvalidRules(val message: String) : DownloadResult
    data class NetworkFailure(val message: String) : DownloadResult
}

class BoundedFilterDownloader(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Mozilla/5.0 unblocker/2.3")
        }
    }
) {
    suspend fun download(
        source: URL,
        destination: File,
        maxBytes: Long = 16L * 1024 * 1024,
        maxLineBytes: Int = 4096,
        maxRules: Int = 250000
    ): DownloadResult = withContext(Dispatchers.IO) {
        var currentUrl = source
        var redirectCount = 0

        while (true) {
            val validationError = validateUrl(currentUrl)
            if (validationError != null) {
                return@withContext validationError
            }

            val conn: HttpURLConnection
            try {
                conn = connectionFactory(currentUrl)
            } catch (e: IOException) {
                return@withContext DownloadResult.NetworkFailure(e.message ?: "Failed to open connection")
            }

            val statusCode: Int
            try {
                statusCode = conn.responseCode
            } catch (e: IOException) {
                conn.disconnect()
                return@withContext DownloadResult.NetworkFailure(e.message ?: "Failed to read response code")
            }

            if (statusCode in 300..399) {
                redirectCount++
                if (redirectCount > 5) {
                    conn.disconnect()
                    return@withContext DownloadResult.HttpFailure(statusCode, "Redirect limit of 5 exceeded")
                }
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    return@withContext DownloadResult.HttpFailure(statusCode, "Redirect response missing Location header")
                }
                val nextUrl = runCatching { URL(currentUrl, location) }.getOrNull()
                    ?: return@withContext DownloadResult.InvalidSource("Malformed redirect location: $location")
                currentUrl = nextUrl
                continue
            }

            if (statusCode !in 200..299) {
                conn.disconnect()
                return@withContext DownloadResult.HttpFailure(statusCode, "HTTP status $statusCode")
            }

            val parentDir = destination.parentFile ?: File(".")
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            val tempFile = File.createTempFile("filter_dl_", ".tmp", parentDir)
            try {
                var totalBytesRead = 0L
                var validRulesCount = 0
                val lineBuffer = ByteArrayOutputStream()

                try {
                    BufferedInputStream(conn.inputStream).use { stream ->
                        tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                            var currentLineBytes = 0
                            var b: Int
                            while (stream.read().also { b = it } != -1) {
                                totalBytesRead++
                                if (totalBytesRead > maxBytes) {
                                    return@withContext DownloadResult.TooLarge("Download exceeded limit of $maxBytes bytes")
                                }
                                if (b == '\n'.code) {
                                    val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                    lineBuffer.reset()
                                    currentLineBytes = 0
                                    if (ExtensionRuleStore.parseLine(line) != null) {
                                        validRulesCount++
                                        if (validRulesCount > maxRules) {
                                            return@withContext DownloadResult.TooLarge("Rule count exceeded limit of $maxRules")
                                        }
                                        writer.write(line)
                                        writer.write("\n")
                                    }
                                } else {
                                    currentLineBytes++
                                    if (currentLineBytes > maxLineBytes) {
                                        return@withContext DownloadResult.TooLarge("Line length exceeded limit of $maxLineBytes bytes")
                                    }
                                    lineBuffer.write(b)
                                }
                            }

                            if (lineBuffer.size() > 0) {
                                val line = lineBuffer.toString("UTF-8").trimEnd('\r')
                                lineBuffer.reset()
                                if (ExtensionRuleStore.parseLine(line) != null) {
                                    validRulesCount++
                                    if (validRulesCount > maxRules) {
                                        return@withContext DownloadResult.TooLarge("Rule count exceeded limit of $maxRules")
                                    }
                                    writer.write(line)
                                    writer.write("\n")
                                }
                            }
                            writer.flush()
                        }
                    }
                } catch (e: IOException) {
                    return@withContext DownloadResult.NetworkFailure(e.message ?: "I/O error during download")
                } finally {
                    conn.disconnect()
                }

                if (validRulesCount == 0) {
                    return@withContext DownloadResult.InvalidRules("Filter contains no valid rules")
                }

                try {
                    Files.move(
                        tempFile.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        tempFile.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }

                return@withContext DownloadResult.Success(validRulesCount, totalBytesRead)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadResult.NetworkFailure("Unexpected loop exit")
    }

    private fun validateUrl(url: URL): DownloadResult? {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            return DownloadResult.InvalidSource("Only HTTPS protocol is permitted, got '${url.protocol}'")
        }
        val host = url.host?.lowercase() ?: ""
        if (host.isEmpty() ||
            host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "0.0.0.0" ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.startsWith("10.") ||
            host.startsWith("192.168.")
        ) {
            return DownloadResult.InvalidSource("Local and private network endpoints are rejected")
        }
        return null
    }

    companion object {
        val DEFAULT = BoundedFilterDownloader()

        suspend fun download(
            source: URL,
            destination: File,
            maxBytes: Long = 16L * 1024 * 1024,
            maxLineBytes: Int = 4096,
            maxRules: Int = 250000
        ): DownloadResult = DEFAULT.download(source, destination, maxBytes, maxLineBytes, maxRules)
    }
}
