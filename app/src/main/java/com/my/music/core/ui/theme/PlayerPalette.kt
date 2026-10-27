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
import kotlinx.coroutines.CancellationException
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

private const val MAX_CACHE_ENTRIES = 32

/**
 * LRU-кэш палитр: ограничен 32 обложками, чтобы длинная сессия
 * не держала в памяти цвета всех когда-либо открытых треков.
 * Доступ синхронизирован: запись идёт с IO-диспетчера, чтение — с main.
 */
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

/**
 * Один ImageLoader на процесс: создание нового загрузчика на каждый трек
 * плодило независимые дисковые и memory-кэши.
 */
private fun imageLoader(context: android.content.Context): ImageLoader =
    sharedImageLoader ?: synchronized(paletteLock) {
        sharedImageLoader ?: ImageLoader(context.applicationContext).also { sharedImageLoader = it }
    }

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

    cachedPalette(artworkUrl)?.let { return it }

    var dynamicColors by remember(artworkUrl) { mutableStateOf(fallback.copy(isLoading = true)) }

    LaunchedEffect(artworkUrl) {
        // Кэш мог заполниться прогревом (prewarmPlayerPalette) ещё до навигации —
        // тогда берём готовые цвета сразу и не трогаем фон во время перехода.
        cachedPalette(artworkUrl)?.let {
            dynamicColors = it
            return@LaunchedEffect
        }
        // Пока картинка грузится — дефолтный фон; по готовности ставим палитру.
        dynamicColors = fallback.copy(isLoading = true)
        dynamicColors = computePlayerColors(context, artworkUrl) ?: fallback
    }

    return dynamicColors
}

/**
 * Извлекает палитру обложки и кладёт результат в кэш. Тяжёлая работа (загрузка
 * битмапа + Palette) выполняется на IO. Возвращает null, если обложку не удалось
 * декодировать или палитра пустая — вызывающий код сам подставит дефолт.
 */
private suspend fun computePlayerColors(
    context: android.content.Context,
    artworkUrl: String,
): PlayerDynamicColors? = withContext(Dispatchers.IO) {
    try {
        val loader = imageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        if (result !is SuccessResult) return@withContext null

        val bitmap: Bitmap = (result.image as? BitmapImage)?.bitmap
            ?: runCatching { result.image.toBitmap() }.getOrNull()
            ?: return@withContext null

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
            ?: return@withContext null

        val swatchInt = swatch.rgb
        val luminance = ColorUtils.calculateLuminance(swatchInt)
        val isLightCover = luminance > 0.35f

        // Оттенок фона. Насыщенность поднимаем мягко и держим в «благородном»
        // диапазоне: прежний буст ×1.4 с полом 0.32 красил даже серые и пастельные
        // обложки в кислотный цвет. Светлота ограничена средним диапазоном, чтобы
        // оттенок читался и на светлом, и на тёмном фоне.
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(swatchInt, hsl)
        hsl[1] = (hsl[1] * 1.15f).coerceIn(0.20f, 0.58f)
        hsl[2] = hsl[2].coerceIn(0.38f, 0.62f)
        val tintInt = ColorUtils.HSLToColor(hsl)
        val tintColor = Color(tintInt)
        val tintLuminance = ColorUtils.calculateLuminance(tintInt)

        // Контрастная пара к оттенку. Раньше onPrimaryContainer/onSecondaryContainer
        // возвращали сам tintColor — текст сливался с полупрозрачным контейнером.
        val onTint = if (tintLuminance > 0.5f) Color.Black else Color.White

        // Плавная рампа фона: без резкой ступени 0.88 → 0.40, которая читалась
        // как видимая полоса и делала верх «кричащим».
        val topAlpha = if (isLightCover) 0.58f else 0.46f
        val bottomAlpha = if (isLightCover) 0.26f else 0.20f

        val colors = PlayerDynamicColors(
            dominant = tintColor,
            backgroundTop = tintColor.copy(alpha = topAlpha),
            backgroundBottom = tintColor.copy(alpha = bottomAlpha),
            primary = tintColor,
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
        // Отмену пробрасываем: иначе уход с экрана не отменял загрузку битмапа.
        throw e
    } catch (e: Exception) {
        // Любая ошибка загрузки/декодирования — вернём null, фон останется дефолтным.
        null
    }
}

/**
 * Прогрев палитры до открытия плеера. Безопасно вызывать при тапе по треку:
 * к первому кадру PlayerScreen цвета уже в кэше, поэтому rememberPlayerDynamicColors
 * берёт их синхронно — без isLoading и без анимации фона прямо во время перехода.
 * Повторные вызовы для уже закэшированной обложки ничего не делают.
 */
suspend fun prewarmPlayerPalette(context: android.content.Context, artworkUrl: String?) {
    if (artworkUrl.isNullOrBlank()) return
    if (cachedPalette(artworkUrl) != null) return
    computePlayerColors(context, artworkUrl)
}
