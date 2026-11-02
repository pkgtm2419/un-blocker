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

class BoundedFilterDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeHttpURLConnection(
        url: URL,
        private val statusCode: Int = 200,
        private val headers: Map<String, String> = emptyMap(),
        private val body: ByteArray = ByteArray(0),
        private val streamException: Exception? = null
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

    @Test
    fun rejectsNonHttpsUrl() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val downloader = BoundedFilterDownloader()
        val result = downloader.download(URL("http://example.com/filter.txt"), dest)
        assertTrue("Expected InvalidSource, got $result", result is DownloadResult.InvalidSource)
    }

    @Test
    fun rejectsLocalhostTarget() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val downloader = BoundedFilterDownloader()
        val result1 = downloader.download(URL("https://127.0.0.1/filter.txt"), dest)
        assertTrue("Expected InvalidSource, got $result1", result1 is DownloadResult.InvalidSource)

        val result2 = downloader.download(URL("https://127.0.0.2/filter.txt"), dest)
        assertTrue("Expected InvalidSource for loopback, got $result2", result2 is DownloadResult.InvalidSource)

        val result3 = downloader.download(URL("https://172.16.1.1/filter.txt"), dest)
        assertTrue("Expected InvalidSource for RFC1918, got $result3", result3 is DownloadResult.InvalidSource)
    }

    @Test
    fun acceptsExactCapBytes() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val content = "0.0.0.0 ad.example.com\n"
        val bytes = content.toByteArray(Charsets.UTF_8)
        val maxBytes = bytes.size.toLong()

        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = bytes)
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxBytes = maxBytes)
        assertTrue("Expected Success, got $result", result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertEquals(1, success.validRuleCount)
        assertEquals(maxBytes, success.bytesRead)
        assertTrue(dest.exists())
    }

    @Test
    fun rejectsCapPlusOneByte() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val content = "0.0.0.0 ad.example.com\n"
        val bytes = content.toByteArray(Charsets.UTF_8)
        val maxBytes = (bytes.size - 1).toLong()

        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = bytes)
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxBytes = maxBytes)
        assertTrue("Expected TooLarge, got $result", result is DownloadResult.TooLarge)
        assertTrue(!dest.exists())
    }

    @Test
    fun rejectsLineLongerThanMaxLineBytes() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val longLine = "a".repeat(4097)
        val bytes = longLine.toByteArray(Charsets.UTF_8)

        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = bytes)
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest, maxLineBytes = 4096)
        assertTrue("Expected TooLarge, got $result", result is DownloadResult.TooLarge)
        assertTrue(!dest.exists())
    }

    @Test
    fun rejectsInsecureRedirectToHttp() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val downloader = BoundedFilterDownloader { url ->
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

    @Test
    fun rejectsRedirectMissingLocationHeader() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 301, headers = emptyMap())
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest)
        assertTrue("Expected HttpFailure, got $result", result is DownloadResult.HttpFailure)
        assertEquals(301, (result as DownloadResult.HttpFailure).statusCode)
    }

    @Test
    fun rejectsRedirectLoopExceedingLimit() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        var count = 0
        val downloader = BoundedFilterDownloader { url ->
            count++
            FakeHttpURLConnection(url, 302, headers = mapOf("Location" to "https://example.com/redirect_$count"))
        }

        val result = downloader.download(URL("https://example.com/initial"), dest)
        assertTrue("Expected HttpFailure, got $result", result is DownloadResult.HttpFailure)
        assertTrue((result as DownloadResult.HttpFailure).message.contains("Redirect limit"))
    }

    @Test
    fun timeoutLeavesExistingFileUnchanged() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val initialContent = "0.0.0.0 preserved.example.com\n"
        dest.writeText(initialContent)

        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, streamException = SocketTimeoutException("Read timed out"))
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest)
        assertTrue("Expected NetworkFailure, got $result", result is DownloadResult.NetworkFailure)
        assertEquals(initialContent, dest.readText())
    }

    @Test
    fun rejectsStreamWithNoValidRules() = runBlocking {
        val dest = File(tempFolder.root, "rules.txt")
        val emptyContent = "# Only comments\n\n# Another comment\n"

        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = emptyContent.toByteArray(Charsets.UTF_8))
        }

        val result = downloader.download(URL("https://example.com/filter.txt"), dest)
        assertTrue("Expected InvalidRules, got $result", result is DownloadResult.InvalidRules)
        assertTrue(!dest.exists())
    }
}
