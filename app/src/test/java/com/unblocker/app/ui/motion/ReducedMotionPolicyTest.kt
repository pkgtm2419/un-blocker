package com.unblocker.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionPolicyTest {

    @Test
    fun reducedMotionDisabledByDefaultWhenScaleIsOne() {
        val policy = ReducedMotionPolicy(disableDecorativeLoops = false, durationScale = 1.0f)
        assertFalse(policy.disableDecorativeLoops)
        assertEquals(1.0f, policy.durationScale, 0.001f)
    }

    @Test
    fun quietUiOptInForcesReducedMotion() {
        val policy = ReducedMotionPolicy(disableDecorativeLoops = true, durationScale = 0.0f)
        assertTrue(policy.disableDecorativeLoops)
        assertEquals(0.0f, policy.durationScale, 0.001f)
    }
}
