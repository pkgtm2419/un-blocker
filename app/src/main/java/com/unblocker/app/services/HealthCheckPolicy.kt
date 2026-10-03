package com.unblocker.app.services

object HealthCheckPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 6 * 60

    fun shouldRun(protectionEnabled: Boolean): Boolean = protectionEnabled
}
