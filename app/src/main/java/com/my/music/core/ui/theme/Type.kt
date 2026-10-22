package com.my.music.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.my.music.R

/**
 * Google Sans Flex (variable, OFL) — единый шрифт приложения.
 * Каждый вес — это одна и та же variable-тушка с настройкой оси wght (API 26+).
 */
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: Int) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GoogleSansFlex = FontFamily(
    googleSansFlex(400),
    googleSansFlex(500),
    googleSansFlex(600),
    googleSansFlex(700),
    googleSansFlex(800),
)

/**
 * Выразительная типографическая шкала Material 3 Expressive:
 * плотный трекинг в крупных заголовках, SemiBold/ExtraBold в акцентах,
 * спокойный контраст второстепенного текста.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp,
    ),
)
