package com.unblocker.app.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Regression coverage for bounded filter downloader.
 */
class BoundedFilterDownloaderTest {
    /**
     * Temporary filesystem fixture isolated and cleaned up by JUnit.
     */
    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeHttpURLConnection(
        url: URL,
        private val statusCode: Int = 200,
        private val headers: Map<String, String> = emptyMap(),
        private val body: ByteArray = ByteArray(0),
        private val streamException: Exception? = null,
    ) : HttpURLConnection(url) {
        override fun connect() {}

        override fun disconnect() {}

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = statusCode

        override fun getHeaderField(name: String?): String? = headers[name]

        override fun getInputStream(): InputStream {
            if (streamException != null) throw streamException
            return ByteArrayInputStream(body)
        }
    }

    private val hermeticHostResolver: (String) -> Array<java.net.InetAddress> = { host ->
        when (host) {
            "127.0.0.1" -> {
                arrayOf(java.net.InetAddress.getByAddress("127.0.0.1", byteArrayOf(127, 0, 0, 1)))
            }

            "127.0.0.2" -> {
                arrayOf(java.net.InetAddress.getByAddress("127.0.0.2", byteArrayOf(127, 0, 0, 2)))
            }

            "172.16.1.1" -> {
                arrayOf(
                    java.net.InetAddress.getByAddress(
                        "172.16.1.1",
                        byteArrayOf(172.toByte(), 16, 1, 1),
                    ),
                )
            }

            else -> {
                arrayOf(
                    java.net.InetAddress.getByAddress(
                        host,
                        byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte()),
                    ),
                )
            }
        }
    }

    private fun hermeticDownloader(
        connectionFactory: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
    ): BoundedFilterDownloader = BoundedFilterDownloader(connectionFactory, hermeticHostResolver)

    /**
     * Verifies that rejects non https URL.
     */
    @Test
    fun rejectsNonHttpsUrl() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val downloader = hermeticDownloader()
            val result = downloader.download(URL("http://example.com/filter.txt"), dest)
            assertTrue("Expected InvalidSource, got $result", result is DownloadResult.InvalidSource)
        }

    /**
     * Verifies that rejects localhost target.
     */
    @Test
    fun rejectsLocalhostTarget() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val downloader = hermeticDownloader()
            val result1 = downloader.download(URL("https://127.0.0.1/filter.txt"), dest)
            assertTrue("Expected InvalidSource, got $result1", result1 is DownloadResult.InvalidSource)

            val result2 = downloader.download(URL("https://127.0.0.2/filter.txt"), dest)
            assertTrue("Expected InvalidSource for loopback, got $result2", result2 is DownloadResult.InvalidSource)

            val result3 = downloader.download(URL("https://172.16.1.1/filter.txt"), dest)
            assertTrue("Expected InvalidSource for RFC1918, got $result3", result3 is DownloadResult.InvalidSource)
        }

    /**
     * Verifies that accepts exact cap bytes.
     */
    @Test
    fun acceptsExactCapBytes() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val content = "0.0.0.0 ad.example.com\n"
            val bytes = content.toByteArray(Charsets.UTF_8)
            val maxBytes = bytes.size.toLong()

            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 200, body = bytes)
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxBytes = maxBytes)
            assertTrue("Expected Success, got $result", result is DownloadResult.Success)
            val success = result as DownloadResult.Success
            assertEquals(1, success.validRuleCount)
            assertEquals(maxBytes, success.bytesRead)
            assertTrue(dest.exists())
        }

    /**
     * Verifies that rejects cap plus one byte.
     */
    @Test
    fun rejectsCapPlusOneByte() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val content = "0.0.0.0 ad.example.com\n"
            val bytes = content.toByteArray(Charsets.UTF_8)
            val maxBytes = (bytes.size - 1).toLong()

            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 200, body = bytes)
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxBytes = maxBytes)
            assertTrue("Expected TooLarge, got $result", result is DownloadResult.TooLarge)
            assertTrue(!dest.exists())
        }

    /**
     * Verifies that rejects line longer than max line bytes.
     */
    @Test
    fun rejectsLineLongerThanMaxLineBytes() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val longLine = "a".repeat(4097)
            val bytes = longLine.toByteArray(Charsets.UTF_8)

            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 200, body = bytes)
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxLineBytes = 4096)
            assertTrue("Expected TooLarge, got $result", result is DownloadResult.TooLarge)
            assertTrue(!dest.exists())
        }

    /**
     * Verifies that rejects insecure redirect to http.
     */
    @Test
    fun rejectsInsecureRedirectToHttp() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val downloader =
                hermeticDownloader { url ->
                    if (url.toString() == "https://example.com/initial") {
                        FakeHttpURLConnection(url, 302, headers = mapOf("Location" to "http://example.com/insecure"))
                    } else {
                        FakeHttpURLConnection(url, 200, body = "0.0.0.0 ad.com\n".toByteArray())
                    }
                }

            val result = downloader.download(URL("https://example.com/initial"), dest)
            assertTrue("Expected InvalidSource, got $result", result is DownloadResult.InvalidSource)
            assertTrue(!dest.exists())
        }

    /**
     * Verifies that rejects redirect missing location header.
     */
    @Test
    fun rejectsRedirectMissingLocationHeader() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 301, headers = emptyMap())
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest)
            assertTrue("Expected HttpFailure, got $result", result is DownloadResult.HttpFailure)
            assertEquals(301, (result as DownloadResult.HttpFailure).statusCode)
        }

    /**
     * Verifies that rejects redirect loop exceeding limit.
     */
    @Test
    fun rejectsRedirectLoopExceedingLimit() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            var count = 0
            val downloader =
                hermeticDownloader { url ->
                    count++
                    FakeHttpURLConnection(
                        url,
                        302,
                        headers = mapOf("Location" to "https://example.com/redirect_$count"),
                    )
                }

            val result = downloader.download(URL("https://example.com/initial"), dest)
            assertTrue("Expected HttpFailure, got $result", result is DownloadResult.HttpFailure)
            assertTrue((result as DownloadResult.HttpFailure).message.contains("Redirect limit"))
        }

    /**
     * Verifies that timeout leaves existing file unchanged.
     */
    @Test
    fun timeoutLeavesExistingFileUnchanged() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val initialContent = "0.0.0.0 preserved.example.com\n"
            dest.writeText(initialContent)

            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 200, streamException = SocketTimeoutException("Read timed out"))
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest)
            assertTrue("Expected NetworkFailure, got $result", result is DownloadResult.NetworkFailure)
            assertEquals(initialContent, dest.readText())
        }

    /**
     * Verifies that rejects stream with no valid rules.
     */
    @Test
    fun rejectsStreamWithNoValidRules() =
        runBlocking {
            val dest = File(tempFolder.root, "rules.txt")
            val emptyContent = "# Only comments\n\n# Another comment\n"

            val downloader =
                hermeticDownloader { url ->
                    FakeHttpURLConnection(url, 200, body = emptyContent.toByteArray(Charsets.UTF_8))
                }

            val result = downloader.download(URL("https://example.com/filter.txt"), dest)
            assertTrue("Expected InvalidRules, got $result", result is DownloadResult.InvalidRules)
            assertTrue(!dest.exists())
        }
}
