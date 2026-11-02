package com.unblocker.app.ui.model

import com.unblocker.app.services.ServiceStatus

enum class RecoveryAction {
    OPEN_PRIVATE_DNS_SETTINGS,
    RETRY,
    START_VPN
}

/**
 * Transparent, truthful protection UI state.
 * Never claims 100% protection or DoH interception.
 */
data class ProtectionUiState(
    val label: String,
    val detail: String,
    val action: RecoveryAction?,
    val status: ServiceStatus
)
