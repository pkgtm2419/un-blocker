package com.unblocker.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.unblocker.app.services.ServiceStatus

/**
 * Real-time dynamic state-driven color theming for Un-Blocker.
 * Morphing color palette that responds dynamically to VPN protection states.
 */
@Immutable
data class VpnStateColors(
    val coreAccent: Color,
    val coreGradientStart: Color,
    val coreGradientEnd: Color,
    val glowColor: Color,
    val surfaceHighlight: Color,
    val surfaceShadow: Color,
    val badgeBackground: Color,
    val badgeTextColor: Color,
    val waveAccent: Color,
    val statusText: Color
)

val LocalVpnStateColors = compositionLocalOf {
    VpnStateColors(
        coreAccent = AllowedGreen,
        coreGradientStart = Color(0xFF064E3B),
        coreGradientEnd = Slate900,
        glowColor = AllowedGreen.copy(alpha = 0.35f),
        surfaceHighlight = Color.White.copy(alpha = 0.12f),
        surfaceShadow = Color.Black.copy(alpha = 0.5f),
        badgeBackground = AllowedGreenBg,
        badgeTextColor = AllowedGreen,
        waveAccent = AllowedGreen.copy(alpha = 0.08f),
        statusText = AllowedGreen
    )
}

@Composable
fun rememberVpnStateColors(
    serviceStatus: ServiceStatus,
    isDarkTheme: Boolean = true
): VpnStateColors {
    val duration = 400

    val (targetCore, targetGradStart, targetGradEnd, targetGlow, targetBadgeBg, targetBadgeText, targetWave) = when (serviceStatus) {
        ServiceStatus.RUNNING -> StatePalette(
            core = AllowedGreen,
            gradStart = Color(0xFF047857),
            gradEnd = Color(0xFF064E3B),
            glow = AllowedGreen.copy(alpha = 0.40f),
            badgeBg = AllowedGreenBg,
            badgeText = AllowedGreen,
            wave = AllowedGreen.copy(alpha = 0.10f)
        )
        ServiceStatus.STARTING -> StatePalette(
            core = Color(0xFFF59E0B),
            gradStart = Color(0xFFB45309),
            gradEnd = Color(0xFF78350F),
            glow = Color(0xFFF59E0B).copy(alpha = 0.35f),
            badgeBg = Color(0xFF451A03),
            badgeText = Color(0xFFFBBF24),
            wave = Color(0xFFF59E0B).copy(alpha = 0.08f)
        )
        ServiceStatus.STOPPING -> StatePalette(
            core = Color(0xFFF97316),
            gradStart = Color(0xFFC2410C),
            gradEnd = Color(0xFF7C2D12),
            glow = Color(0xFFF97316).copy(alpha = 0.35f),
            badgeBg = Color(0xFF431407),
            badgeText = Color(0xFFFB923C),
            wave = Color(0xFFF97316).copy(alpha = 0.08f)
        )
        ServiceStatus.ERROR -> StatePalette(
            core = Color(0xFFEF4444),
            gradStart = Color(0xFFB91C1C),
            gradEnd = Color(0xFF7F1D1D),
            glow = Color(0xFFEF4444).copy(alpha = 0.45f),
            badgeBg = Color(0xFF450A0A),
            badgeText = Color(0xFFF87171),
            wave = Color(0xFFEF4444).copy(alpha = 0.12f)
        )
        ServiceStatus.STOPPED -> StatePalette(
            core = Slate400,
            gradStart = Slate800,
            gradEnd = Slate900,
            glow = Color.Transparent,
            badgeBg = Slate800,
            badgeText = Slate400,
            wave = Slate700.copy(alpha = 0.05f)
        )
    }

    val animCore by animateColorAsState(targetCore, tween(duration), label = "animCore")
    val animGradStart by animateColorAsState(targetGradStart, tween(duration), label = "animGradStart")
    val animGradEnd by animateColorAsState(targetGradEnd, tween(duration), label = "animGradEnd")
    val animGlow by animateColorAsState(targetGlow, tween(duration), label = "animGlow")
    val animBadgeBg by animateColorAsState(targetBadgeBg, tween(duration), label = "animBadgeBg")
    val animBadgeText by animateColorAsState(targetBadgeText, tween(duration), label = "animBadgeText")
    val animWave by animateColorAsState(targetWave, tween(duration), label = "animWave")

    return VpnStateColors(
        coreAccent = animCore,
        coreGradientStart = animGradStart,
        coreGradientEnd = animGradEnd,
        glowColor = animGlow,
        surfaceHighlight = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f),
        surfaceShadow = if (isDarkTheme) Color.Black.copy(alpha = 0.65f) else Color(0xFF0F172A).copy(alpha = 0.10f),
        badgeBackground = animBadgeBg,
        badgeTextColor = animBadgeText,
        waveAccent = animWave,
        statusText = animBadgeText
    )
}

private data class StatePalette(
    val core: Color,
    val gradStart: Color,
    val gradEnd: Color,
    val glow: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val wave: Color
)
