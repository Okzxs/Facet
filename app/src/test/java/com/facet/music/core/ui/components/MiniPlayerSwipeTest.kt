package com.facet.music.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerSwipeTest {
    @Test fun shortSlowSwipeReturnsToPlace() {
        assertEquals(0, swipeTrackDirection(offsetPx = 40f, widthPx = 300f, velocityX = 500f))
    }

    @Test fun leftDistanceSwitchesToNext() {
        assertEquals(1, swipeTrackDirection(offsetPx = -100f, widthPx = 300f, velocityX = 0f))
    }

    @Test fun rightDistanceSwitchesToPrevious() {
        assertEquals(-1, swipeTrackDirection(offsetPx = 100f, widthPx = 300f, velocityX = 0f))
    }

    @Test fun exactThirtyPercentIsEnough() {
        assertEquals(1, swipeTrackDirection(offsetPx = -90f, widthPx = 300f, velocityX = 0f))
        assertEquals(-1, swipeTrackDirection(offsetPx = 90f, widthPx = 300f, velocityX = 0f))
    }

    @Test fun fastLeftFlickSwitchesWithoutDistance() {
        assertEquals(1, swipeTrackDirection(offsetPx = -10f, widthPx = 300f, velocityX = -1001f))
    }

    @Test fun fastRightFlickSwitchesWithoutDistance() {
        assertEquals(-1, swipeTrackDirection(offsetPx = 10f, widthPx = 300f, velocityX = 1001f))
    }

    @Test fun velocityAtThresholdDoesNotSwitch() {
        assertEquals(0, swipeTrackDirection(offsetPx = 0f, widthPx = 300f, velocityX = -1000f))
        assertEquals(0, swipeTrackDirection(offsetPx = 0f, widthPx = 300f, velocityX = 1000f))
    }

    @Test fun zeroWidthIsSafe() {
        assertEquals(0, swipeTrackDirection(offsetPx = -100f, widthPx = 0f, velocityX = -5000f))
    }
}
