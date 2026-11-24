package com.facet.music.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricClockTest {

    private var positionMs = 0L
    private var elapsedMs = 0L
    private val clock = LyricClock({ positionMs }, { elapsedMs })

    @Test
    fun `small drift never moves time backwards`() {
        positionMs = 1_000L
        clock.reset()
        clock.setPlaying(true)

        elapsedMs += 16L
        positionMs = 1_000L
        val first = clock.tick()

        elapsedMs += 16L
        positionMs = 900L
        val second = clock.tick()

        assertTrue(first >= 1_000L)
        assertTrue(second >= first)
    }

    @Test
    fun `forward seek snaps to the new position`() {
        positionMs = 1_000L
        clock.reset()
        clock.setPlaying(true)

        elapsedMs += 16L
        positionMs = 60_000L
        val value = clock.tick()

        assertEquals(60_000L, value)
    }

    @Test
    fun `backward seek snaps to the new position`() {
        positionMs = 60_000L
        clock.reset()
        clock.setPlaying(true)

        elapsedMs += 16L
        positionMs = 5_000L
        val value = clock.tick()

        assertEquals(5_000L, value)
    }

    @Test
    fun `paused clock does not run forward`() {
        positionMs = 10_000L
        clock.reset()
        clock.setPlaying(false)

        elapsedMs += 5_000L
        val first = clock.tick()
        elapsedMs += 5_000L
        val second = clock.tick()

        assertEquals(first, second)
        assertTrue(first >= 10_000L)
    }

    @Test
    fun `playing clock advances with elapsed time`() {
        positionMs = 10_000L
        clock.reset()
        clock.setPlaying(true)

        elapsedMs += 100L
        positionMs = 10_100L
        val value = clock.tick()

        assertTrue(value >= 10_100L)
    }
}
