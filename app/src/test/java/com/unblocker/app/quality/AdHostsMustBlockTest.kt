package com.unblocker.app.quality

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.rules.ExtensionRuleStore
import com.unblocker.app.logic.rules.RuleSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies SMART-mode blocking against the WP-00 `must-block-hosts.tsv` regression corpus
 * with zero user overrides across generated `dns-rules.bin`, `never-block-hosts.txt`, and `bundled_rules.txt`.
 */
class AdHostsMustBlockTest {
    /**
     * Clears any extension snapshot loaded for test assertions.
     */
    @After
    fun tearDown() {
        ExtensionRuleStore.clearForTesting()
    }

    /**
     * Loads the compiled binary rule set, never-block asset, and bundled extension seed rules,
     * then checks that every host in `must-block-hosts.tsv` resolves to [BlockingAction.BLOCK].
     */
    @Test
    fun smartModeBlocksAllMustBlockHostsWithCompiledAndBundledRules() {
        val hosts = loadMustBlockHosts()
        assertTrue("must-block-hosts.tsv must contain hosts", hosts.isNotEmpty())

        val dnsAssetsDir = resolveDnsAssetsDir()
        val ruleSet = RuleSet.fromByteArray(File(dnsAssetsDir, "dns-rules.bin").readBytes())
        assertTrue(
            "Compiled dns-rules.bin must contain full rule corpus, got ${ruleSet.ruleCount}",
            ruleSet.ruleCount > 10_000,
        )
        loadBundledExtensionRules()

        val useCase = buildSmartUseCase(ruleSet, loadNeverBlockHosts(dnsAssetsDir))
        val allowedUnexpectedly = collectUnblocked(hosts, useCase)

        assertEquals(
            "Expected all must-block hosts to be blocked in SMART mode with compiled + bundled rules",
            emptyList<String>(),
            allowedUnexpectedly,
        )
    }

    /**
     * Checks that the compiled `dns-rules.bin` rule set alone (without `bundled_rules.txt`)
     * blocks every host in `must-block-hosts.tsv` in SMART mode.
     */
    @Test
    fun smartModeBlocksAllMustBlockHostsWithCompiledRuleSetAlone() {
        ExtensionRuleStore.clearForTesting()
        val hosts = loadMustBlockHosts()
        val dnsAssetsDir = resolveDnsAssetsDir()
        val ruleSet = RuleSet.fromByteArray(File(dnsAssetsDir, "dns-rules.bin").readBytes())
        assertTrue(
            "Compiled dns-rules.bin must contain full rule corpus, got ${ruleSet.ruleCount}",
            ruleSet.ruleCount > 10_000,
        )

        val useCase = buildSmartUseCase(ruleSet, loadNeverBlockHosts(dnsAssetsDir))
        val allowedUnexpectedly = collectUnblocked(hosts, useCase)

        assertEquals(
            "Expected all must-block hosts to be blocked in SMART mode by compiled dns-rules.bin alone",
            emptyList<String>(),
            allowedUnexpectedly,
        )
    }

    private fun buildSmartUseCase(
        ruleSet: RuleSet?,
        neverBlockHosts: Set<String>,
    ): DecideBlockingUseCase =
        DecideBlockingUseCase(
            adDetector = AdDetector(context = null, ruleSet = ruleSet),
            adultContentDetector = AdultContentDetector(context = null),
            adaptiveBlockingEngine = AdaptiveBlockingEngine(preferences = null, networkLearner = LocalNetworkLearner()),
            isAllowlisted = { false },
            isBlocklisted = { false },
            isAdBlockingEnabled = { true },
            isAdultBlockingEnabled = { true },
            isPaused = { false },
            blockingMode = { BlockingMode.SMART },
            protectedDomains = DefaultProtectedDomains(builtIn = neverBlockHosts),
        )

    private fun collectUnblocked(
        hosts: List<String>,
        useCase: DecideBlockingUseCase,
    ): List<String> =
        hosts
            .map { host -> host to useCase(host) }
            .filter { (_, decision) -> !decision.isBlocked }
            .map { (host, decision) -> "$host -> ${decision.action} (${decision.reasonCode}: ${decision.reason})" }

    private fun loadNeverBlockHosts(dnsAssetsDir: File): Set<String> =
        File(dnsAssetsDir, "never-block-hosts.txt")
            .readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    private fun loadMustBlockHosts(): List<String> {
        val stream =
            requireNotNull(javaClass.classLoader?.getResourceAsStream("must-block-hosts.tsv")) {
                "Missing test resource must-block-hosts.tsv"
            }
        return stream
            .bufferedReader()
            .useLines { lines ->
                lines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { it.split('\t').first().trim() }
                    .toList()
            }
    }

    private fun resolveDnsAssetsDir(): File {
        val candidates =
            listOf(
                File("app/build/generated/dns-assets"),
                File("build/generated/dns-assets"),
            )
        return candidates.firstOrNull { File(it, "dns-rules.bin").isFile }
            ?: error("Generated dns-assets not found in candidates: $candidates")
    }

    private fun loadBundledExtensionRules() {
        val candidates =
            listOf(
                File("app/src/main/assets/extensions/bundled_rules.txt"),
                File("src/main/assets/extensions/bundled_rules.txt"),
            )
        val file =
            candidates.firstOrNull { it.isFile }
                ?: error("bundled_rules.txt not found in candidates: $candidates")
        val builder = ExtensionRuleStore.Builder()
        file.forEachLine { line -> builder.addLine(line) }
        ExtensionRuleStore.applyBuilder(builder)
    }
}
