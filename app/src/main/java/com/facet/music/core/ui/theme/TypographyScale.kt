@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

fun scaledTypography(base: Typography, scale: Float): Typography {
    if (scale == 1f) return base
    return base.copy(
        displayLarge = base.displayLarge.scaled(scale),
        displayMedium = base.displayMedium.scaled(scale),
        displaySmall = base.displaySmall.scaled(scale),
        headlineLarge = base.headlineLarge.scaled(scale),
        headlineMedium = base.headlineMedium.scaled(scale),
        headlineSmall = base.headlineSmall.scaled(scale),
        titleLarge = base.titleLarge.scaled(scale),
        titleMedium = base.titleMedium.scaled(scale),
        titleSmall = base.titleSmall.scaled(scale),
        bodyLarge = base.bodyLarge.scaled(scale),
        bodyMedium = base.bodyMedium.scaled(scale),
        bodySmall = base.bodySmall.scaled(scale),
        labelLarge = base.labelLarge.scaled(scale),
        labelMedium = base.labelMedium.scaled(scale),
        labelSmall = base.labelSmall.scaled(scale),
        displayLargeEmphasized = base.displayLargeEmphasized.scaled(scale),
        displayMediumEmphasized = base.displayMediumEmphasized.scaled(scale),
        displaySmallEmphasized = base.displaySmallEmphasized.scaled(scale),
        headlineLargeEmphasized = base.headlineLargeEmphasized.scaled(scale),
        headlineMediumEmphasized = base.headlineMediumEmphasized.scaled(scale),
        headlineSmallEmphasized = base.headlineSmallEmphasized.scaled(scale),
        titleLargeEmphasized = base.titleLargeEmphasized.scaled(scale),
        titleMediumEmphasized = base.titleMediumEmphasized.scaled(scale),
        titleSmallEmphasized = base.titleSmallEmphasized.scaled(scale),
    )
}

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = fontSize.scaled(scale),
    lineHeight = lineHeight.scaled(scale),
)

private fun TextUnit.scaled(scale: Float): TextUnit =
    if (isSpecified) this * scale else this
