package com.unblocker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unblocker.app.ui.theme.LocalVpnStateColors
import com.unblocker.app.ui.theme.Slate800
import com.unblocker.app.ui.theme.Slate900

/**
 * Skeuomorphic tactile surface card with realistic depth cues,
 * dual-specular rim lighting, directional shadows, and tactile affordances.
 */
@Composable
fun SkeuomorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 6.dp,
    highlightColor: Color? = null,
    shadowColor: Color? = null,
    isInset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val stateColors = LocalVpnStateColors.current
    val actualHighlight = highlightColor ?: (if (isDark) stateColors.surfaceHighlight else Color.White.copy(alpha = 0.9f))
    val actualShadow = shadowColor ?: (if (isDark) stateColors.surfaceShadow else Color(0xFFCBD5E1).copy(alpha = 0.7f))

    val surfaceGradient = if (isDark) {
        if (isInset) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F172A),
                    Slate900
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Slate800.copy(alpha = 0.95f),
                    Slate900.copy(alpha = 0.98f)
                )
            )
        }
    } else {
        if (isInset) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFF1F5F9),
                    Color(0xFFE2E8F0)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White,
                    Color(0xFFF8FAFC)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isInset) 1.dp else elevation,
                shape = shape,
                ambientColor = actualShadow,
                spotColor = actualShadow
            )
            .clip(shape)
            .background(surfaceGradient)
            // Tactile dual-specular border (top-left specular rim, bottom shadow bevel)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isInset) {
                        listOf(actualShadow, actualHighlight.copy(alpha = 0.2f))
                    } else {
                        listOf(actualHighlight, actualShadow)
                    }
                ),
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (isDark) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onSurface
            ) {
                content()
            }
        }
    }
}

/**
 * Skeuomorphic tactile button with physical depression affordance.
 */
@Composable
fun SkeuomorphicTactileSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    isPressed: Boolean = false,
    accentColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val stateColors = LocalVpnStateColors.current
    val elevation = if (isPressed) 1.dp else 4.dp
    val shadowColor = if (isDark) stateColors.surfaceShadow else Color(0xFF64748B).copy(alpha = 0.2f)
    val highlightColor = if (isDark) stateColors.surfaceHighlight else Color.White.copy(alpha = 0.9f)

    val surfaceGradient = if (isDark) {
        if (isPressed) {
            listOf(Slate900, Slate800)
        } else {
            listOf(Slate800, Slate900)
        }
    } else {
        if (isPressed) {
            listOf(Color(0xFFE2E8F0), Color(0xFFF1F5F9))
        } else {
            listOf(Color.White, Color(0xFFF1F5F9))
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(colors = surfaceGradient)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (accentColor != Color.Transparent) accentColor.copy(alpha = 0.6f) else highlightColor,
                        shadowColor
                    )
                ),
                shape = shape
            )
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (isDark) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.onSurface
        ) {
            content()
        }
    }
}
