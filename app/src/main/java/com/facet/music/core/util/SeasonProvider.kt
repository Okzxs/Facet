package com.facet.music.core.util

import java.util.Calendar

/**
 * Определение сезона для «Воспоминаний» и Wave.
 * Сезоны хранятся в БД строками: Winter / Spring / Summer / Autumn.
 */
object SeasonProvider {

    const val WINTER = "Winter"
    const val SPRING = "Spring"
    const val SUMMER = "Summer"
    const val AUTUMN = "Autumn"

    /**
     * Текущий сезон на основе системной даты.
     */
    fun currentSeason(calendar: Calendar = Calendar.getInstance()): String =
        seasonForMonth(calendar.get(Calendar.MONTH))

    /**
     * Сезон по номеру месяца в терминах [Calendar.MONTH] (0 — январь).
     */
    fun seasonForMonth(month: Int): String = when (month) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> WINTER
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> SPRING
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> SUMMER
        Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> AUTUMN
        else -> WINTER
    }
}
