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

internal const val ACHROMATIC_CHROMA_THRESHOLD = 5.0

internal fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val achromatic = Hct.fromInt(accent.toArgb()).chroma < ACHROMATIC_CHROMA_THRESHOLD
    return dynamicColorScheme(
        seedColor = accent,
        isDark = darkTheme,
        isAmoled = false,
        style = if (achromatic) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
    ).copy(surfaceTint = Color.Transparent)
}

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
        inverseSurface = InverseSurfaceDark,
        inverseOnSurface = InverseOnSurfaceDark,
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
        inverseSurface = InverseSurface,
        inverseOnSurface = InverseOnSurface,
        surfaceTint = Color.Transparent,
    )
}

private val CloudSeed = Color(0xFF5B63D6)
private val JazzSeed = Color(0xFF7C86C9)
private val PaperSeed = Color(0xFF8A6A34)
private val MonoSeed = Color(0xFF8A8A8A)

private fun cloudScheme(darkTheme: Boolean): ColorScheme =
    accentColorScheme(CloudSeed, darkTheme).withAiryNeutrals(darkTheme)

private fun paletteScheme(palette: FacetPalette, darkTheme: Boolean): ColorScheme = when (palette) {
    FacetPalette.SIGNATURE -> cloudScheme(darkTheme)
    FacetPalette.NIGHT_JAZZ -> accentColorScheme(JazzSeed, darkTheme).withAiryNeutrals(darkTheme)
    FacetPalette.PAPER -> accentColorScheme(PaperSeed, darkTheme).withAiryNeutrals(darkTheme)
    FacetPalette.MONO -> accentColorScheme(MonoSeed, darkTheme).withAiryNeutrals(darkTheme)
    FacetPalette.DYNAMIC -> cloudScheme(darkTheme)
}

private val FallbackLightScheme = cloudScheme(darkTheme = false)
private val FallbackDarkScheme = cloudScheme(darkTheme = true)

@Composable
fun FacetTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    customAccentColor: Color? = null,
    palette: FacetPalette = FacetPalette.SIGNATURE,
    serifDisplay: Boolean = false,
    amoledBlack: Boolean = false,
    shapeStyle: FacetShapeStyle = FacetShapeStyle.DEFAULT,
    textScale: Float = 1f,
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
        customAccentColor != null ->
            accentColorScheme(customAccentColor, darkTheme).withAiryNeutrals(darkTheme)
        useDynamicColor -> {
            val dynamic = runCatching {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }.getOrNull()
            dynamic?.withAiryNeutrals(darkTheme)
                ?: if (darkTheme) FallbackDarkScheme else FallbackLightScheme
        }
        accentColor != null ->
            accentColorScheme(accentColor, darkTheme).withAiryNeutrals(darkTheme)
        palette != FacetPalette.DYNAMIC -> paletteScheme(palette, darkTheme)
        darkTheme -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
    val baseScheme = when {
        useDynamicColor || accentColor != null || customAccentColor != null -> rawColorScheme
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
        typography = scaledTypography(
            if (serifDisplay) SerifDisplayTypography else AppTypography,
            textScale.coerceIn(0.85f, 1.3f),
        ),
        shapes = shapesFor(shapeStyle),
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}
