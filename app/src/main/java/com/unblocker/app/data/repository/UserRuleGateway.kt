package com.unblocker.app.data.repository

import com.unblocker.app.logic.analysis.UserFeedback

/**
 * Outcome of an attempted user rule or protected-site update.
 */
sealed interface RuleUpdateResult {
    /**
     * The requested update completed without a reported persistence failure.
     */
    data object Applied : RuleUpdateResult
    /**
     * The update was rejected or could not be saved.
     *
     * @property reason User-facing explanation of the failure.
     */
    data class Failed(val reason: String) : RuleUpdateResult
}

/**
 * Boundary for applying private user rules and maintaining the separate protected-site list.
 */
interface UserRuleGateway {
    /**
     * Updates an exact-domain override.
     *
     * @param domain User-entered host to normalize.
     * @param feedback Allow, block, or remove the override.
     * @return Whether validation and persistence succeeded.
     */
    suspend fun set(domain: String, feedback: UserFeedback): RuleUpdateResult
    /**
     * Updates an override for a registrable site and its descendants.
     *
     * @param domain User-entered host used to identify the site.
     * @param feedback Allow, block, or remove the site override.
     * @return Whether validation and persistence succeeded.
     */
    suspend fun setWholeSite(domain: String, feedback: UserFeedback): RuleUpdateResult
    /**
     * Adds a site to the separate My domains protection list.
     *
     * @param domain User-entered host used to identify the site.
     * @return Whether the site was added.
     */
    suspend fun protectDomain(domain: String): RuleUpdateResult
    /**
     * Removes exact and site overrides and related protected-site entries.
     *
     * @param domain User-entered host whose overrides should be removed.
     * @return Whether the reset completed.
     */
    suspend fun reset(domain: String): RuleUpdateResult
}
