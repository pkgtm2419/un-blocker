package com.unblocker.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.unblocker.app.services.ServiceStatus

/**
 * Visual palette for a rendered service state; colors do not independently verify VPN health.
 *
 * @property coreAccent Main shield and status accent.
 * @property coreGradientStart Start color for the shield gradient.
 * @property coreGradientEnd End color for the shield gradient.
 * @property glowColor Decorative halo color.
 * @property surfaceHighlight Highlight color for raised surface edges.
 * @property surfaceShadow Shadow color for surface depth.
 * @property badgeBackground Background color for status badges.
 * @property badgeTextColor Foreground color for status badges.
 * @property waveAccent Decorative background wave color.
 * @property statusText Foreground color for status text.
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
    val statusText: Color,
)

/**
 * Service-state palette available to descendants, with a green default palette.
 */
val LocalVpnStateColors =
    compositionLocalOf {
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
            statusText = AllowedGreen,
        )
    }

/**
 * Animates the state-specific palette and selects theme-appropriate surface contrast.
 *
 * @param serviceStatus Service state driving the target colors.
 * @param isDarkTheme Whether contrast colors target dark surfaces.
 * @return Current interpolated palette for rendering.
 */
@Composable
fun rememberVpnStateColors(
    serviceStatus: ServiceStatus,
    isDarkTheme: Boolean = true,
): VpnStateColors {
    val (targetCore, targetGradStart, targetGradEnd, targetGlow, targetBadgeBg, targetBadgeText, targetWave) =
        when (serviceStatus) {
            ServiceStatus.RUNNING -> {
                StatePalette(
                    core = AllowedGreen,
                    gradStart = Color(0xFF047857),
                    gradEnd = Color(0xFF064E3B),
                    glow = AllowedGreen.copy(alpha = 0.40f),
                    badgeBg = if (isDarkTheme) AllowedGreenBg else Color(0xFFD1FAE5),
                    badgeText = if (isDarkTheme) AllowedGreen else Color(0xFF065F46),
                    wave = AllowedGreen.copy(alpha = 0.10f),
                )
            }

            ServiceStatus.STARTING -> {
                StatePalette(
                    core = if (isDarkTheme) Color(0xFFF59E0B) else Color(0xFFD97706),
                    gradStart = Color(0xFFB45309),
                    gradEnd = Color(0xFF78350F),
                    glow = Color(0xFFF59E0B).copy(alpha = 0.35f),
                    badgeBg = if (isDarkTheme) Color(0xFF451A03) else Color(0xFFFEF3C7),
                    badgeText = if (isDarkTheme) Color(0xFFFBBF24) else Color(0xFF92400E),
                    wave = Color(0xFFF59E0B).copy(alpha = 0.08f),
                )
            }

            ServiceStatus.STOPPING -> {
                StatePalette(
                    core = if (isDarkTheme) Color(0xFFF97316) else Color(0xFFEA580C),
                    gradStart = Color(0xFFC2410C),
                    gradEnd = Color(0xFF7C2D12),
                    glow = Color(0xFFF97316).copy(alpha = 0.35f),
                    badgeBg = if (isDarkTheme) Color(0xFF431407) else Color(0xFFFFEDD5),
                    badgeText = if (isDarkTheme) Color(0xFFFB923C) else Color(0xFF9A3412),
                    wave = Color(0xFFF97316).copy(alpha = 0.08f),
                )
            }

            ServiceStatus.ERROR -> {
                StatePalette(
                    core = if (isDarkTheme) Color(0xFFEF4444) else Color(0xFFDC2626),
                    gradStart = Color(0xFFB91C1C),
                    gradEnd = Color(0xFF7F1D1D),
                    glow = Color(0xFFEF4444).copy(alpha = 0.45f),
                    badgeBg = if (isDarkTheme) Color(0xFF450A0A) else Color(0xFFFEE2E2),
                    badgeText = if (isDarkTheme) Color(0xFFF87171) else Color(0xFF991B1B),
                    wave = Color(0xFFEF4444).copy(alpha = 0.12f),
                )
            }

            ServiceStatus.STOPPED -> {
                StatePalette(
                    core = if (isDarkTheme) Slate400 else Slate600,
                    gradStart = if (isDarkTheme) Slate800 else Color(0xFFE2E8F0),
                    gradEnd = if (isDarkTheme) Slate900 else Color(0xFFCBD5E1),
                    glow = Color.Transparent,
                    badgeBg = if (isDarkTheme) Slate800 else Color(0xFFE2E8F0),
                    badgeText = if (isDarkTheme) Slate400 else Color(0xFF334155),
                    wave = if (isDarkTheme) Slate700.copy(alpha = 0.05f) else Slate400.copy(alpha = 0.08f),
                )
            }
        }

    val animCore by animateColorAsState(targetCore, tween(STATE_COLOR_TRANSITION_MS), label = "animCore")
    val animGradStart by animateColorAsState(targetGradStart, tween(STATE_COLOR_TRANSITION_MS), label = "animGradStart")
    val animGradEnd by animateColorAsState(targetGradEnd, tween(STATE_COLOR_TRANSITION_MS), label = "animGradEnd")
    val animGlow by animateColorAsState(targetGlow, tween(STATE_COLOR_TRANSITION_MS), label = "animGlow")
    val animBadgeBg by animateColorAsState(targetBadgeBg, tween(STATE_COLOR_TRANSITION_MS), label = "animBadgeBg")
    val animBadgeText by animateColorAsState(targetBadgeText, tween(STATE_COLOR_TRANSITION_MS), label = "animBadgeText")
    val animWave by animateColorAsState(targetWave, tween(STATE_COLOR_TRANSITION_MS), label = "animWave")

    return VpnStateColors(
        coreAccent = animCore,
        coreGradientStart = animGradStart,
        coreGradientEnd = animGradEnd,
        glowColor = animGlow,
        surfaceHighlight = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f),
        surfaceShadow = if (isDarkTheme) Color.Black.copy(alpha = 0.65f) else Color(0xFF64748B).copy(alpha = 0.18f),
        badgeBackground = animBadgeBg,
        badgeTextColor = animBadgeText,
        waveAccent = animWave,
        statusText = animBadgeText,
    )
}

private const val STATE_COLOR_TRANSITION_MS = 400

private data class StatePalette(
    val core: Color,
    val gradStart: Color,
    val gradEnd: Color,
    val glow: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val wave: Color,
)
