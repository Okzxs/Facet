@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.facet.music.data.repository.ThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct

internal const val ACHROMATIC_SATURATION_THRESHOLD = 0.05f

internal fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val achromatic = Hct.fromInt(accent.toArgb()).chroma < ACHROMATIC_CHROMA_THRESHOLD
    return dynamicColorScheme(
        seedColor = accent,
        isDark = darkTheme,
        isAmoled = false,
        style = if (achromatic) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
    ).copy(surfaceTint = Color.Transparent)
}

internal const val ACHROMATIC_CHROMA_THRESHOLD = 5.0

private fun ColorScheme.withAiryNeutrals(darkTheme: Boolean): ColorScheme = if (darkTheme) {
    copy(
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        outlineVariant = OutlineVariantDark,
        surfaceDim = SurfaceDimDark,
        surfaceBright = SurfaceBrightDark,
        surfaceContainerLowest = SurfaceContainerLowestDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        surfaceTint = Color.Transparent,
    )
} else {
    copy(
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        outlineVariant = OutlineVariant,
        surfaceDim = SurfaceDim,
        surfaceBright = SurfaceBright,
        surfaceContainerLowest = SurfaceContainerLowest,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest,
        surfaceTint = Color.Transparent,
    )
}

private val FallbackSeed = Color(0xFF3A4E6E)

private val FallbackLightScheme = accentColorScheme(FallbackSeed, darkTheme = false)
private val FallbackDarkScheme = accentColorScheme(FallbackSeed, darkTheme = true)

@Composable
fun FacetTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    customAccentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val useDynamicColor = customAccentColor == null &&
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val rawColorScheme = when {
        customAccentColor != null -> accentColorScheme(customAccentColor, darkTheme)
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColor != null -> accentColorScheme(accentColor, darkTheme)
        darkTheme -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
    val colorScheme = if (useDynamicColor) rawColorScheme else rawColorScheme.withAiryNeutrals(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}
