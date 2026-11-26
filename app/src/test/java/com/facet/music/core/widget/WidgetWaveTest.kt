package com.facet.music.core.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetWaveTest {

    @Test
    fun `phase starts at zero`() {
        assertEquals(0f, WidgetWave.phase(0L), 0.001f)
    }

    @Test
    fun `phase advances with position`() {
        assertEquals(kotlin.math.PI.toFloat(), WidgetWave.phase(800L), 0.01f)
        assertEquals(0f, WidgetWave.phase(1600L), 0.01f)
    }

    @Test
    fun `phase wraps every period`() {
        assertEquals(WidgetWave.phase(100L), WidgetWave.phase(1700L), 0.001f)
        assertEquals(WidgetWave.phase(900L), WidgetWave.phase(2500L), 0.001f)
    }
}
