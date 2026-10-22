package com.my.music.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
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
    inversePrimary = InversePrimary,
    surfaceTint = Primary,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
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
    inversePrimary = InversePrimaryDark,
    surfaceTint = PrimaryDark,
)

/**
 * Схема из пользовательского акцента для Android ниже 12:
 * primary-группа генерируется из seed, поверхности остаются тёплыми.
 */
private fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    val baseHue = hsl[0]

    fun tint(saturation: Float, lightness: Float, hueShift: Float = 0f): Color {
        val shifted = ((baseHue + hueShift) % 360f + 360f) % 360f
        return Color(ColorUtils.HSLToColor(floatArrayOf(shifted, saturation, lightness)))
    }

    return if (darkTheme) {
        DarkColorScheme.copy(
            primary = tint(0.55f, 0.78f),
            onPrimary = tint(0.60f, 0.18f),
            primaryContainer = tint(0.45f, 0.32f),
            onPrimaryContainer = tint(0.60f, 0.92f),
            secondary = tint(0.25f, 0.80f),
            onSecondary = tint(0.30f, 0.18f),
            secondaryContainer = tint(0.30f, 0.30f),
            onSecondaryContainer = tint(0.40f, 0.92f),
            tertiary = tint(0.40f, 0.80f, 40f),
            onTertiary = tint(0.40f, 0.18f, 40f),
            tertiaryContainer = tint(0.35f, 0.30f, 40f),
            onTertiaryContainer = tint(0.45f, 0.92f, 40f),
        )
    } else {
        LightColorScheme.copy(
            primary = tint(0.50f, 0.42f),
            onPrimary = Color.White,
            primaryContainer = tint(0.55f, 0.90f),
            onPrimaryContainer = tint(0.60f, 0.16f),
            secondary = tint(0.22f, 0.45f),
            onSecondary = Color.White,
            secondaryContainer = tint(0.28f, 0.90f),
            onSecondaryContainer = tint(0.35f, 0.18f),
            tertiary = tint(0.35f, 0.45f, 40f),
            onTertiary = Color.White,
            tertiaryContainer = tint(0.38f, 0.90f, 40f),
            onTertiaryContainer = tint(0.40f, 0.18f, 40f),
        )
    }
}

/**
 * Главная тема приложения.
 * Приоритет: Dynamic Color системы (Android 12+) → ручной акцент (Android ниже 12) → fallback-палитра.
 * Цвета главного экрана не зависят от обложки альбома.
 */
@Composable
fun MyMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColor != null -> accentColorScheme(accentColor, darkTheme)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
