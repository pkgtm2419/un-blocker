package com.unblocker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window width buckets used for app layout decisions, measured in density-independent pixels.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

/**
 * Constraints and orientation snapshot supplied to adaptive content.
 *
 * @property widthClass Width bucket derived from 600 dp and 840 dp breakpoints.
 * @property maxWidth Maximum available layout width in density-independent pixels.
 * @property maxHeight Maximum available layout height in density-independent pixels.
 * @property isLandscape Whether available width exceeds available height.
 */
data class WindowDimensions(
    val widthClass: WindowWidthClass,
    val maxWidth: Dp,
    val maxHeight: Dp,
    val isLandscape: Boolean
)

/**
 * Adaptive window snapshot for descendants; defaults to a compact 360 dp by 640 dp window.
 */
val LocalWindowDimensions = compositionLocalOf {
    WindowDimensions(
        widthClass = WindowWidthClass.COMPACT,
        maxWidth = 360.dp,
        maxHeight = 640.dp,
        isLandscape = false
    )
}

/**
 * Provides dimensions derived from the current layout constraints to its descendants.
 *
 * @param content Content that can read [LocalWindowDimensions].
 */
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
