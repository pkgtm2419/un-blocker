package com.unblocker.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Reusable motion primitives for tactile physics-based micro-interactions,
 * bouncy button presses, breathing atmospheric pulses, and smooth transitions.
 */

/**
 * Modifier that applies an authentic spring bounce to any composable on press.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.LongPress,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedbackType?.let { haptic.performHapticFeedback(it) }
            scale.animateTo(
                targetValue = scaleDown,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    this
        .scale(scale.value)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Modifier creating an organic breathing pulse.
 * If disabled or reduced motion is preferred, immediately returns 1.0f without running transitions.
 */
@Composable
fun rememberBreathingPulse(
    enabled: Boolean = true,
    minScale: Float = 0.96f,
    maxScale: Float = 1.06f,
    durationMillis: Int = 2200
): Float {
    if (!enabled) return 1.0f

    val context = androidx.compose.ui.platform.LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    if (isReducedMotion) {
        return 1.0f
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    return scale
}

/**
 * Shimmer effect brush primitive for active or loading states.
 * Respects reduced motion settings and enabled flag to conserve device resources.
 */
@Composable
fun rememberShimmerBrush(
    enabled: Boolean = true,
    baseColor: Color = Color.White.copy(alpha = 0.1f),
    highlightColor: Color = Color.White.copy(alpha = 0.35f),
    durationMillis: Int = 1600
): Brush {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
    if (!enabled || isReducedMotion) {
        return Brush.linearGradient(
            colors = listOf(baseColor, baseColor)
        )
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )
}

/**
 * Expressive container with slide and fade transitions.
 */
@Composable
fun FluidVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(tween(250)) + slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy)) { it / 3 },
    exit: ExitTransition = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 4 },
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}
