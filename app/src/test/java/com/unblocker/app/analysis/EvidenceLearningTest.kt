package com.unblocker.app.analysis

import com.unblocker.app.logic.analysis.*
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * Regression coverage for evidence learning.
 */
class EvidenceLearningTest {
    private fun policy() = ReputationPolicy(PrivateEvidenceStore(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")))

    private val strong = DomainFeatures(lexical = .8f, entropy = .85f, cadence = .85f)

    /**
     * Verifies that two independent windows and multiple families required.
     */
    @Test fun twoIndependentWindowsAndMultipleFamiliesRequired() {
        val p = policy()
        assertFalse(p.observe("metrics.example.test", strong, 10).confirmed)
        repeat(20) { assertFalse(p.observe("metrics.example.test", strong, 30L + it).confirmed) }
        assertTrue(p.observe("metrics.example.test", strong, 60_010).confirmed)
        val words = policy()
        repeat(
            5,
        ) { assertFalse(words.observe("ads.example.test", DomainFeatures(lexical = 1f), it * 60_000L).confirmed) }
    }

    /**
     * Verifies that trusted evidence and feedback are explicit not immortal overrides.
     */
    @Test fun trustedEvidenceAndFeedbackAreExplicitNotImmortalOverrides() {
        val p = policy()
        assertTrue(p.trustedAlias("cloak.test").confirmed)
        assertEquals(ReputationState.SUPPRESSED, p.feedback("cloak.test", UserFeedback.ALLOW).state)
        assertFalse(p.observe("cloak.test", strong, 100).confirmed)
        p.feedback("cloak.test", UserFeedback.NONE)
        assertTrue(p.observe("cloak.test", strong, 101).confirmed)
        assertTrue(p.feedback("manual.test", UserFeedback.BLOCK).confirmed)
        assertFalse(p.feedback("manual.test", UserFeedback.NONE).confirmed)
    }

    /**
     * Verifies that cadence uses explicit monotonic clock and peek does not observe.
     */
    @Test fun cadenceUsesExplicitMonotonicClockAndPeekDoesNotObserve() {
        val c = CadenceTracker()
        repeat(3) { c.observe("tracker.test", it * 100L) }
        val before = c.peek("tracker.test", 300)
        repeat(20) { assertEquals(before, c.peek("tracker.test", 300), 0f) }
        assertTrue(c.observe("tracker.test", 300) >= .8f)
        assertEquals(0f, c.observe("tracker.test", 10), 0f)
        repeat(2100) { c.observe("n$it.test", 1000) }
        assertTrue(c.size() <= 2000)
    }

    /**
     * Verifies that scorer is pure and lexical only is weak.
     */
    @Test fun scorerIsPureAndLexicalOnlyIsWeak() {
        val scorer = HeuristicScorer()
        assertEquals(scorer.score(strong), scorer.score(strong))
        assertTrue(scorer.score(DomainFeatures(lexical = 1f)).score < .65f)
    }

    /**
     * Verifies that monotonic rollback cannot confirm another window.
     */
    @Test fun monotonicRollbackCannotConfirmAnotherWindow() {
        val p = policy()
        assertFalse(p.observe("rollback.test", strong, 10_000).confirmed)
        assertFalse(p.observe("rollback.test", strong, 100).confirmed)
    }
}
