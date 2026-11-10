package com.unblocker.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

    val buttonSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 320.dp else 360.dp
    val outerGlowSize = if (dimensions.widthClass == WindowWidthClass.COMPACT) 380.dp else 420.dp

    // Status color transition tracking with outward radial wave animation
    var currentRenderStatus by remember { mutableStateOf(serviceStatus) }
    var previousRenderStatus by remember { mutableStateOf(serviceStatus) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(serviceStatus) {
        if (serviceStatus != currentRenderStatus) {
            previousRenderStatus = currentRenderStatus
            currentRenderStatus = serviceStatus
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
    }

    val currentPainter = painterResource(id = getLogoResForStatus(currentRenderStatus))
    val previousPainter = painterResource(id = getLogoResForStatus(previousRenderStatus))

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

                Spacer(modifier = Modifier.height(16.dp))

                // CENTRAL HERO: SHIELD LOGO START/STOP BUTTON
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

                    // Interactive shield logo container without background border/card
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(buttonSize)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = false, radius = buttonSize / 2),
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
                        // Dynamic state-reactive logo with outward radial color animation
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val maxWaveRadius = size.minDimension * 0.44f
                            val progress = animProgress.value

                            if (progress >= 1f) {
                                with(currentPainter) {
                                    draw(size = size)
                                }
                            } else {
                                // 1. Base previous status image
                                with(previousPainter) {
                                    draw(size = size)
                                }

                                val currentRadius = progress * maxWaveRadius
                                if (currentRadius > 0f) {
                                    // 2. Target status image clipped to outward expanding circular wave from center
                                    val clipPath = Path().apply {
                                        addOval(
                                            Rect(
                                                center = center,
                                                radius = currentRadius
                                            )
                                        )
                                    }
                                    clipPath(clipPath) {
                                        with(currentPainter) {
                                            draw(size = size)
                                        }
                                    }

                                    // 3. Glowing energy wavefront expanding from center -> circle -> lines
                                    val waveColor = when (currentRenderStatus) {
                                        ServiceStatus.RUNNING -> TealAccent
                                        ServiceStatus.STARTING, ServiceStatus.STOPPING -> Color(0xFFFFB703)
                                        else -> Slate400
                                    }

                                    val waveAlpha = (1f - (progress * 0.35f)).coerceIn(0f, 1f)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                waveColor.copy(alpha = 0.65f * waveAlpha),
                                                waveColor.copy(alpha = 0.25f * waveAlpha),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = (currentRadius + 14.dp.toPx()).coerceAtLeast(1f)
                                        ),
                                        radius = currentRadius + 10.dp.toPx(),
                                        center = center
                                    )

                                    // Crisp bright leading edge ring
                                    drawCircle(
                                        color = waveColor.copy(alpha = 0.85f * waveAlpha),
                                        radius = currentRadius,
                                        center = center,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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

private fun getLogoResForStatus(status: ServiceStatus): Int = when (status) {
    ServiceStatus.RUNNING -> com.unblocker.app.R.drawable.app_logo
    ServiceStatus.STARTING, ServiceStatus.STOPPING -> com.unblocker.app.R.drawable.app_logo_amber
    else -> com.unblocker.app.R.drawable.app_logo_off
}

