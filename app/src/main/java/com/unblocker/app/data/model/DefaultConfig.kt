package com.unblocker.app.data.model

/**
 * Bundled initial preferences applied when the preference store is first initialized.
 *
 * @property adBlockingEnabled Whether ad and tracking filtering starts enabled.
 * @property adultContentBlockingEnabled Whether adult-domain filtering starts enabled.
 * @property autoRestartOnBootEnabled Whether boot restart is requested by default.
 * @property healthCheckInterval Initial health-check interval in milliseconds.
 */
data class DefaultConfig(
    val adBlockingEnabled: Boolean = true,
    val adultContentBlockingEnabled: Boolean = true,
    val autoRestartOnBootEnabled: Boolean = true,
    val healthCheckInterval: Long = 21_600_000L
)

/**
 * Bundled application identity and initial preference payload.
 *
 * @property appName Application name supplied by configuration.
 * @property appTagline Descriptive tagline supplied by configuration.
 * @property defaultConfig Initial filtering and health-check preferences.
 */
data class AppConfigPayload(
    val appName: String = "unblocker",
    val appTagline: String = "unwanted network blocker",
    val defaultConfig: DefaultConfig = DefaultConfig()
)
