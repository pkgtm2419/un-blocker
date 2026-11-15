package com.unblocker.app.services

/**
 * Eligibility and minimum interval for local health smoke checks.
 */
object HealthCheckPolicy {
    /**
     * Minimum scheduled health-check interval in minutes, equal to six hours.
     */
    const val DEFAULT_INTERVAL_MINUTES = 6 * 60

    /**
     * Checks whether stored protection intent permits health checks.
     *
     * @param protectionEnabled Current user protection preference.
     * @return The supplied protection-enabled value.
     */
    fun shouldRun(protectionEnabled: Boolean): Boolean = protectionEnabled
}
