package com.unblocker.app.regression

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class PrimaryHostsRegressionTest {

    private lateinit var decideBlockingUseCase: DecideBlockingUseCase
    private val primaryHosts = mutableListOf<String>()

    @Before
    fun setup() {
        val stream = javaClass.classLoader?.getResourceAsStream("primary-hosts.tsv")
            ?: throw IllegalStateException("primary-hosts.tsv not found in resources")
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { line ->
                    line.split(Regex("\\s+")).forEach { host ->
                        if (host.isNotBlank()) primaryHosts.add(host.trim().lowercase())
                    }
                }
        }

        val adDetector = AdDetector(context = null)
        val adultDetector = AdultContentDetector(context = null)
        val learner = LocalNetworkLearner()
        val adaptive = AdaptiveBlockingEngine(preferences = null, networkLearner = learner)
        val protected = DefaultProtectedDomains()

        decideBlockingUseCase = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultDetector,
            adaptiveBlockingEngine = adaptive,
            isAllowlisted = { false },
            isBlocklisted = { false },
            protectedDomains = protected,
            blockingMode = { BlockingMode.SMART }
        )
    }

    @Test
    fun testAllPrimaryHostsResolveToAllowInSmartMode() = runBlocking {
        assertTrue("Corpus must not be empty", primaryHosts.isNotEmpty())
        for (host in primaryHosts) {
            val decision = decideBlockingUseCase(host)
            assertFalse(
                "Primary host '$host' must not be blocked! Decided: ${decision.action} with reason ${decision.reason}",
                decision.isBlocked
            )
        }
    }
}
