package com.unblocker.app.data.repository

import com.unblocker.app.logic.rules.ExtensionRuleStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class ExtensionUpdateRollbackTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeHttpURLConnection(
        url: URL,
        private val statusCode: Int = 200,
        private val body: ByteArray = ByteArray(0),
        private val errorToThrow: Exception? = null
    ) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = statusCode
        override fun getInputStream(): InputStream {
            if (errorToThrow != null) throw errorToThrow
            return ByteArrayInputStream(body)
        }
    }

    @Test
    fun failedUpdatePreservesExistingFilterAndRules() = runBlocking {
        val rulesFile = File(tempFolder.root, "my_filter.rules")
        val originalRuleContent = "||old-blocked-domain.com^\n"
        rulesFile.writeText(originalRuleContent)

        // Verify initial rule matches
        val initialParsed = ExtensionRuleStore.parseLine("||old-blocked-domain.com^")
        assertNotNull(initialParsed)

        // Simulate failing network update with SocketTimeoutException
        val failingDownloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, errorToThrow = SocketTimeoutException("Connection reset"))
        }

        val result = failingDownloader.download(URL("https://example.com/rules.txt"), rulesFile)
        assertTrue(result is DownloadResult.NetworkFailure)

        // Verify rulesFile is untouched and still has original content
        assertEquals(originalRuleContent, rulesFile.readText())
    }

    @Test
    fun invalidRulesUpdatePreservesExistingFile() = runBlocking {
        val rulesFile = File(tempFolder.root, "my_filter.rules")
        val originalRuleContent = "0.0.0.0 ads.tracker.org\n"
        rulesFile.writeText(originalRuleContent)

        val invalidContent = "# Just a header\n# No valid rules at all\n"
        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = invalidContent.toByteArray(Charsets.UTF_8))
        }

        val result = downloader.download(URL("https://example.com/rules.txt"), rulesFile)
        assertTrue(result is DownloadResult.InvalidRules)

        // Existing rules file remains intact
        assertEquals(originalRuleContent, rulesFile.readText())
    }

    @Test
    fun oversizedUpdatePreservesExistingFile() = runBlocking {
        val rulesFile = File(tempFolder.root, "my_filter.rules")
        val originalRuleContent = "0.0.0.0 good-filter.com\n"
        rulesFile.writeText(originalRuleContent)

        val largeContent = "0.0.0.0 extra-domain.com\n".repeat(100)
        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = largeContent.toByteArray(Charsets.UTF_8))
        }

        // Limit to 50 bytes so it overflows
        val result = downloader.download(URL("https://example.com/rules.txt"), rulesFile, maxBytes = 50)
        assertTrue(result is DownloadResult.TooLarge)

        // Original file must still exist and be intact
        assertEquals(originalRuleContent, rulesFile.readText())
    }

    @Test
    fun successfulUpdateAtomicallySwapsFile() = runBlocking {
        val rulesFile = File(tempFolder.root, "my_filter.rules")
        val originalRuleContent = "0.0.0.0 old.com\n"
        rulesFile.writeText(originalRuleContent)

        val newRuleContent = "0.0.0.0 newly-blocked.com\n"
        val downloader = BoundedFilterDownloader { url ->
            FakeHttpURLConnection(url, 200, body = newRuleContent.toByteArray(Charsets.UTF_8))
        }

        val result = downloader.download(URL("https://example.com/rules.txt"), rulesFile)
        assertTrue(result is DownloadResult.Success)

        // File now contains the new rule
        assertEquals(newRuleContent, rulesFile.readText())
    }
}
