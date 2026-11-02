package com.unblocker.app.ui.logs

import com.unblocker.app.data.repository.RuleUpdateResult
import com.unblocker.app.data.repository.UserRuleGateway
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.UserFeedback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleActionTest {

    private class FakeUserRuleGateway : UserRuleGateway {
        val rules = mutableMapOf<String, UserFeedback>()

        override suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult {
            val normalized = DomainName.normalizeUserEntry(domain)
                ?: return RuleUpdateResult.Failed("Invalid domain name: $domain")

            if (feedback == UserFeedback.NONE) {
                rules.remove(normalized)
            } else {
                rules[normalized] = feedback
            }
            return RuleUpdateResult.Applied
        }
    }

    @Test
    fun fakeGatewayReceivesAllowAndBlockAndRejectsMalformed() = runBlocking {
        val gateway = FakeUserRuleGateway()

        val allowResult = gateway.set("doubleclick.net", UserFeedback.ALLOW)
        assertTrue(allowResult is RuleUpdateResult.Applied)
        assertEquals(UserFeedback.ALLOW, gateway.rules["doubleclick.net"])

        val blockResult = gateway.set("wikipedia.org", UserFeedback.BLOCK)
        assertTrue(blockResult is RuleUpdateResult.Applied)
        assertEquals(UserFeedback.BLOCK, gateway.rules["wikipedia.org"])

        val blankResult = gateway.set("   ", UserFeedback.ALLOW)
        assertTrue(blankResult is RuleUpdateResult.Failed)

        val malformedResult = gateway.set("invalid domain!#.com", UserFeedback.BLOCK)
        assertTrue(malformedResult is RuleUpdateResult.Failed)

        // Ensure no malformed rules mutated the map
        assertEquals(2, gateway.rules.size)
    }
}
