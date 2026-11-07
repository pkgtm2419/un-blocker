package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.PublicSuffixRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class RuleLineParserTest {

    private val psl = PublicSuffixRules.fallback
    private val protectedHosts = setOf(
        "google.com",
        "www.google.com",
        "youtube.com",
        "www.youtube.com"
    )
    private val protectedDomains = DefaultProtectedDomains(builtIn = protectedHosts, psl = psl)
    private val admission = DefaultRuleAdmission(psl = psl, protected = protectedDomains)
    private val parser = RuleLineParser(admission = admission, psl = psl)

    @Test
    fun testAllCasesFromTsv() {
        val stream = javaClass.classLoader?.getResourceAsStream("rule-parser-cases.tsv")
            ?: throw IllegalStateException("Resource rule-parser-cases.tsv not found")

        val lines = BufferedReader(InputStreamReader(stream)).readLines()
        assertTrue("Test cases TSV must not be empty", lines.isNotEmpty())

        var count = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val parts = trimmed.split('\t')
            assertTrue("Line must have 3 tab-separated fields: $line", parts.size >= 3)
            val rawRule = parts[0]
            val categoryStr = parts[1]
            val expectedStr = parts[2]

            val category = when (categoryStr.uppercase()) {
                "SECURITY" -> BlockingCategory.AD // Or Security if available; security flag = true
                "PRIVACY" -> BlockingCategory.TRACKER
                else -> BlockingCategory.AD
            }
            val isSecurity = categoryStr.uppercase() == "SECURITY"
            val ctx = ParseContext(sourceId = 1, security = isSecurity, category = category)

            val parsed = parser.parse(rawRule, ctx)
            val actualStr = when (parsed) {
                is ParsedLine.Skipped -> "SKIP:${parsed.why.name}"
                is ParsedLine.Rule -> "RULE:${parsed.rule.action.name}:${parsed.rule.kind.name}:${parsed.rule.value}"
            }

            assertEquals("Mismatch for rule '$rawRule' (category $categoryStr)", expectedStr, actualStr)
            count++
        }
        assertTrue("Expected to run all test cases", count >= 36)
    }
}
