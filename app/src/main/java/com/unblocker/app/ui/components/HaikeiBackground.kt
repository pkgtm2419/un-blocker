package com.unblocker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.unblocker.app.ui.theme.LocalVpnStateColors

/**
 * Haikei-style mathematically generated background waves and organic mesh curves.
 * Creates an atmospheric, layered visual canvas that reacts dynamically to VPN state.
 */
@Composable
fun HaikeiAtmosphericBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val stateColors = LocalVpnStateColors.current
    val baseBackground = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Draw base canvas background
            drawRect(color = baseBackground)

            val width = size.width
            val height = size.height

            // 2. Haikei Upper Wave Layer 1 (Deep Organic Flow)
            val wavePath1 = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, height * 0.28f)
                cubicTo(
                    width * 0.25f, height * 0.38f,
                    width * 0.55f, height * 0.18f,
                    width, height * 0.32f
                )
                lineTo(width, 0f)
                close()
            }
            drawPath(
                path = wavePath1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        stateColors.waveAccent.copy(alpha = 0.6f),
                        stateColors.waveAccent.copy(alpha = 0.05f)
                    ),
                    startY = 0f,
                    endY = height * 0.38f
                )
            )

            // 3. Haikei Upper Wave Layer 2 (Opposing Gentle Contour)
            val wavePath2 = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, height * 0.16f)
                cubicTo(
                    width * 0.35f, height * 0.08f,
                    width * 0.70f, height * 0.24f,
                    width, height * 0.12f
                )
                lineTo(width, 0f)
                close()
            }
            drawPath(
                path = wavePath2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        stateColors.waveAccent.copy(alpha = 0.4f),
                        stateColors.waveAccent.copy(alpha = 0.1f)
                    )
                )
            )

            // 4. Central Energy Halo (Radial Bloom around the Shield Button)
            val centerHaloY = height * 0.38f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateColors.glowColor.copy(alpha = 0.22f),
                        stateColors.waveAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, centerHaloY),
                    radius = width * 0.55f
                ),
                radius = width * 0.55f,
                center = Offset(width * 0.5f, centerHaloY)
            )

            // 5. Haikei Bottom Foundation Wave (Soft anchor above bottom nav)
            val bottomWave = Path().apply {
                moveTo(0f, height)
                lineTo(0f, height * 0.88f)
                cubicTo(
                    width * 0.30f, height * 0.82f,
                    width * 0.65f, height * 0.94f,
                    width, height * 0.86f
                )
                lineTo(width, height)
                close()
            }
            drawPath(
                path = bottomWave,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        stateColors.waveAccent.copy(alpha = 0.35f)
                    ),
                    startY = height * 0.82f,
                    endY = height
                )
            )
        }

        // Screen content layer rendered cleanly on top
        content()
    }
}
