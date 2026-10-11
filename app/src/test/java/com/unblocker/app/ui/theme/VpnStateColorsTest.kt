package com.unblocker.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.unblocker.app.services.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnStateColorsTest {

    @Test
    fun testVpnStateColorsDataClassIntegrity() {
        val colors = VpnStateColors(
            coreAccent = AllowedGreen,
            coreGradientStart = Color(0xFF064E3B),
            coreGradientEnd = Slate900,
            glowColor = AllowedGreen.copy(alpha = 0.35f),
            surfaceHighlight = Color.White.copy(alpha = 0.12f),
            surfaceShadow = Color.Black.copy(alpha = 0.5f),
            badgeBackground = AllowedGreenBg,
            badgeTextColor = AllowedGreen,
            waveAccent = AllowedGreen.copy(alpha = 0.08f),
            statusText = AllowedGreen
        )

        assertNotNull(colors)
        assertEquals(AllowedGreen, colors.coreAccent)
        assertEquals(AllowedGreenBg, colors.badgeBackground)
        assertEquals(AllowedGreen, colors.badgeTextColor)
    }

    @Test
    fun testStatusTransitionsProduceDistinctSemantics() {
        val runningStatus = ServiceStatus.RUNNING
        val stoppedStatus = ServiceStatus.STOPPED
        val errorStatus = ServiceStatus.ERROR

        assertNotEquals(runningStatus, stoppedStatus)
        assertNotEquals(runningStatus, errorStatus)
    }
}
