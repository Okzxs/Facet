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

private fun cloudStyle(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = GoogleSansFlex,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = cloudStyle(56, 64, FontWeight.Normal, -0.25),
    displayMedium = cloudStyle(44, 52, FontWeight.Normal, -0.15),
    displaySmall = cloudStyle(34, 42, FontWeight.Normal, -0.1),
    headlineLarge = cloudStyle(30, 40, FontWeight.Medium),
    headlineMedium = cloudStyle(26, 34, FontWeight.Medium),
    headlineSmall = cloudStyle(22, 30, FontWeight.Medium),
    titleLarge = cloudStyle(20, 28, FontWeight.Medium),
    titleMedium = cloudStyle(16, 24, FontWeight.Medium, 0.1),
    titleSmall = cloudStyle(14, 20, FontWeight.Medium, 0.1),
    bodyLarge = cloudStyle(16, 26, FontWeight.Normal, 0.2),
    bodyMedium = cloudStyle(14, 22, FontWeight.Normal, 0.2),
    bodySmall = cloudStyle(12, 18, FontWeight.Normal, 0.3),
    labelLarge = cloudStyle(14, 20, FontWeight.Medium, 0.2),
    labelMedium = cloudStyle(12, 16, FontWeight.Medium, 0.4),
    labelSmall = cloudStyle(11, 16, FontWeight.Medium, 0.4),
    displayLargeEmphasized = cloudStyle(56, 64, FontWeight.Medium, -0.25),
    displayMediumEmphasized = cloudStyle(44, 52, FontWeight.Medium, -0.15),
    displaySmallEmphasized = cloudStyle(34, 42, FontWeight.Medium, -0.1),
    headlineLargeEmphasized = cloudStyle(30, 40, FontWeight.SemiBold),
    headlineMediumEmphasized = cloudStyle(26, 34, FontWeight.SemiBold),
    headlineSmallEmphasized = cloudStyle(22, 30, FontWeight.SemiBold),
    titleLargeEmphasized = cloudStyle(20, 28, FontWeight.SemiBold),
    titleMediumEmphasized = cloudStyle(16, 24, FontWeight.SemiBold, 0.1),
    titleSmallEmphasized = cloudStyle(14, 20, FontWeight.SemiBold, 0.1),
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
