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

/**
 * Порог насыщенности HSL, ниже которого цвет считается ахроматическим
 * (чёрный, белый, серый): у таких цветов оттенок в HSL равен 0 (красный).
 */
internal const val ACHROMATIC_SATURATION_THRESHOLD = 0.05f

/**
 * Схема из пользовательского акцента на HCT-тональных палитрах (material-kolor).
 * Хроматический seed даёт TonalSpot, ахроматический (чёрный/белый/серый,
 * chroma < 5) — строгий Monochrome: иначе hue=0 и принудительная хрома
 * красили такой акцент в бордовый.
 */
internal fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val achromatic = Hct.fromInt(accent.toArgb()).chroma < ACHROMATIC_CHROMA_THRESHOLD
    return dynamicColorScheme(
        seedColor = accent,
        isDark = darkTheme,
        isAmoled = false,
        style = if (achromatic) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
    ).copy(surfaceTint = Color.Transparent)
}

/** Порог хромы HCT, ниже которого seed считается ахроматическим. */
internal const val ACHROMATIC_CHROMA_THRESHOLD = 5.0

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
    // «Light Airy»-нейтрали только без Dynamic Color: системные обои должны
    // перекрашивать и фон тоже, иначе тема пользователя игнорируется.
    val colorScheme = if (useDynamicColor) rawColorScheme else rawColorScheme.withAiryNeutrals(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
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
