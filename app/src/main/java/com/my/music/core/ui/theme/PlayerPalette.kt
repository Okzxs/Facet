package com.my.music.core.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
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
)

private val paletteCache = mutableMapOf<String, PlayerDynamicColors>()

/**
 * Асинхронно извлекает палитру из обложки с кэшированием по URL.
 * Не пересчитывается на тиках прогресса.
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
            backgroundTop = defaultPrimary.copy(alpha = 0.18f),
            backgroundBottom = defaultSurface,
            primary = defaultPrimary,
            onPrimary = defaultOnPrimary,
            primaryContainer = defaultPrimaryContainer,
            onPrimaryContainer = defaultOnPrimaryContainer,
            secondaryContainer = defaultPrimaryContainer.copy(alpha = 0.7f),
            onSecondaryContainer = defaultPrimary,
            surfaceContainerHigh = defaultSurface,
        )
    }

    if (artworkUrl.isNullOrBlank()) {
        return fallback
    }

    paletteCache[artworkUrl]?.let { return it }

    var dynamicColors by remember(artworkUrl) { mutableStateOf(fallback) }

    LaunchedEffect(artworkUrl) {
        withContext(Dispatchers.IO) {
            val cached = paletteCache[artworkUrl]
            if (cached != null) {
                dynamicColors = cached
                return@withContext
            }

            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? BitmapImage)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatch = palette.dominantSwatch
                            ?: palette.vibrantSwatch
                            ?: palette.mutedSwatch

                        val dominantInt = swatch?.rgb ?: android.graphics.Color.DKGRAY
                        val dominantColor = Color(dominantInt)

                        val colors = PlayerDynamicColors(
                            dominant = dominantColor,
                            backgroundTop = dominantColor.copy(alpha = 0.32f),
                            backgroundBottom = defaultSurface,
                            primary = dominantColor,
                            onPrimary = Color(swatch?.titleTextColor ?: android.graphics.Color.WHITE),
                            primaryContainer = dominantColor.copy(alpha = 0.28f),
                            onPrimaryContainer = dominantColor,
                            secondaryContainer = dominantColor.copy(alpha = 0.22f),
                            onSecondaryContainer = dominantColor,
                            surfaceContainerHigh = dominantColor.copy(alpha = 0.14f),
                        )
                        paletteCache[artworkUrl] = colors
                        withContext(Dispatchers.Main) {
                            dynamicColors = colors
                        }
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки извлечения палитры, используем фолбэк
            }
        }
    }

    return dynamicColors
}
