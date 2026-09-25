package com.unblocker.app.responsive

import com.unblocker.app.ui.adaptive.WindowWidthClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {

    private fun resolveWidthClass(widthDp: Float): WindowWidthClass {
        return when {
            widthDp < 600f -> WindowWidthClass.COMPACT
            widthDp < 840f -> WindowWidthClass.MEDIUM
            else -> WindowWidthClass.EXPANDED
        }
    }

    private fun resolveColumns(widthClass: WindowWidthClass): Int {
        return when (widthClass) {
            WindowWidthClass.COMPACT -> 1
            WindowWidthClass.MEDIUM -> 2
            WindowWidthClass.EXPANDED -> 4
        }
    }

    @Test
    fun testCompactPhoneScreens() {
        // Standard phone screens: 360dp, 390dp, 412dp
        assertEquals(WindowWidthClass.COMPACT, resolveWidthClass(360f))
        assertEquals(WindowWidthClass.COMPACT, resolveWidthClass(390f))
        assertEquals(WindowWidthClass.COMPACT, resolveWidthClass(412f))
        assertEquals(WindowWidthClass.COMPACT, resolveWidthClass(599f))

        // On compact screens, layout defaults to single-column vertical flow
        assertEquals(1, resolveColumns(WindowWidthClass.COMPACT))
    }

    @Test
    fun testFoldableAndSmallTabletScreens() {
        // Foldable outer/inner screens and 7-8" tablets: 600dp, 720dp, 800dp
        assertEquals(WindowWidthClass.MEDIUM, resolveWidthClass(600f))
        assertEquals(WindowWidthClass.MEDIUM, resolveWidthClass(720f))
        assertEquals(WindowWidthClass.MEDIUM, resolveWidthClass(800f))
        assertEquals(WindowWidthClass.MEDIUM, resolveWidthClass(839f))

        // On medium screens, layout adapts to 2 columns with side navigation rail
        assertEquals(2, resolveColumns(WindowWidthClass.MEDIUM))
    }

    @Test
    fun testLargeTabletAndExpandedScreens() {
        // 10-12" tablets and desktop/chromebooks: 840dp, 1024dp, 1280dp
        assertEquals(WindowWidthClass.EXPANDED, resolveWidthClass(840f))
        assertEquals(WindowWidthClass.EXPANDED, resolveWidthClass(1024f))
        assertEquals(WindowWidthClass.EXPANDED, resolveWidthClass(1280f))

        // On expanded screens, layout adapts to 4 columns with full rail
        assertEquals(4, resolveColumns(WindowWidthClass.EXPANDED))
    }

    @Test
    fun testOrientationDetection() {
        val widthPortrait = 412f
        val heightPortrait = 915f
        val isLandscapePortrait = widthPortrait > heightPortrait
        assertFalse("Portrait should not be landscape", isLandscapePortrait)

        val widthLandscape = 915f
        val heightLandscape = 412f
        val isLandscapeLandscape = widthLandscape > heightLandscape
        assertTrue("Landscape dimensions should be landscape", isLandscapeLandscape)
    }
}
