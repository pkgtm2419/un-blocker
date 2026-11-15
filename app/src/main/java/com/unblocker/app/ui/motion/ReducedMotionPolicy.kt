package com.unblocker.app.ui.motion

import android.content.Context
import android.provider.Settings

/**
 * Snapshot of animation settings for UI decoration, independent of VPN packet processing.
 *
 * @property disableDecorativeLoops Whether looping decoration should be disabled.
 * @property durationScale System animation-duration multiplier, or zero when reduced motion is requested.
 */
data class ReducedMotionPolicy(
    val disableDecorativeLoops: Boolean,
    val durationScale: Float
) {
    /**
     * Factory for snapshots of the current system animation policy.
     */
    companion object {
        /**
         * Reads the global animator scale, falling back to one when the setting cannot be read.
         *
         * @param context Context providing access to global settings.
         * @param quietUiOptIn Whether a caller explicitly requests reduced decoration.
         * @return Reduced motion when opted in or when the system animator scale is zero.
         */
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
