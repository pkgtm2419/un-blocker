package com.unblocker.app.data.repository

import com.unblocker.app.logic.analysis.UserFeedback

sealed interface RuleUpdateResult {
    data object Applied : RuleUpdateResult
    data class Failed(val reason: String) : RuleUpdateResult
}

interface UserRuleGateway {
    suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult
}
