package com.unblocker.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.AllowedGreen
import com.unblocker.app.ui.theme.CyanGlow
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.TealPrimary

@Composable
fun UnblockerScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onRequestVpnPermission: () -> Unit
) {
    val dimensions = LocalWindowDimensions.current
    val serviceStatus by quickStartManager.status.collectAsState()
    val isRunning = serviceStatus == ServiceStatus.RUNNING

    val adBlockingEnabled by preferences.adBlockingEnabled.collectAsState()
    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 24.dp else 48.dp,
                    vertical = 36.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Centered Hero Protection Shield
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (dimensions.widthClass == WindowWidthClass.COMPACT) 130.dp else 150.dp)
                    .scale(if (isRunning) pulseScale else 1.0f)
            ) {
                // Background radial glow
                Box(
                    modifier = Modifier
                        .size(if (dimensions.widthClass == WindowWidthClass.COMPACT) 130.dp else 150.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    if (isRunning) AllowedGreen.copy(alpha = 0.35f) else TealPrimary.copy(alpha = 0.20f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Shield Icon Box
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (dimensions.widthClass == WindowWidthClass.COMPACT) 84.dp else 96.dp)
                        .background(
                            color = Slate800,
                            shape = RoundedCornerShape(26.dp)
                        )
                        .border(
                            width = 2.dp,
                            color = if (isRunning) AllowedGreen else TealAccent,
                            shape = RoundedCornerShape(26.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = "unblocker Shield",
                        tint = if (isRunning) AllowedGreen else CyanGlow,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Application Branding
            Text(
                text = "unblocker",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )

            Text(
                text = "unwanted network blocker",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = TealAccent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Live Status Indicator Pill
            Surface(
                color = if (isRunning) Color(0xFF064E3B) else Slate800,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isRunning) AllowedGreen else Slate700
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isRunning) AllowedGreen else Slate400, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "SHIELD ACTIVE • ANALYZING LOCALLY" else "SHIELD IDLE • TAP TO ENABLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color.White else Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // TWO-OPTION CONTROL CARD
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {

                    // OPTION 1: Analyze Network & Block Unwanted Ads (Master Switch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Analyze Network & Block Unwanted Ads",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "On-device self-learning network analysis to block unwanted ads & trackers",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate400
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Switch(
                            checked = isRunning && adBlockingEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    preferences.setAdBlockingEnabled(true)
                                    quickStartManager.startBlockingServices(onPermissionRequired = onRequestVpnPermission)
                                } else {
                                    preferences.setAdBlockingEnabled(false)
                                    quickStartManager.stopBlockingServices()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TealAccent,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate900
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Slate700)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // OPTION 2: Block 18+ Content Also
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block 18+ Content Also",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Filter adult websites and explicit streaming domains alongside ads",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate400
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Switch(
                            checked = adultBlockingEnabled,
                            onCheckedChange = { enable ->
                                preferences.setAdultBlockingEnabled(enable)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AdultBlockedPurple,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate900
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Privacy & Zero-Cloud Guarantee Note
            Surface(
                color = Slate900.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔒 100% Local & Zero-Log Guarantee",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All network analysis runs on your device using system resources. Zero cloud data is sent, and zero logs or browsing history are ever created.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
