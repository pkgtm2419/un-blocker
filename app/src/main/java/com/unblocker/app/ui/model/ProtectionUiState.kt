package com.unblocker.app.ui.model

import androidx.annotation.StringRes
import com.unblocker.app.services.ServiceStatus

/**
 * UI recovery actions offered for stopped, failed, or limited-coverage protection states.
 */
enum class RecoveryAction {
    OPEN_PRIVATE_DNS_SETTINGS,
    RETRY,
    START_VPN,
}

/**
 * Protection label and recovery hint derived from service and coverage signals.
 *
 * @property label Short state label shown to the user.
 * @property detail Explanation of the state and known coverage limitations.
 * @property action Suggested recovery action, or null when no action is offered.
 * @property status Service status associated with this presentation.
 * @property labelRes String resource backing [label] for localized Compose UI rendering, or 0 if unset.
 * @property detailRes String resource backing [detail] for localized Compose UI rendering, or 0 if unset.
 */
data class ProtectionUiState(
    val label: String,
    val detail: String,
    val action: RecoveryAction?,
    val status: ServiceStatus,
    @param:StringRes val labelRes: Int = 0,
    @param:StringRes val detailRes: Int = 0,
)
