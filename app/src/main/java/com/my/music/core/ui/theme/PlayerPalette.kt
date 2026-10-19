package com.my.music.core.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Цветовые роли плеера, полученные из доминантных цветов обложки через Palette (в стиле MD3).
 */
data class PlayerDynamicColors(
    val dominant: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surfaceContainerHigh: Color,
    val isLoading: Boolean = false,
)

private val paletteCache = mutableMapOf<String, PlayerDynamicColors>()

/**
 * Асинхронно извлекает палитру из обложки с кэшированием по URL.
 * Пока картинка грузится — возвращает дефолтный фон.
 * При ошибке извлечения — оставляет дефолтный фон.
 * Для картинок с высоким контрастом и светлых обложек цвет выразительно применяется к фону.
 */
@Composable
fun rememberPlayerDynamicColors(
    artworkUrl: String?,
    defaultSurface: Color,
    defaultPrimary: Color,
    defaultOnPrimary: Color,
    defaultPrimaryContainer: Color,
    defaultOnPrimaryContainer: Color,
): PlayerDynamicColors {
    val context = androidx.compose.ui.platform.LocalContext.current

    val fallback = remember(defaultSurface, defaultPrimary) {
        PlayerDynamicColors(
            dominant = defaultPrimary,
            backgroundTop = defaultPrimary.copy(alpha = 0.20f),
            backgroundBottom = defaultSurface,
            primary = defaultPrimary,
            onPrimary = defaultOnPrimary,
            primaryContainer = defaultPrimaryContainer,
            onPrimaryContainer = defaultOnPrimaryContainer,
            secondaryContainer = defaultPrimaryContainer.copy(alpha = 0.7f),
            onSecondaryContainer = defaultPrimary,
            surfaceContainerHigh = defaultSurface,
            isLoading = false,
        )
    }

    if (artworkUrl.isNullOrBlank()) {
        return fallback
    }

    paletteCache[artworkUrl]?.let { return it }

    var dynamicColors by remember(artworkUrl) { mutableStateOf(fallback.copy(isLoading = true)) }

    LaunchedEffect(artworkUrl) {
        val cached = paletteCache[artworkUrl]
        if (cached != null) {
            dynamicColors = cached
            return@LaunchedEffect
        }

        // Пока картинка грузится — ставим дефолтный фон
        dynamicColors = fallback.copy(isLoading = true)

        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap: Bitmap? = (result.image as? BitmapImage)?.bitmap
                        ?: runCatching { result.image.toBitmap() }.getOrNull()

                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        // Сначала ищем выразительные (насыщенные) свотчи, и только потом — доминанту:
                        // у светлых «металлических» обложек доминанта серая и почти не даёт цвета.
                        val swatch = palette.vibrantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.lightMutedSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.swatches.maxByOrNull { it.population }

                        if (swatch != null) {
                            val swatchInt = swatch.rgb
                            val luminance = ColorUtils.calculateLuminance(swatchInt)
                            val isLightCover = luminance > 0.35f

                            // Для светлых/металлических обложек поднимаем насыщенность и светлоту оттенка,
                            // иначе серый доминант на тёмном фоне выглядит почти чёрным.
                            val hsl = FloatArray(3)
                            ColorUtils.colorToHSL(swatchInt, hsl)
                            if (isLightCover) {
                                hsl[1] = (hsl[1] * 1.4f).coerceIn(0.32f, 0.82f)
                                hsl[2] = hsl[2].coerceIn(0.50f, 0.66f)
                            }
                            val tintInt = ColorUtils.HSLToColor(hsl)
                            val tintColor = Color(tintInt)
                            val tintLuminance = ColorUtils.calculateLuminance(tintInt)

                            // Больший вес цвета: фон реально окрашивается даже на тёмной теме
                            val topAlpha = if (isLightCover) 0.88f else 0.62f
                            val bottomAlpha = if (isLightCover) 0.40f else 0.22f
                            val onPrimary = if (tintLuminance > 0.5) Color.Black else Color.White

                            val colors = PlayerDynamicColors(
                                dominant = tintColor,
                                backgroundTop = tintColor.copy(alpha = topAlpha),
                                backgroundBottom = tintColor.copy(alpha = bottomAlpha),
                                primary = tintColor,
                                onPrimary = onPrimary,
                                primaryContainer = tintColor.copy(alpha = 0.30f),
                                onPrimaryContainer = if (tintLuminance > 0.5) Color.Black else tintColor,
                                secondaryContainer = tintColor.copy(alpha = 0.24f),
                                onSecondaryContainer = tintColor,
                                surfaceContainerHigh = tintColor.copy(alpha = 0.15f),
                                isLoading = false,
                            )
                            paletteCache[artworkUrl] = colors
                            withContext(Dispatchers.Main) {
                                dynamicColors = colors
                            }
                            return@withContext
                        }
                    }
                }
                // Ошибка извлечения или пустая палитра: оставляем дефолт
                withContext(Dispatchers.Main) {
                    dynamicColors = fallback
                }
            } catch (e: Exception) {
                // При ошибке загрузки — оставляем дефолтный фон
                withContext(Dispatchers.Main) {
                    dynamicColors = fallback
                }
            }
        }
    }

    return dynamicColors
}
