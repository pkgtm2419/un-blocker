package com.unblocker.app.ui.model

import com.unblocker.app.R
import com.unblocker.app.services.ServiceStatus

/**
 * Maps service and DNS coverage signals to protection labels and recovery actions.
 */
object ProtectionUiStateMapper {
    /**
     * Derives a presentation with Private DNS warnings taking priority over missing resolvers while running.
     *
     * @param status Current service status.
     * @param privateDnsActive Whether Android reports active Private DNS.
     * @param resolverAvailable Whether a usable upstream network resolver is available.
     * @return Protection label, detail, and an optional recovery action.
     */
    fun from(
        status: ServiceStatus,
        privateDnsActive: Boolean,
        resolverAvailable: Boolean,
    ): ProtectionUiState =
        when (status) {
            ServiceStatus.STOPPED -> {
                ProtectionUiState(
                    label = "Off",
                    detail = "Protection is off. Standard DNS queries resolve directly via your network without local filtering.",
                    action = RecoveryAction.START_VPN,
                    status = ServiceStatus.STOPPED,
                    labelRes = R.string.protection_state_off,
                    detailRes = R.string.protection_state_off_detail,
                )
            }

            ServiceStatus.STOPPING -> {
                ProtectionUiState(
                    label = "Off",
                    detail = "Stopping local VPN filter interface…",
                    action = null,
                    status = ServiceStatus.STOPPING,
                    labelRes = R.string.protection_state_off,
                    detailRes = R.string.protection_state_stopping_detail,
                )
            }

            ServiceStatus.STARTING -> {
                ProtectionUiState(
                    label = "Starting",
                    detail = "Initializing local DNS filter interface…",
                    action = null,
                    status = ServiceStatus.STARTING,
                    labelRes = R.string.protection_state_starting,
                    detailRes = R.string.protection_state_starting_detail,
                )
            }

            ServiceStatus.ERROR -> {
                ProtectionUiState(
                    label = "Error",
                    detail = "Unable to establish local VPN filter interface.",
                    action = RecoveryAction.RETRY,
                    status = ServiceStatus.ERROR,
                    labelRes = R.string.protection_state_error,
                    detailRes = R.string.protection_state_error_detail,
                )
            }

            ServiceStatus.RUNNING -> {
                when {
                    privateDnsActive -> {
                        ProtectionUiState(
                            label = "Limited coverage (Private DNS)",
                            detail = "Android Private DNS is active and sending encrypted queries directly to external servers, bypassing local filtering.",
                            action = RecoveryAction.OPEN_PRIVATE_DNS_SETTINGS,
                            status = ServiceStatus.RUNNING,
                            labelRes = R.string.protection_state_limited_private_dns,
                            detailRes = R.string.protection_state_limited_private_dns_detail,
                        )
                    }

                    !resolverAvailable -> {
                        ProtectionUiState(
                            label = "Limited coverage (no DNS servers)",
                            detail = "No active network DNS resolvers were detected. Queries cannot be resolved.",
                            action = RecoveryAction.RETRY,
                            status = ServiceStatus.RUNNING,
                            labelRes = R.string.protection_state_limited_no_dns,
                            detailRes = R.string.protection_state_limited_no_dns_detail,
                        )
                    }

                    else -> {
                        ProtectionUiState(
                            label = "Protected (standard DNS)",
                            detail = "Filtering standard DNS queries on this device. Allowed queries resolve via network resolvers.",
                            action = null,
                            status = ServiceStatus.RUNNING,
                            labelRes = R.string.protection_state_protected,
                            detailRes = R.string.protection_state_protected_detail,
                        )
                    }
                }
            }
        }
}
