package com.facet.music.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.facet.music.data.repository.ThemeMode

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
    // Иерархию задают роли surfaceContainer*, а не подмешивание акцента в elevation.
    surfaceTint = Color.Transparent,
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
    surfaceTint = Color.Transparent,
)

/**
 * Порог насыщенности HSL, ниже которого цвет считается ахроматическим
 * (чёрный, белый, серый): у таких цветов оттенок в HSL равен 0 (красный).
 */
internal const val ACHROMATIC_SATURATION_THRESHOLD = 0.05f

/**
 * Схема из пользовательского акцента: primary/secondary/tertiary-группы генерируются
 * из seed, поверхности остаются тёплыми. Используется для собственного акцента
 * (все версии Android) и для ручной палитры (Android ниже 12).
 *
 * Ахроматический seed (чёрный/белый/серый) даёт строго монохромную палитру:
 * насыщенность ролей обнуляется, иначе hue=0 и принудительная насыщенность
 * красили такой акцент в бордовый.
 */
internal fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    val baseHue = hsl[0]
    val achromatic = hsl[1] < ACHROMATIC_SATURATION_THRESHOLD
    // Светлоту ролей слегка тянем к светлоте seed, чтобы чёрный и белый давали
    // различимые оттенки серого, но контраст пар оставался читаемым.
    val lightnessShift = if (achromatic) (hsl[2] - 0.5f) * 0.16f else 0f

    fun tint(saturation: Float, lightness: Float, hueShift: Float = 0f): Color {
        val sat = if (achromatic) 0f else saturation
        val lit = (lightness + lightnessShift).coerceIn(0.04f, 0.96f)
        val shifted = ((baseHue + hueShift) % 360f + 360f) % 360f
        return Color(ColorUtils.HSLToColor(floatArrayOf(shifted, sat, lit)))
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
 * Нейтральные роли «Light Airy»: мягкий off-white в светлой теме и тёмно-серый
 * в тёмной. Применяются поверх любой схемы — Dynamic Color пользователя
 * перекрашивает акцент, но не фон приложения.
 */
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

/**
 * Главная тема приложения.
 * Приоритет акцента: собственный акцент пользователя (все версии Android) →
 * Dynamic Color системы (Android 12+) → ручной акцент из палитры (Android ниже 12) →
 * fallback-палитра. Нейтральные роли всегда приходят из «Light Airy»-палитры,
 * поэтому фон не зависит от обоев. Цвета главного экрана не зависят от обложки.
 *
 * @param themeMode режим темы: светлая/тёмная/системная;
 * @param customAccentColor собственный акцентный цвет из RGB-пикера — переопределяет
 * primary/secondary/tertiary на всех версиях Android, в том числе поверх Dynamic Color.
 */
/** Seed-цвет fallback-палитры: глубокий серо-синий. */
private val FallbackSeed = Color(0xFF3A4E6E)

/** Палитра без Dynamic Color и без пользовательского акцента. */
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
    // isSystemInDarkTheme вызывается до remember: результат кладётся в обычное значение
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val rawColorScheme = when {
        customAccentColor != null -> accentColorScheme(customAccentColor, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColor != null -> accentColorScheme(accentColor, darkTheme)
        darkTheme -> FallbackDarkScheme
        else -> FallbackLightScheme
    }
    val colorScheme = rawColorScheme.withAiryNeutrals(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        // MaterialTheme не предоставляет LocalContentColor, а его дефолт — Color.Black.
        // Раньше его задавал корневой Scaffold; без него любой Text без явного цвета
        // чернел на тёмном фоне. Возвращаем то же значение, что давал Scaffold.
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            content()
        }
    }
}
