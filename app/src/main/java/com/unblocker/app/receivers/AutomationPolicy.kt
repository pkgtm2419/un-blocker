package com.unblocker.app.receivers

import java.security.MessageDigest

/**
 * Validated automation command parsed after opt-in and constant-time token verification (WP-33 / F37).
 */
enum class AutomationCommand {
    ENABLE,
    DISABLE,
    PAUSE,
    RESUME,
}

/**
 * Pure verification policy for local automation broadcasts.
 *
 * Automation is opt-in (disabled by default) and authenticates intents with a local random hex
 * token compared in constant time via [MessageDigest.isEqual].
 */
object AutomationCommandPolicy {
    const val ACTION_ENABLE: String = "com.unblocker.app.ACTION_ENABLE_PROTECTION"
    const val ACTION_DISABLE: String = "com.unblocker.app.ACTION_DISABLE_PROTECTION"
    const val ACTION_PAUSE: String = "com.unblocker.app.ACTION_PAUSE_PROTECTION"
    const val ACTION_RESUME: String = "com.unblocker.app.ACTION_RESUME_PROTECTION"
    const val EXTRA_TOKEN: String = "token"

    /**
     * Validates opt-in flag, token match, and requested [action].
     *
     * @return Matching [AutomationCommand], or null when disabled, unauthorized, or unsupported.
     */
    fun authorize(
        automationEnabled: Boolean,
        storedToken: String,
        suppliedToken: String?,
        action: String?,
    ): AutomationCommand? {
        if (!automationEnabled) return null
        if (storedToken.isBlank() || suppliedToken.isNullOrBlank()) return null

        val expectedBytes = storedToken.toByteArray(Charsets.UTF_8)
        val providedBytes = suppliedToken.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            return null
        }

        return when (action) {
            ACTION_ENABLE -> AutomationCommand.ENABLE
            ACTION_DISABLE -> AutomationCommand.DISABLE
            ACTION_PAUSE -> AutomationCommand.PAUSE
            ACTION_RESUME -> AutomationCommand.RESUME
            else -> null
        }
    }
}
