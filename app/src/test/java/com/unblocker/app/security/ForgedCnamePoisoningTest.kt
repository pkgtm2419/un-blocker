package com.unblocker.app.security

import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.ContentFilterEngine
import com.unblocker.app.logic.analysis.DeviceLearning
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import com.unblocker.app.logic.analysis.ReputationPolicy
import com.unblocker.app.logic.analysis.ReputationState
import com.unblocker.app.logic.rules.CompiledRuleSet
import com.unblocker.app.logic.rules.RuleAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgedCnamePoisoningTest {

    @Test
    fun testForgedCnameCannotCreatePersistentBlock() {
        val tsvFile = File("build/generated/dns-assets/dns-rules.tsv")
        assertTrue("dns-rules.tsv must exist", tsvFile.exists())
        val rules = tsvFile.reader().use { CompiledRuleSet.fromTsv(it) }
        val detector = AdDetector(compiledRules = rules)
        val evidenceStore = DeviceLearning.memoryEvidenceStore()
        val policy = ReputationPolicy(evidenceStore)
        val learner = LocalNetworkLearner(policy = policy)

        val targetDomain = "accounts.google.com"
        val forgedAliases = listOf("ad.doubleclick.net")

        val cnamePolicy = com.unblocker.app.logic.rules.CnamePolicy({ true }, { false }, detector::matchRule)

        // Simulate a hostile upstream resolver returning CNAME -> ad.doubleclick.net 100 times
        for (i in 0 until 100) {
            val blocked = cnamePolicy.blockedAlias(targetDomain, forgedAliases)
            assertEquals("ad.doubleclick.net", blocked)
        }

        // Verify that NO persistent record exists in the evidence store
        val storedRecord = evidenceStore.get(targetDomain)
        assertNull("Forged CNAME must NOT create persistent record in evidence store", storedRecord)

        // Verify that targetDomain has not become persistently CONFIRMED
        val queryScore = learner.analyzeQuery(targetDomain)
        assertFalse(
            "Target domain must not be blocked by persistent learner state: ${queryScore.state}",
            queryScore.state == ReputationState.CONFIRMED
        )
    }

    @Test
    fun testGenuineCnameCloakingBlockedPerResponse() {
        val tsvFile = File("build/generated/dns-assets/dns-rules.tsv")
        assertTrue("dns-rules.tsv must exist", tsvFile.exists())
        val rules = tsvFile.reader().use { CompiledRuleSet.fromTsv(it) }
        val detector = AdDetector(compiledRules = rules)

        // When a genuine cloaked tracker alias is present in the response
        val original = "analytics.customer-site.com"
        val aliases = listOf("cloaked.cname.net", "ad.doubleclick.net")

        val match = aliases.firstOrNull { detector.matchRule(it)?.action == RuleAction.BLOCK }
        assertNotNull("AdDetector must detect cloaked tracker in CNAME chain", match)
        assertEquals("ad.doubleclick.net", match)
    }
}
