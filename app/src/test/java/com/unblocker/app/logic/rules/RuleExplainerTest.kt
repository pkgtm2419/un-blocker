package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingAction
import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.domain.model.DecisionReason
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.AdDetector
import com.unblocker.app.logic.AdultContentDetector
import com.unblocker.app.logic.analysis.AdaptiveBlockingEngine
import com.unblocker.app.logic.analysis.DefaultProtectedDomains
import com.unblocker.app.logic.analysis.LocalNetworkLearner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RuleExplainerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var explainer: RuleExplainer
    private lateinit var rulesDir: File
    private var allowlisted = emptySet<String>()
    private var blocklisted = emptySet<String>()

    @Before
    fun setup() {
        rulesDir = File(tempFolder.root, "rules").apply { mkdirs() }
        val adDetector = AdDetector(context = null).apply {
            addDomain("tracker.com")
        }
        val adultDetector = AdultContentDetector(context = null)
        val learner = LocalNetworkLearner()
        val adaptive = AdaptiveBlockingEngine(preferences = null, networkLearner = learner)
        val protected = DefaultProtectedDomains(builtIn = setOf("google.com", "docs.google.com"))

        val useCase = DecideBlockingUseCase(
            adDetector = adDetector,
            adultContentDetector = adultDetector,
            adaptiveBlockingEngine = adaptive,
            isAllowlisted = { it in allowlisted },
            isBlocklisted = { it in blocklisted },
            protectedDomains = protected
        )

        // Write a mock rules file in rulesDir
        File(rulesDir, "adguard.rules").writeText("||tracker.com^\n||adservice.net^\n")
        File(rulesDir, "easylist.rules").writeText("||tracker.com^\n")

        explainer = RuleExplainer(
            decideBlockingUseCase = useCase,
            protectedDomains = protected,
            extensionsDirSupplier = { rulesDir }
        )
    }

    @Test
    fun testExplainAdDomainInMultipleLists() = runBlocking {
        val explanation = explainer.explain("tracker.com")
        assertTrue(explanation.decision.isBlocked)
        assertEquals(DecisionReason.STATIC_RULE, explanation.decision.reasonCode)
        assertFalse(explanation.isProtected)
        assertTrue(explanation.matchingListIds.contains("adguard"))
        assertTrue(explanation.matchingListIds.contains("easylist"))
    }

    @Test
    fun testExplainProtectedDomain() = runBlocking {
        val explanation = explainer.explain("docs.google.com")
        assertFalse(explanation.decision.isBlocked)
        assertEquals(DecisionReason.PROTECTED, explanation.decision.reasonCode)
        assertTrue(explanation.isProtected)
    }

    @Test
    fun testExplainUserBlockedDomain() = runBlocking {
        blocklisted = setOf("custom-block.com")

        val explanation = explainer.explain("custom-block.com")
        assertTrue(explanation.decision.isBlocked)
        assertEquals(DecisionReason.USER_BLOCK, explanation.decision.reasonCode)
        assertEquals(BlockingCategory.CUSTOM, explanation.decision.category)
    }
}
