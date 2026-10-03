package com.unblocker.app.quality

import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.rules.CompiledRuleSet
import com.unblocker.app.logic.rules.RuleSet
import com.unblocker.app.logic.rules.toRuleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Coverage evaluation harness implementing ANTIGRAVITY_UNBLOCKER_SPEC Section 6.
 *
 * Evaluates the compiled RuleSet against a labeled host corpus (AD, TRACKER, NEEDED, OTHER).
 * Outputs recall on AD+TRACKER and gates false positives on NEEDED hosts strictly at 0.
 */
class CoverageReportTest {

    @Test
    fun evaluateCoverageAndZeroFalsePositivesOnNeeded() {
        val binFile = File("build/generated/dns-assets/dns-rules.bin")
        val ruleSet: RuleSet = if (binFile.exists()) {
            RuleSet.fromByteArray(binFile.readBytes())
        } else {
            val tsvFile = File("build/generated/dns-assets/dns-rules.tsv")
            tsvFile.reader().use { CompiledRuleSet.fromTsv(it) }.toRuleSet()
        }

        val adDetector = AdDetector(ruleSet = ruleSet)
        val adultDetector = AdultContentDetector()
        val pipeline = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultDetector,
            adaptiveBlockingEngine = AdaptiveBlockingEngine(null, LocalNetworkLearner()),
            isAdBlockingEnabled = { true },
            isAdultBlockingEnabled = { false }
        )

        val stream = javaClass.getResourceAsStream("/labeled-hosts.csv")
            ?: error("labeled-hosts.csv test resource not found")

        val lines = stream.bufferedReader().use { it.readLines() }
        val header = lines.firstOrNull() ?: error("Empty labeled-hosts.csv")
        require(header.startsWith("host,count,label")) { "Invalid CSV header: $header" }

        var totalAd = 0
        var blockedAd = 0
        var totalTracker = 0
        var blockedTracker = 0
        var totalNeeded = 0
        var blockedNeeded = 0
        var totalOther = 0
        var blockedOther = 0

        val results = mutableListOf<String>()

        for (line in lines.drop(1)) {
            if (line.isBlank() || line.startsWith("#")) continue
            val parts = line.split(',')
            if (parts.size < 3) continue
            val host = parts[0].trim()
            val count = parts[1].trim().toIntOrNull() ?: 1
            val label = parts[2].trim().uppercase()

            val decision = pipeline(host)
            val isBlocked = decision.isBlocked
            val reason = decision.reason

            results.add(String.format("%-35s %5d  %-8s  %-7s  %s", host, count, label, if (isBlocked) "BLOCKED" else "ALLOWED", reason))

            when (label) {
                "AD" -> {
                    totalAd++
                    if (isBlocked) blockedAd++
                }
                "TRACKER" -> {
                    totalTracker++
                    if (isBlocked) blockedTracker++
                }
                "NEEDED" -> {
                    totalNeeded++
                    if (isBlocked) blockedNeeded++
                }
                else -> {
                    totalOther++
                    if (isBlocked) blockedOther++
                }
            }
        }

        val totalAdTracker = totalAd + totalTracker
        val blockedAdTracker = blockedAd + blockedTracker
        val recall = if (totalAdTracker > 0) (blockedAdTracker.toDouble() / totalAdTracker) * 100.0 else 0.0

        val reportDir = File("build/reports/coverage")
        reportDir.mkdirs()
        val reportFile = File(reportDir, "coverage-report.txt")

        val reportSummary = buildString {
            appendLine("================================================================================")
            appendLine("UN-BLOCKER COVERAGE REPORT (ANTIGRAVITY_UNBLOCKER_SPEC Section 6)")
            appendLine("================================================================================")
            appendLine(String.format("Rules in binary RuleSet: %d", ruleSet.ruleCount))
            appendLine(String.format("AD Hosts:                %d / %d blocked (%.1f%%)", blockedAd, totalAd, if (totalAd > 0) blockedAd * 100.0 / totalAd else 0.0))
            appendLine(String.format("TRACKER Hosts:           %d / %d blocked (%.1f%%)", blockedTracker, totalTracker, if (totalTracker > 0) blockedTracker * 100.0 / totalTracker else 0.0))
            appendLine(String.format("AD + TRACKER Recall:     %d / %d (%.2f%%)", blockedAdTracker, totalAdTracker, recall))
            appendLine(String.format("NEEDED Hosts:            %d (False Positives: %d)", totalNeeded, blockedNeeded))
            appendLine(String.format("OTHER Hosts:             %d (Blocked: %d)", totalOther, blockedOther))
            appendLine("================================================================================")
            appendLine("DETAILS:")
            appendLine(String.format("%-35s %5s  %-8s  %-7s  %s", "HOST", "COUNT", "LABEL", "STATUS", "REASON"))
            appendLine("-".repeat(80))
            results.forEach { appendLine(it) }
            appendLine("================================================================================")
        }

        reportFile.writeText(reportSummary)
        println(reportSummary)

        assertTrue("Corpus must include at least 15 AD+TRACKER hosts", totalAdTracker >= 15)
        assertTrue("Corpus must include at least 15 NEEDED hosts", totalNeeded >= 15)
        assertEquals("False positives on NEEDED hosts must be 0", 0, blockedNeeded)
    }
}
