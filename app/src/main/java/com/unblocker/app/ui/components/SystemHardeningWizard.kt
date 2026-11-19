package com.unblocker.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.services.HardeningPosture
import com.unblocker.app.services.SystemHardeningChecker
import com.unblocker.app.ui.theme.AdBlockedRed
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.TealPrimary

/**
 * Interactive checklist guiding users through Android OS settings to prevent leaks and process death.
 *
 * @param posture Current snapshot of battery optimization and Private DNS states.
 * @param modifier Layout modifier applied to the root card container.
 * @param onRefreshRequested Invoked when settings changes occur to trigger a fresh system posture check.
 */
@Composable
fun SystemHardeningWizard(
    posture: HardeningPosture,
    modifier: Modifier = Modifier,
    onRefreshRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    SkeuomorphicCard(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = TealPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "System Hardening Checklist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (posture.isFullyHardened) "Optimal defense active" else "Action recommended",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (posture.isFullyHardened) AllowedGreen else AdBlockedRed,
                            fontSize = 11.sp,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                        if (posture.isFullyHardened) {
                            AllowedGreen.copy(alpha = 0.15f)
                        } else {
                            AdBlockedRed.copy(alpha = 0.15f)
                        },
                    border =
                        BorderStroke(
                            1.dp,
                            if (posture.isFullyHardened) {
                                AllowedGreen.copy(alpha = 0.4f)
                            } else {
                                AdBlockedRed.copy(alpha = 0.4f)
                            },
                        ),
                ) {
                    Text(
                        text = if (posture.isFullyHardened) "SECURE" else "VULNERABLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (posture.isFullyHardened) AllowedGreen else AdBlockedRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text =
                    "Android battery killers and encrypted Private DNS can silently terminate " +
                        "the VPN or bypass local ad blocking. Ensure these 4 pillars are configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Item 1: Battery Optimization
            HardeningItemRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "Battery Optimization",
                description =
                    if (posture.isBatteryOptimizationIgnored) {
                        "Unrestricted — safe from OEM background task killer"
                    } else {
                        "Optimized — system may stop VPN in background"
                    },
                isSuccess = posture.isBatteryOptimizationIgnored,
                actionLabel = if (posture.isBatteryOptimizationIgnored) "Configured" else "Exempt",
                onAction = {
                    runCatching {
                        context.startActivity(SystemHardeningChecker.createBatteryOptimizationIntent(context))
                    }
                    onRefreshRequested()
                },
            )

            // Item 2: Private DNS Leak Prevention
            val isPrivateDnsSafe = !posture.isPrivateDnsActive
            HardeningItemRow(
                icon = Icons.Default.PrivacyTip,
                title = "Private DNS (DoT) Status",
                description =
                    if (isPrivateDnsSafe) {
                        "Disabled/Filtered — 100% of DNS routes through Un-Blocker"
                    } else {
                        "ACTIVE — encrypted queries bypass VPN (${posture.privateDnsServerName ?: "DoT"})"
                    },
                isSuccess = isPrivateDnsSafe,
                actionLabel = if (isPrivateDnsSafe) "Protected" else "Turn Off",
                onAction = {
                    runCatching {
                        context.startActivity(SystemHardeningChecker.createNetworkSettingsIntent())
                    }
                    onRefreshRequested()
                },
            )

            // Item 3: Always-On VPN & Lockdown
            HardeningItemRow(
                icon = Icons.Default.Lock,
                title = "Always-On VPN & Lockdown",
                description = "Guarantees zero traffic leaks during reboot or network switches",
                isSuccess = null,
                actionLabel = "Open VPN Settings",
                onAction = {
                    runCatching {
                        context.startActivity(SystemHardeningChecker.createVpnSettingsIntent())
                    }
                },
            )

            // Item 4: Android Advertising ID
            HardeningItemRow(
                icon = Icons.Default.Warning,
                title = "Advertising ID & Telemetry",
                description = "Delete advertising ID to stop cross-app behavioral tracking",
                isSuccess = null,
                actionLabel = "Ad Settings",
                onAction = {
                    runCatching {
                        context.startActivity(SystemHardeningChecker.createAdPrivacyIntent())
                    }
                },
            )
        }
    }
}

/**
 * Single actionable item row inside the system hardening checklist card.
 *
 * @param icon Leading descriptive icon.
 * @param title Short title of the security setting.
 * @param description Detailed guidance explaining current state and expected configuration.
 * @param isSuccess Tri-state status (true = green check, false = red warning, null = neutral teal).
 * @param actionLabel Button text for the system settings shortcut.
 * @param onAction Action invoked when the settings button is pressed.
 */
@Composable
private fun HardeningItemRow(
    icon: ImageVector,
    title: String,
    description: String,
    isSuccess: Boolean?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val statusColor =
        when (isSuccess) {
            true -> AllowedGreen
            false -> AdBlockedRed
            null -> TealPrimary
        }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Slate800 else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isDark) Slate700 else Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector =
                            when (isSuccess) {
                                true -> Icons.Default.CheckCircle
                                false -> Icons.Default.Warning
                                null -> icon
                            },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = statusColor),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp,
                        ),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}
