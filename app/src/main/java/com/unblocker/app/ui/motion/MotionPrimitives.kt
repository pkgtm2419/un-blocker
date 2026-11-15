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
 * Adds a spring press scale, optional press haptic, and click handling to this modifier.
 *
 * @param scaleDown Target scale while pressed, relative to the original size.
 * @param hapticFeedbackType Haptic emitted on press, or null to omit feedback.
 * @param onClick Action invoked when the click completes.
 * @return Modifier with press scaling and click behavior.
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
 * Produces a looping decorative scale unless disabled or reduced motion was requested.
 *
 * @param enabled Whether to create the loop.
 * @param minScale Lower relative scale endpoint.
 * @param maxScale Upper relative scale endpoint.
 * @param durationMillis Duration in milliseconds for each direction of the pulse.
 * @return Animated scale, or one when the loop is disabled.
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
        ReducedMotionPolicy.resolve(context).disableDecorativeLoops
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
 * Creates a moving diagonal highlight, or a stationary base brush when motion is disabled.
 *
 * @param enabled Whether the shimmer may animate.
 * @param baseColor Background color of the shimmer.
 * @param highlightColor Moving highlight color.
 * @param durationMillis Duration of one shimmer sweep in milliseconds.
 * @return Brush using the current animation position or the base color.
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
        ReducedMotionPolicy.resolve(context).disableDecorativeLoops
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
 * Shows or hides content with the supplied Compose visibility transitions.
 *
 * @param visible Whether content should be visible.
 * @param modifier Modifier applied to the animated container.
 * @param enter Transition used when content appears.
 * @param exit Transition used when content disappears.
 * @param content Content rendered in the animated visibility scope.
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
