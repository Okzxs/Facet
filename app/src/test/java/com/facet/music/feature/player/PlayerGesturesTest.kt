package com.facet.music.feature.player

import androidx.compose.ui.unit.Density
import com.facet.music.data.repository.AppSettings
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

    @Test
    fun `light sensitivity closes earlier than balanced`() {
        val light = dismissThresholdPx(2_000f, density, AppSettings.SENSITIVITY_HIGH)
        val balanced = dismissThresholdPx(2_000f, density, AppSettings.SENSITIVITY_MEDIUM)
        val firm = dismissThresholdPx(2_000f, density, AppSettings.SENSITIVITY_LOW)

        assertTrue(light < balanced)
        assertTrue(balanced < firm)
        assertEquals(200f, light, 0.01f)
        assertEquals(268.8f, balanced, 0.01f)
    }

    @Test
    fun `firm sensitivity needs a longer fling`() {
        assertTrue(
            closeVelocityPxS(AppSettings.SENSITIVITY_LOW) >
                closeVelocityPxS(AppSettings.SENSITIVITY_MEDIUM),
        )
        assertTrue(
            closeVelocityPxS(AppSettings.SENSITIVITY_MEDIUM) >
                closeVelocityPxS(AppSettings.SENSITIVITY_HIGH),
        )
    }

    @Test
    fun `unknown sensitivity falls back to balanced`() {
        assertEquals(
            dismissThresholdPx(1_000f, density),
            dismissThresholdPx(1_000f, density, "TURBO"),
            0.01f,
        )
    }
}
