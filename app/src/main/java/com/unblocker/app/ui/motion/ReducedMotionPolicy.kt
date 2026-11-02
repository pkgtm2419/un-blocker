package com.unblocker.app.ui.motion

import android.content.Context
import android.provider.Settings

/**
 * Encapsulates system accessibility animation settings and user Quiet UI preference.
 * Affects only Compose decoration and visual effects, never VPN packet processing.
 */
data class ReducedMotionPolicy(
    val disableDecorativeLoops: Boolean,
    val durationScale: Float
) {
    companion object {
        fun resolve(context: Context, quietUiOptIn: Boolean = false): ReducedMotionPolicy {
            val systemScale = try {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f
                )
            } catch (_: Exception) {
                1.0f
            }
            val isReduced = quietUiOptIn || systemScale == 0f
            return ReducedMotionPolicy(
                disableDecorativeLoops = isReduced,
                durationScale = if (isReduced) 0f else systemScale
            )
        }
    }
}
