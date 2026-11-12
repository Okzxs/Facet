package com.facet.music.core.ui.theme

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

private const val MAX_CACHE_ENTRIES = 32

private val paletteCache = object :
    LinkedHashMap<String, PlayerDynamicColors>(MAX_CACHE_ENTRIES, 0.75f, true) {
    override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<String, PlayerDynamicColors>?,
    ): Boolean = size > MAX_CACHE_ENTRIES
}

private val paletteLock = Any()

@Volatile
private var sharedImageLoader: ImageLoader? = null

private fun cachedPalette(url: String): PlayerDynamicColors? =
    synchronized(paletteLock) { paletteCache[url] }

private fun putPalette(url: String, colors: PlayerDynamicColors) {
    synchronized(paletteLock) { paletteCache[url] = colors }
}

private fun imageLoader(context: android.content.Context): ImageLoader =
    sharedImageLoader ?: synchronized(paletteLock) {
        sharedImageLoader ?: ImageLoader(context.applicationContext).also { sharedImageLoader = it }
    }

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

    cachedPalette(artworkUrl)?.let { return it }

    var dynamicColors by remember(artworkUrl) { mutableStateOf(fallback.copy(isLoading = true)) }

    LaunchedEffect(artworkUrl) {
        cachedPalette(artworkUrl)?.let {
            dynamicColors = it
            return@LaunchedEffect
        }
        dynamicColors = fallback.copy(isLoading = true)
        dynamicColors = computePlayerColors(context, artworkUrl) ?: fallback
    }

    return dynamicColors
}

private suspend fun computePlayerColors(
    context: android.content.Context,
    artworkUrl: String,
): PlayerDynamicColors? = withContext(Dispatchers.IO) {
    try {
        val loader = imageLoader(context)
        val paletteUrl = com.facet.music.core.ui.artwork.ArtworkUrl.highRes(artworkUrl)
            ?: artworkUrl
        val request = ImageRequest.Builder(context)
            .data(paletteUrl)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        if (result !is SuccessResult) return@withContext null

        val bitmap: Bitmap = (result.image as? BitmapImage)?.bitmap
            ?: runCatching { result.image.toBitmap() }.getOrNull()
            ?: return@withContext null

        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.mutedSwatch
            ?: palette.lightMutedSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.swatches.maxByOrNull { it.population }
            ?: return@withContext null

        val swatchInt = swatch.rgb
        val luminance = ColorUtils.calculateLuminance(swatchInt)
        val isLightCover = luminance > 0.35f

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatchInt, hsl)
        val achromatic = hsl[1] < ACHROMATIC_SATURATION_THRESHOLD
        hsl[1] = if (achromatic) 0f else (hsl[1] * 1.15f).coerceIn(0.20f, 0.58f)
        hsl[2] = hsl[2].coerceIn(0.38f, 0.62f)
        val tintInt = ColorUtils.HSLToColor(hsl)
        val tintColor = Color(tintInt)

        val (accessibleTint, onTint) = bestContentColor(tintColor)

        val topAlpha = if (isLightCover) 0.58f else 0.46f
        val bottomAlpha = if (isLightCover) 0.26f else 0.20f

        val colors = PlayerDynamicColors(
            dominant = accessibleTint,
            backgroundTop = tintColor.copy(alpha = topAlpha),
            backgroundBottom = tintColor.copy(alpha = bottomAlpha),
            primary = accessibleTint,
            onPrimary = onTint,
            primaryContainer = tintColor.copy(alpha = 0.30f),
            onPrimaryContainer = onTint,
            secondaryContainer = tintColor.copy(alpha = 0.24f),
            onSecondaryContainer = onTint,
            surfaceContainerHigh = tintColor.copy(alpha = 0.15f),
            isLoading = false,
        )
        putPalette(artworkUrl, colors)
        colors
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

suspend fun prewarmPlayerPalette(context: android.content.Context, artworkUrl: String?) {
    if (artworkUrl.isNullOrBlank()) return
    if (cachedPalette(artworkUrl) != null) return
    computePlayerColors(context, artworkUrl)
}
