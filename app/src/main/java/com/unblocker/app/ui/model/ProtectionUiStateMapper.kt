package com.unblocker.app.ui.model

import com.unblocker.app.services.ServiceStatus

object ProtectionUiStateMapper {

    fun from(
        status: ServiceStatus,
        privateDnsActive: Boolean,
        resolverAvailable: Boolean
    ): ProtectionUiState {
        return when (status) {
            ServiceStatus.STOPPED -> ProtectionUiState(
                label = "Off",
                detail = "Protection is off. Standard DNS queries resolve directly via your network without local filtering.",
                action = RecoveryAction.START_VPN,
                status = ServiceStatus.STOPPED
            )
            ServiceStatus.STOPPING -> ProtectionUiState(
                label = "Off",
                detail = "Stopping local VPN filter interface…",
                action = null,
                status = ServiceStatus.STOPPING
            )
            ServiceStatus.STARTING -> ProtectionUiState(
                label = "Starting",
                detail = "Initializing local DNS filter interface…",
                action = null,
                status = ServiceStatus.STARTING
            )
            ServiceStatus.ERROR -> ProtectionUiState(
                label = "Error",
                detail = "Unable to establish local VPN filter interface.",
                action = RecoveryAction.RETRY,
                status = ServiceStatus.ERROR
            )
            ServiceStatus.RUNNING -> {
                when {
                    privateDnsActive -> ProtectionUiState(
                        label = "Limited coverage (Private DNS)",
                        detail = "Android Private DNS is active and sending encrypted queries directly to external servers, bypassing local filtering.",
                        action = RecoveryAction.OPEN_PRIVATE_DNS_SETTINGS,
                        status = ServiceStatus.RUNNING
                    )
                    !resolverAvailable -> ProtectionUiState(
                        label = "Limited coverage (no DNS servers)",
                        detail = "No active network DNS resolvers were detected. Queries cannot be resolved.",
                        action = RecoveryAction.RETRY,
                        status = ServiceStatus.RUNNING
                    )
                    else -> ProtectionUiState(
                        label = "Protected (standard DNS)",
                        detail = "Filtering standard DNS queries on this device. Allowed queries resolve via network resolvers.",
                        action = null,
                        status = ServiceStatus.RUNNING
                    )
                }
            }
        }
    }
}
