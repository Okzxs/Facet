package com.facet.music.core.util

import java.util.Calendar

object SeasonProvider {

    const val WINTER = "Winter"
    const val SPRING = "Spring"
    const val SUMMER = "Summer"
    const val AUTUMN = "Autumn"

    fun currentSeason(calendar: Calendar = Calendar.getInstance()): String =
        seasonForMonth(calendar.get(Calendar.MONTH))

    fun seasonForMonth(month: Int): String = when (month) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> WINTER
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> SPRING
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> SUMMER
        Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> AUTUMN
        else -> WINTER
    }
}
