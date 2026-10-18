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
                        val swatch = palette.dominantSwatch
                            ?: palette.vibrantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.lightMutedSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.swatches.maxByOrNull { it.population }

                        if (swatch != null) {
                            val dominantInt = swatch.rgb
                            val dominantColor = Color(dominantInt)
                            val luminance = ColorUtils.calculateLuminance(dominantInt)

                            // Для светлых и контрастных обложек обеспечиваем насыщенный оттенок фона,
                            // чтобы фон не поглощался темной системной темой.
                            val topAlpha = if (luminance > 0.4) 0.72f else 0.55f
                            val bottomAlpha = if (luminance > 0.4) 0.28f else 0.16f
                            val onPrimary = if (luminance > 0.5) Color.Black else Color.White

                            val colors = PlayerDynamicColors(
                                dominant = dominantColor,
                                backgroundTop = dominantColor.copy(alpha = topAlpha),
                                backgroundBottom = dominantColor.copy(alpha = bottomAlpha),
                                primary = dominantColor,
                                onPrimary = onPrimary,
                                primaryContainer = dominantColor.copy(alpha = 0.28f),
                                onPrimaryContainer = if (luminance > 0.5) Color.Black else dominantColor,
                                secondaryContainer = dominantColor.copy(alpha = 0.22f),
                                onSecondaryContainer = dominantColor,
                                surfaceContainerHigh = dominantColor.copy(alpha = 0.14f),
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
