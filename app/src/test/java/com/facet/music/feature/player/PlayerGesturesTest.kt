package com.facet.music.feature.player

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGesturesTest {

    private val density = Density(2.8f, 1f)

    @Test
    fun `short screens use fourteen percent height`() {
        assertEquals(140f, dismissThresholdPx(1_000f, density), 0.01f)
    }

    @Test
    fun `tall screens cap the threshold at 96dp`() {
        assertEquals(268.8f, dismissThresholdPx(2_400f, density), 0.01f)
    }

    @Test
    fun `threshold is never longer than the screen`() {
        assertTrue(dismissThresholdPx(50f, density) <= 50f)
    }
}
