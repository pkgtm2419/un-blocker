package com.unblocker.app.data.model

data class DefaultConfig(
    val adBlockingEnabled: Boolean = true,
    val adultContentBlockingEnabled: Boolean = true,
    val autoRestartOnBootEnabled: Boolean = true,
    val healthCheckInterval: Long = 21_600_000L
)

data class AppConfigPayload(
    val appName: String = "unblocker",
    val appTagline: String = "unwanted network blocker",
    val defaultConfig: DefaultConfig = DefaultConfig()
)
