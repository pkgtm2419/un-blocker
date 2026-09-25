package com.unblocker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class WindowDimensions(
    val widthClass: WindowWidthClass,
    val maxWidth: Dp,
    val maxHeight: Dp,
    val isLandscape: Boolean
)

val LocalWindowDimensions = compositionLocalOf {
    WindowDimensions(
        widthClass = WindowWidthClass.COMPACT,
        maxWidth = 360.dp,
        maxHeight = 640.dp,
        isLandscape = false
    )
}

@Composable
fun ProvideAdaptiveWindow(
    content: @Composable () -> Unit
) {
    BoxWithConstraints {
        val width = maxWidth
        val height = maxHeight
        val isLandscape = width > height

        val widthClass = when {
            width < 600.dp -> WindowWidthClass.COMPACT
            width < 840.dp -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.EXPANDED
        }

        val dimensions = WindowDimensions(
            widthClass = widthClass,
            maxWidth = width,
            maxHeight = height,
            isLandscape = isLandscape
        )

        CompositionLocalProvider(LocalWindowDimensions provides dimensions) {
            content()
        }
    }
}
