package com.unblocker.app.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.R
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.services.TunnelDnsConfig
import com.unblocker.app.ui.motion.FluidVisibility
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.LocalVpnStateColors

/**
 * Renders an expandable status panel inferred from service state and Private DNS inputs.
 *
 * The pipeline rows are presentation hints; this component does not probe each stage
 * or measure resolver availability.
 *
 * @param serviceStatus Service state used for status labels and pipeline indicators.
 * @param isPrivateDnsActive Whether Android reports active Private DNS.
 * @param privateDnsServerName Reported Private DNS server name, if available.
 * @param modifier Modifier for the status card.
 * @param onOpenSettings Callback for the settings shortcut.
 */
@Composable
fun AgentStatusPanel(
    serviceStatus: ServiceStatus,
    isPrivateDnsActive: Boolean,
    privateDnsServerName: String?,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val stateColors = LocalVpnStateColors.current
    var isExpanded by remember { mutableStateOf(false) }
    val isRunning = serviceStatus == ServiceStatus.RUNNING
    val protectionState =
        remember(serviceStatus, isPrivateDnsActive) {
            com.unblocker.app.ui.model.ProtectionUiStateMapper.from(
                status = serviceStatus,
                privateDnsActive = isPrivateDnsActive,
                resolverAvailable = true,
            )
        }

    SkeuomorphicCard(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .animateContentSize(animationSpec = tween(220)),
        shape = RoundedCornerShape(20.dp),
        elevation = 4.dp,
    ) {
        // Compact Status Header (always visible, clickable to toggle diagnostics)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Stepped pulse indicator pip
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(
                                color = stateColors.coreAccent,
                                shape = CircleShape,
                            ),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.agent_runtime_label),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = stateColors.badgeBackground,
                        ) {
                            Text(
                                text =
                                    when (serviceStatus) {
                                        ServiceStatus.RUNNING -> {
                                            if (isPrivateDnsActive) {
                                                stringResource(R.string.agent_badge_limited)
                                            } else {
                                                stringResource(R.string.agent_badge_active)
                                            }
                                        }

                                        ServiceStatus.STARTING -> {
                                            stringResource(R.string.agent_badge_starting)
                                        }

                                        ServiceStatus.STOPPING -> {
                                            stringResource(R.string.agent_badge_stopping)
                                        }

                                        ServiceStatus.ERROR -> {
                                            stringResource(R.string.agent_badge_fault)
                                        }

                                        ServiceStatus.STOPPED -> {
                                            stringResource(R.string.agent_badge_standby)
                                        }
                                    },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = stateColors.badgeTextColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text =
                            when (protectionState.status) {
                                ServiceStatus.RUNNING -> {
                                    if (isPrivateDnsActive && protectionState.labelRes != 0) {
                                        stringResource(protectionState.labelRes)
                                    } else {
                                        stringResource(R.string.protection_state_protected)
                                    }
                                }

                                ServiceStatus.STOPPING -> {
                                    stringResource(R.string.agent_status_stopping)
                                }

                                else -> {
                                    if (protectionState.labelRes != 0) {
                                        stringResource(protectionState.labelRes)
                                    } else {
                                        protectionState.label
                                    }
                                }
                            },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.agent_link_extensions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Expanded Multi-Stage Workflow Diagnostics
        FluidVisibility(visible = isExpanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Text(
                    text = stringResource(R.string.agent_execution_pipeline),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Step 1: Tunnel Interface
                PipelineStepRow(
                    stepNumber = "01",
                    title = stringResource(R.string.agent_step_01_title),
                    detail =
                        if (isRunning) {
                            stringResource(
                                R.string.agent_step_01_detail_running,
                                TunnelDnsConfig.IPV4_DNS,
                                TunnelDnsConfig.IPV6_DNS,
                            )
                        } else {
                            stringResource(R.string.agent_step_01_detail_offline)
                        },
                    isSuccess = isRunning,
                    isWarning = false,
                )

                // Step 2: Content Filter Engine
                PipelineStepRow(
                    stepNumber = "02",
                    title = stringResource(R.string.agent_step_02_title),
                    detail =
                        if (isRunning) {
                            stringResource(R.string.agent_step_02_detail_running)
                        } else {
                            stringResource(R.string.agent_step_02_detail_standby)
                        },
                    isSuccess = isRunning,
                    isWarning = false,
                )

                // Step 3: Upstream DNS Gateway
                PipelineStepRow(
                    stepNumber = "03",
                    title = stringResource(R.string.agent_step_03_title),
                    detail =
                        if (isPrivateDnsActive) {
                            stringResource(
                                R.string.agent_step_03_detail_private_dns,
                                privateDnsServerName.orEmpty(),
                            )
                        } else {
                            stringResource(R.string.agent_step_03_detail_system)
                        },
                    isSuccess = isRunning && !isPrivateDnsActive,
                    isWarning = isPrivateDnsActive,
                )

                // Step 4: Evidence Learner
                PipelineStepRow(
                    stepNumber = "04",
                    title = stringResource(R.string.agent_step_04_title),
                    detail = stringResource(R.string.agent_step_04_detail),
                    isSuccess = true,
                    isWarning = false,
                )

                // Private DNS Warning & Settings Shortcut if active
                if (isPrivateDnsActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF451A03) else Color(0xFFFFFBEB),
                        border =
                            androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFFD97706) else Color(0xFFF59E0B),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.agent_private_dns_warning_title),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E),
                                )
                                Text(
                                    text = stringResource(R.string.agent_private_dns_warning_body),
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFFFDE68A).copy(alpha = 0.8f) else Color(0xFFB45309),
                                )
                            }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            },
                                        )
                                    }
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.action_fix),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStepRow(
    stepNumber: String,
    title: String,
    detail: String,
    isSuccess: Boolean,
    isWarning: Boolean,
) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stepNumber,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color =
                    if (isWarning) {
                        (if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309))
                    } else if (isSuccess) {
                        (if (isDark) AllowedGreen else Color(0xFF047857))
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .background(
                        color =
                            when {
                                isWarning -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                isSuccess -> if (isDark) AllowedGreen else Color(0xFF047857)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                        shape = CircleShape,
                    ),
        )
    }
}
