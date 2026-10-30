package com.facet.music.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SeasonProviderTest {

    @Test
    fun `maps every month to the expected season`() {
        val expected = mapOf(
            Calendar.JANUARY to SeasonProvider.WINTER,
            Calendar.FEBRUARY to SeasonProvider.WINTER,
            Calendar.MARCH to SeasonProvider.SPRING,
            Calendar.APRIL to SeasonProvider.SPRING,
            Calendar.MAY to SeasonProvider.SPRING,
            Calendar.JUNE to SeasonProvider.SUMMER,
            Calendar.JULY to SeasonProvider.SUMMER,
            Calendar.AUGUST to SeasonProvider.SUMMER,
            Calendar.SEPTEMBER to SeasonProvider.AUTUMN,
            Calendar.OCTOBER to SeasonProvider.AUTUMN,
            Calendar.NOVEMBER to SeasonProvider.AUTUMN,
            Calendar.DECEMBER to SeasonProvider.WINTER,
        )

        expected.forEach { (month, season) ->
            assertEquals("Месяц $month", season, SeasonProvider.seasonForMonth(month))
        }
    }

    @Test
    fun `current season uses calendar month`() {
        val calendar = Calendar.getInstance().apply { set(Calendar.MONTH, Calendar.JULY) }

        assertEquals(SeasonProvider.SUMMER, SeasonProvider.currentSeason(calendar))
        assertTrue(
            SeasonProvider.currentSeason() in setOf(
                SeasonProvider.WINTER,
                SeasonProvider.SPRING,
                SeasonProvider.SUMMER,
                SeasonProvider.AUTUMN,
            ),
        )
    }
}
