package com.unblocker.app.data.model

data class DefaultConfig(
    val adBlockingEnabled: Boolean = true,
    val adultContentBlockingEnabled: Boolean = true,
    val parentalControlEnabled: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = true,
    val connectionLoggingEnabled: Boolean = true,
    val selfCheckingEnabled: Boolean = true,
    val autoRestartOnBootEnabled: Boolean = true,
    val healthCheckInterval: Long = 300_000L,
    val logRetentionDays: Int = 30,
    val notificationEnabled: Boolean = true,
    val notificationTitle: String = "unblocker is Active",
    val notificationSubtitle: String = "Blocking unwanted ads & adult network traffic",
    val updateAdListFrequency: String = "weekly",
    val updateAdultListFrequency: String = "weekly"
)

data class AppConfigPayload(
    val appVersion: String = "1.0.0",
    val appName: String = "unblocker",
    val appTagline: String = "unwanted network blocker",
    val defaultConfig: DefaultConfig = DefaultConfig()
)
