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

private val BrassSeed = Color(0xFFC8A46A)
private val JazzSeed = Color(0xFF6C7BC4)
private val PaperSeed = Color(0xFF8A6A34)
private val MonoSeed = Color(0xFF8A8A8A)

private fun signatureScheme(darkTheme: Boolean): ColorScheme =
    accentColorScheme(BrassSeed, darkTheme).let { scheme ->
        if (darkTheme) {
            scheme.copy(
                background = Color(0xFF0E0F12),
                onBackground = Color(0xFFF2EFE8),
                surface = Color(0xFF12141A),
                onSurface = Color(0xFFF2EFE8),
                surfaceVariant = Color(0xFF2A2E36),
                onSurfaceVariant = Color(0xFFB9B3A8),
                outline = Color(0xFF8B8579),
                outlineVariant = Color(0xFF3A3E46),
                surfaceContainerLowest = Color(0xFF0A0B0E),
                surfaceContainerLow = Color(0xFF161920),
                surfaceContainer = Color(0xFF1B1E25),
                surfaceContainerHigh = Color(0xFF242831),
                surfaceContainerHighest = Color(0xFF2C303A),
                surfaceTint = Color.Transparent,
            )
        } else {
            scheme.copy(
                background = Color(0xFFF7F4EE),
                onBackground = Color(0xFF1A1815),
                surface = Color(0xFFFFFDF8),
                onSurface = Color(0xFF1A1815),
                surfaceVariant = Color(0xFFE7E1D6),
                onSurfaceVariant = Color(0xFF5E594F),
                outline = Color(0xFF8A8274),
                outlineVariant = Color(0xFFD6CFC2),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF2EEE6),
                surfaceContainer = Color(0xFFEDE8DE),
                surfaceContainerHigh = Color(0xFFE7E1D6),
                surfaceContainerHighest = Color(0xFFE0D9CC),
                surfaceTint = Color.Transparent,
            )
        }
    }

private fun paletteScheme(palette: FacetPalette, darkTheme: Boolean): ColorScheme = when (palette) {
    FacetPalette.SIGNATURE -> signatureScheme(darkTheme)
    FacetPalette.NIGHT_JAZZ -> accentColorScheme(JazzSeed, darkTheme).withAiryNeutrals(darkTheme)
    FacetPalette.PAPER -> signatureScheme(darkTheme).let { scheme ->
        accentColorScheme(PaperSeed, darkTheme).copy(
            background = scheme.background,
            onBackground = scheme.onBackground,
            surface = scheme.surface,
            onSurface = scheme.onSurface,
            surfaceContainerLowest = scheme.surfaceContainerLowest,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainer = scheme.surfaceContainer,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            surfaceContainerHighest = scheme.surfaceContainerHighest,
            surfaceTint = Color.Transparent,
        )
    }
    FacetPalette.MONO -> dynamicColorScheme(
        seedColor = MonoSeed,
        isDark = darkTheme,
        isAmoled = false,
        style = PaletteStyle.Monochrome,
    ).copy(surfaceTint = Color.Transparent)
    FacetPalette.DYNAMIC -> FallbackLightScheme
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
    palette: FacetPalette = FacetPalette.SIGNATURE,
    serifDisplay: Boolean = true,
    amoledBlack: Boolean = false,
    shapeStyle: FacetShapeStyle = FacetShapeStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val useDynamicColor = customAccentColor == null &&
        dynamicColor && palette == FacetPalette.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val rawColorScheme = when {
        customAccentColor != null -> accentColorScheme(customAccentColor, darkTheme)
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColor != null -> accentColorScheme(accentColor, darkTheme)
        palette != FacetPalette.DYNAMIC -> paletteScheme(palette, darkTheme)
        darkTheme -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
    val baseScheme = when {
        useDynamicColor -> rawColorScheme
        palette == FacetPalette.SIGNATURE || palette == FacetPalette.PAPER -> rawColorScheme
        else -> rawColorScheme.withAiryNeutrals(darkTheme)
    }
    val colorScheme = if (amoledBlack && darkTheme) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF050505),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF101010),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1D1D1D),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = if (serifDisplay) SerifDisplayTypography else AppTypography,
        shapes = shapesFor(shapeStyle),
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}
