@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.core.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.facet.music.R

@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: Int) = Font(
    resId = R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun playfair(weight: Int) = Font(
    resId = R.font.playfair_display,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PlayfairDisplay = FontFamily(
    playfair(400),
    playfair(600),
    playfair(700),
)

val GoogleSansFlex = FontFamily(
    googleSansFlex(400),
    googleSansFlex(500),
    googleSansFlex(600),
    googleSansFlex(700),
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Medium,
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
        fontWeight = FontWeight.Medium,
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
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)


val SerifDisplayTypography = AppTypography.copy(
    displayLarge = AppTypography.displayLarge.copy(fontFamily = PlayfairDisplay),
    displayMedium = AppTypography.displayMedium.copy(fontFamily = PlayfairDisplay),
    displaySmall = AppTypography.displaySmall.copy(fontFamily = PlayfairDisplay),
    headlineLarge = AppTypography.headlineLarge.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = AppTypography.headlineMedium.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = AppTypography.headlineSmall.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
    displayLargeEmphasized = AppTypography.displayLargeEmphasized.copy(
        fontFamily = PlayfairDisplay,
    ),
    displayMediumEmphasized = AppTypography.displayMediumEmphasized.copy(
        fontFamily = PlayfairDisplay,
    ),
    displaySmallEmphasized = AppTypography.displaySmallEmphasized.copy(
        fontFamily = PlayfairDisplay,
    ),
    headlineLargeEmphasized = AppTypography.headlineLargeEmphasized.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
    headlineMediumEmphasized = AppTypography.headlineMediumEmphasized.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmallEmphasized = AppTypography.headlineSmallEmphasized.copy(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
    ),
)
