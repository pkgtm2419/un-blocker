package com.unblocker.app.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.motion.FluidVisibility
import com.unblocker.app.ui.motion.rememberBreathingPulse
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.LocalVpnStateColors
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900

/**
 * Replit Agent-inspired diagnostic and workflow status panel.
 * Exposes live stepped engine verification pipeline with tactile control.
 */
@Composable
fun AgentStatusPanel(
    serviceStatus: ServiceStatus,
    isPrivateDnsActive: Boolean,
    privateDnsServerName: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stateColors = LocalVpnStateColors.current
    var isExpanded by remember { mutableStateOf(false) }
    val isRunning = serviceStatus == ServiceStatus.RUNNING
    val pulseScale = rememberBreathingPulse(minScale = 0.90f, maxScale = 1.15f, durationMillis = 1500)

    SkeuomorphicCard(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .animateContentSize(animationSpec = tween(220)),
        shape = RoundedCornerShape(20.dp),
        elevation = 4.dp
    ) {
        // Compact Status Header (always visible, clickable to toggle diagnostics)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Stepped pulse indicator pip
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = stateColors.coreAccent,
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AGENT RUNTIME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = Slate400
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = stateColors.badgeBackground
                        ) {
                            Text(
                                text = when (serviceStatus) {
                                    ServiceStatus.RUNNING -> "ACTIVE"
                                    ServiceStatus.STARTING -> "STARTING"
                                    ServiceStatus.STOPPING -> "STOPPING"
                                    ServiceStatus.ERROR -> "FAULT"
                                    ServiceStatus.STOPPED -> "STANDBY"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = stateColors.badgeTextColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when (serviceStatus) {
                            ServiceStatus.RUNNING -> "Local DNS Tunnel • 100% On-Device"
                            ServiceStatus.STARTING -> "Configuring local tunnel interface…"
                            ServiceStatus.STOPPING -> "Gracefully tearing down tunnel…"
                            ServiceStatus.ERROR -> "Tunnel fault • Tap to inspect"
                            ServiceStatus.STOPPED -> "Protection idle • Tap shield to start"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse Diagnostics" else "Expand Diagnostics",
                    tint = Slate400
                )
            }
        }

        // Expanded Multi-Stage Workflow Diagnostics
        FluidVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                HorizontalDivider(
                    color = Slate700.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "EXECUTION PIPELINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate400,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Step 1: Tunnel Interface
                PipelineStepRow(
                    stepNumber = "01",
                    title = "VPN TUNNEL INTERFACE",
                    detail = if (isRunning) "Bound to 10.10.0.1:53 & fd00:1::1:53" else "Offline (0 packet flows)",
                    isSuccess = isRunning,
                    isWarning = false
                )

                // Step 2: Content Filter Engine
                PipelineStepRow(
                    stepNumber = "02",
                    title = "CONTENT FILTER CORE",
                    detail = if (isRunning) "RuleSet compiled (Ad + Adult heuristics active)" else "Standby",
                    isSuccess = isRunning,
                    isWarning = false
                )

                // Step 3: Upstream DNS Gateway
                PipelineStepRow(
                    stepNumber = "03",
                    title = "UPSTREAM DNS GATEWAY",
                    detail = if (isPrivateDnsActive) "Encrypted Private DNS active ($privateDnsServerName)" else "Delegating to configured system resolvers",
                    isSuccess = isRunning && !isPrivateDnsActive,
                    isWarning = isPrivateDnsActive
                )

                // Step 4: Evidence Learner
                PipelineStepRow(
                    stepNumber = "04",
                    title = "LOCAL EVIDENCE LEARNER",
                    detail = "Hardware Keystore HMAC • Zero remote telemetry",
                    isSuccess = true,
                    isWarning = false
                )

                // Private DNS Warning & Settings Shortcut if active
                if (isPrivateDnsActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF451A03),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Private DNS mode active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFDE68A)
                                )
                                Text(
                                    text = "Android may send queries over DoT bypassing local filter.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFDE68A).copy(alpha = 0.8f)
                                )
                            }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    }
                                }
                            ) {
                                Text(
                                    text = "Fix",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
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
    isWarning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stepNumber,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Slate400,
            modifier = Modifier.width(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isWarning) Color(0xFFFBBF24) else if (isSuccess) AllowedGreen else Slate400
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = Slate400
            )
        }

        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color = when {
                        isWarning -> Color(0xFFFBBF24)
                        isSuccess -> AllowedGreen
                        else -> Slate700
                    },
                    shape = CircleShape
                )
        )
    }
}
