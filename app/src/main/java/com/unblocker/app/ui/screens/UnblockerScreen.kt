package com.unblocker.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.services.QuickStartManager
import com.unblocker.app.services.ServiceStatus
import com.unblocker.app.ui.adaptive.LocalWindowDimensions
import com.unblocker.app.ui.adaptive.WindowWidthClass
import com.unblocker.app.ui.components.HaikeiAtmosphericBackground
import com.unblocker.app.ui.components.SkeuomorphicCard
import com.unblocker.app.ui.motion.rememberBreathingPulse
import com.unblocker.app.ui.theme.AdultBlockedPurple
import com.unblocker.app.ui.theme.LocalVpnStateColors
import com.unblocker.app.ui.theme.Slate400
import com.unblocker.app.ui.theme.Slate700
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.TealAccent
import com.unblocker.app.ui.theme.rememberVpnStateColors

/**
 * Streamlined Unblocker Landing Page (Home Tab).
 *
 * Minimalist design featuring strictly:
 *  1. Header branding with dynamic status pill.
 *  2. Central Hero: Tactile Interactive Shield Logo Start/Stop Button.
 *     The authentic shield logo remains identical while the inner power icon
 *     dynamically changes state, illumination, and breathing aura based on VPN status.
 *  3. Directly below the logo: Block 18+ Adult Content Toggle Card.
 *
 * All secondary diagnostic workflows, tier selectors, logs shortcuts, and system
 * toggles have moved to the dedicated Settings screen.
 */
@Composable
fun UnblockerScreen(
    preferences: FilteringPreferences,
    quickStartManager: QuickStartManager,
    onRequestVpnPermission: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenSettings: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) {
    val dimensions = LocalWindowDimensions.current
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    val serviceStatus by quickStartManager.status.collectAsState()
    val isRunning = serviceStatus == ServiceStatus.RUNNING
    val isStarting = serviceStatus == ServiceStatus.STARTING
    val isStopping = serviceStatus == ServiceStatus.STOPPING

    val adultBlockingEnabled by preferences.adultBlockingEnabled.collectAsState()

    // Dynamic state-reactive colors
    val stateColors = rememberVpnStateColors(
        serviceStatus = serviceStatus,
        isDarkTheme = isDark
    )

    // Breathing pulse motion primitive: active only when protection is running
    val pulseGlowScale = rememberBreathingPulse(
        enabled = isRunning,
        minScale = 0.96f,
        maxScale = 1.08f,
        durationMillis = 1800
    )

    // Press interaction state and physics-based spring bounce
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )

    val buttonSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 180.dp else 204.dp
    val outerGlowSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 240.dp else 272.dp
    val innerIconSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 50.dp else 56.dp

    CompositionLocalProvider(LocalVpnStateColors provides stateColors) {
        HaikeiAtmosphericBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = if (dimensions.widthClass == WindowWidthClass.COMPACT) 24.dp else 48.dp,
                        vertical = 24.dp
                    )
                    .padding(bottom = contentPadding.calculateBottomPadding() + 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // 1. TOP BRANDING & STATUS PILL
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Slate800.copy(alpha = 0.85f) else Color(0xFFF1F5F9),
                    border = BorderStroke(
                        1.dp,
                        if (isRunning) stateColors.coreAccent.copy(alpha = 0.5f)
                        else if (isDark) Slate700 else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = when {
                                        isRunning -> TealAccent
                                        isStarting || isStopping -> Color(0xFFFBBF24)
                                        else -> Slate400
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isRunning -> "PROTECTED"
                                isStarting -> "STARTING..."
                                isStopping -> "STOPPING..."
                                else -> "DISCONNECTED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = when {
                                isRunning -> TealAccent
                                isStarting || isStopping -> Color(0xFFFBBF24)
                                else -> Slate400
                            }
                        )
                    }
                }

                Text(
                    text = "unblocker",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "on-device network shield",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(36.dp))

                // 2. CENTRAL HERO: INTERACTIVE SHIELD LOGO START/STOP BUTTON
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(outerGlowSize)
                ) {
                    // Ambient atmospheric breathing aura
                    Box(
                        modifier = Modifier
                            .size(outerGlowSize)
                            .graphicsLayer {
                                val s = if (isRunning) pulseGlowScale else 1.0f
                                scaleX = s
                                scaleY = s
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        stateColors.glowColor,
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Tactile interactive shield card container
                    Surface(
                        shape = RoundedCornerShape(36.dp),
                        color = stateColors.coreGradientStart,
                        tonalElevation = if (isRunning) 12.dp else 4.dp,
                        shadowElevation = if (isRunning) 20.dp else 8.dp,
                        border = BorderStroke(
                            width = if (isRunning) 2.5.dp else 1.5.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    stateColors.coreAccent,
                                    stateColors.coreAccent.copy(alpha = 0.3f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .size(buttonSize)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clip(RoundedCornerShape(36.dp))
                            .clickable(
                                enabled = !isStarting && !isStopping,
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true, radius = buttonSize / 2),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isRunning) {
                                        preferences.setProtectionEnabled(false)
                                        quickStartManager.stopBlockingServices()
                                    } else {
                                        onRequestVpnPermission()
                                    }
                                }
                            )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Full authentic application logo shield emblem (remains identical)
                            Image(
                                painter = painterResource(id = com.unblocker.app.R.drawable.app_logo),
                                contentDescription = if (isRunning) "Disconnect Shield" else "Connect Shield",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(28.dp))
                            )

                            // Status-reactive inner power icon color
                            val innerIconColor = when {
                                isRunning -> TealAccent
                                isStarting || isStopping -> Color(0xFFFBBF24)
                                else -> if (isDark) Slate400.copy(alpha = 0.7f) else Color(0xFF64748B)
                            }

                            // Dynamic radiant glow behind the inner power icon when protected
                            if (isRunning) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    TealAccent.copy(alpha = 0.38f),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                            }

                            // Dynamic inner power icon that changes based on status
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = innerIconColor,
                                modifier = Modifier
                                    .size(innerIconSize)
                                    .graphicsLayer {
                                        alpha = if (isRunning) 0.95f else 0.55f
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action text caption
                Text(
                    text = when {
                        isRunning -> "SHIELD ACTIVE"
                        isStarting -> "STARTING..."
                        isStopping -> "STOPPING..."
                        else -> "TAP TO CONNECT"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = when {
                        isRunning -> TealAccent
                        isStarting || isStopping -> Color(0xFFFBBF24)
                        else -> if (isDark) Slate400 else Color(0xFF64748B)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = when {
                        isRunning -> "Tap shield to disconnect"
                        isStarting -> "Initializing DNS filters"
                        isStopping -> "Closing network tunnel"
                        else -> "Protection is paused"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(36.dp))

                // 3. BELOW LOGO: ONLY THE 18+ CONTENT TOGGLE
                SkeuomorphicCard(
                    modifier = Modifier.widthIn(max = 420.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Block 18+ Content",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AdultBlockedPurple.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, AdultBlockedPurple.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = "Adult",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AdultBlockedPurple,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Filter adult websites & explicit domains",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Switch(
                            checked = adultBlockingEnabled,
                            onCheckedChange = { enable ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                preferences.setAdultBlockingEnabled(enable)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AdultBlockedPurple,
                                uncheckedThumbColor = if (isDark) Slate400 else Color(0xFF64748B),
                                uncheckedTrackColor = if (isDark) Slate800 else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

            }
        }
    }
}
