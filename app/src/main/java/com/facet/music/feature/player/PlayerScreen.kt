package com.facet.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateFormat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.download.DownloadState
import com.facet.music.core.location.LocationPermissions
import com.facet.music.core.ui.components.ExpressivePlayerSlider
import com.facet.music.core.ui.components.PlayerControlIsland
import com.facet.music.core.ui.components.PlayerQueueSheet
import com.facet.music.core.ui.components.PlayerSliderState
import com.facet.music.core.ui.components.AddToPlaylistSheet
import com.facet.music.core.ui.components.SleepTimerSheet
import com.facet.music.core.ui.theme.PillShape
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.facet.music.core.ui.theme.PlayerDynamicColors
import com.facet.music.core.ui.theme.rememberPlayerDynamicColors
import com.facet.music.core.player.PlaybackTuning
import com.facet.music.core.player.PlaybackVariation
import com.facet.music.data.model.LyricsResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.isMetadataOnly
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

/**
 * Вариант компоновки hero-зоны (для сравнения на устройстве, поменяй цифру и пересобери):
 * 1 — «Редакция»: обложка по центру, стеклянная карточка с огромным названием свисает с её нижнего края;
 * 2 — «Смещение»: обложка свисает за правый край экрана, карточка с названием — слева;
 * 3 — «На обложке»: название лежит прямо на обложке в матово-стеклянной плашке.
 */
private const val PLAYER_LAYOUT_VARIANT = 1

/** Порог закрытия свайпом вниз: 30% высоты экрана. */
private const val DISMISS_THRESHOLD = 0.30f

/** Свайп по обложке: порог переключения трека. */
private val SWIPE_TRACK_THRESHOLD = 56.dp

/**
 * Постоянный слот под полоску прогресса активной строки текста.
 * Высота строки не должна зависеть от того, активна она или нет: иначе
 * список пересчитывает смещения и на каждой смене строки дёргается.
 */
private val LyricBarSlot = 12.dp

/** Параметры компоновки для конкретного варианта. */
private data class HeroStyle(
    val widthFraction: Float,
    val alignment: Alignment,
    val topPadding: Dp,
    val offsetX: Dp,
    val overlap: Dp,
    val titleOnArtwork: Boolean,
)

private fun heroStyle(variant: Int): HeroStyle = when (variant) {
    2 -> HeroStyle(0.62f, Alignment.TopEnd, 26.dp, 16.dp, 60.dp, false)
    3 -> HeroStyle(0.94f, Alignment.TopCenter, 4.dp, 0.dp, 0.dp, true)
    else -> HeroStyle(0.88f, Alignment.TopCenter, 6.dp, 0.dp, 46.dp, false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onFindOnYouTube: (Track) -> Unit,
    onArtistClick: (Track) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    coverModifier: Modifier = Modifier,
    foregroundModifier: Modifier = Modifier,
    isClosing: Boolean = false,
    swipeState: PlayerSwipeState,
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val isDisliked by viewModel.isDisliked.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()

    var isLyricsMode by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showTuning by remember { mutableStateOf(false) }
    var showVariations by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ─── Разрешение на геопозицию ────────────────────────────────────────
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(LocationPermissions.hasPermission(context))
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var pendingLike by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRequested = true
        hasLocationPermission = result.values.any { it } ||
            LocationPermissions.hasPermission(context)
        // Лайк не теряем: без разрешения он сохранится без координат
        if (pendingLike) {
            pendingLike = false
            viewModel.toggleLike()
        }
    }

    // Разрешение могли выдать или отозвать в системных настройках — обновляем статус
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = LocationPermissions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggleLikeWithPermission() {
        if (!isLiked && !LocationPermissions.hasPermission(context)) {
            pendingLike = true
            locationPermissionLauncher.launch(LocationPermissions.permissions)
        } else {
            viewModel.toggleLike()
        }
    }

    fun onPermissionBannerAction() {
        if (permissionRequested && !LocationPermissions.hasPermission(context)) {
            LocationPermissions.openAppSettings(context)
        } else {
            locationPermissionLauncher.launch(LocationPermissions.permissions)
        }
    }

    // Текущий трек берём из PlayerController (он всегда актуален после play())
    val track = playerState.currentTrack ?: uiState.track
    // Локальный файл уже лежит на диске — скачивать его нечего.
    val canDownload = track != null && track.source != Source.LOCAL
    // Время остановки таймера сна — в формате локали устройства; пункт меню
    // получает уже готовую строку, DateFormat в Compose не ресурс.
    val sleepTimerEndsAtText = sleepTimer?.let { timer ->
        DateFormat.getTimeFormat(context).format(Date(timer.endsAtMillis))
    }

    // Скачивание невозможно (нет прав/404/HLS-ошибка) — сообщаем пользователю.
    // Крестик живёт секунду, поэтому Snackbar запускаем в scope композиции,
    // иначе смена состояния отменила бы его вместе с LaunchedEffect.
    val snackbarScope = rememberCoroutineScope()
    // Тексты снекбара читаем здесь: внутри LaunchedEffect stringResource недоступен.
    val downloadFailedMessage = stringResource(R.string.player_download_failed)
    val findOnYouTubeLabel = stringResource(R.string.player_find_on_youtube)
    LaunchedEffect(downloadState, track?.id) {
        val error = downloadState as? DownloadState.Error
        if (error?.cause != DownloadState.Cause.FAILED || track == null) return@LaunchedEffect

        val isSoundCloud = track.source == Source.SOUNDCLOUD
        // Точная причина из outputData воркера важнее общего текста по источнику.
        val workerMessage = DownloadMessages.friendly(error.message)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = workerMessage ?: if (isSoundCloud) {
                    DownloadMessages.SOURCE_NOT_FOUND
                } else {
                    downloadFailedMessage
                },
                actionLabel = if (isSoundCloud) findOnYouTubeLabel else null,
                duration = if (isSoundCloud) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (isSoundCloud && result == SnackbarResult.ActionPerformed) {
                onFindOnYouTube(track)
            }
        }
    }

    // Резолвер не нашёл progressive-источник: воркер даже не создавался.
    LaunchedEffect(uiState.downloadError) {
        val message = uiState.downloadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeDownloadError()
    }

    // Гарантируем запрос текста при открытии экрана или смене трека
    LaunchedEffect(track?.id) {
        if (track != null && uiState.lyrics == null && !uiState.isLyricsLoading) {
            viewModel.loadLyricsIfNeeded(track)
        }
    }

    // ─── Тонированный фон и палитра по обложке (Palette) ────────────────
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val defaultOnPrimary = MaterialTheme.colorScheme.onPrimary
    val defaultPrimaryContainer = MaterialTheme.colorScheme.primaryContainer
    val defaultOnPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    // Динамические цвета включены — палитра от обложки; выключены — акцент из темы
    val rawPlayerColors = rememberPlayerDynamicColors(
        artworkUrl = if (appSettings.dynamicCoverColors) track?.artworkUrl else null,
        defaultSurface = defaultSurface,
        defaultPrimary = defaultPrimary,
        defaultOnPrimary = defaultOnPrimary,
        defaultPrimaryContainer = defaultPrimaryContainer,
        defaultOnPrimaryContainer = defaultOnPrimaryContainer,
    )

    // Собственный акцент пользователя (RGB-пикер) подмешивается в палитру обложки,
    // чтобы градиенты и подсветки плеера не конфликтовали с выбранной темой.
    val customAccent = appSettings.customAccentColor?.let { Color(it) }
    val playerColors = remember(rawPlayerColors, customAccent) {
        if (customAccent == null) {
            rawPlayerColors
        } else {
            blendPaletteWithAccent(rawPlayerColors, customAccent)
        }
    }

    // Акцент плеера: цвет обложки, если включены динамические цвета, иначе — тема.
    // Цвет анимируется, чтобы при смене трека фон и кнопки перетекали плавно.
    val dynamicColorsEnabled = appSettings.dynamicCoverColors
    val targetAccent = if (dynamicColorsEnabled) playerColors.primary else defaultPrimary
    val coverAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 420),
        label = "coverAccent",
    )
    val onCoverAccent = if (coverAccent.luminance() > 0.5f) Color.Black else Color.White
    val themeBackground = MaterialTheme.colorScheme.background

    // Кнопки-сквирклы под обложкой (скачать/лайк/дизлайк) живут в той же палитре,
    // что фон и кнопка Play: при динамических цветах контейнер — оттенок обложки,
    // иначе — контейнер темы. Цвета анимируются вместе с акцентом (420 мс),
    // чтобы при смене трека кнопки перетекали, а не переключались рывком.
    val heroActionContainer by animateColorAsState(
        targetValue = if (dynamicColorsEnabled) playerColors.primaryContainer else defaultPrimaryContainer,
        animationSpec = tween(durationMillis = 420),
        label = "heroActionContainer",
    )
    // Спокойное состояние: лёгкий тон обложки вместо серой подложки onSurface.
    val heroActionIdleContainer by animateColorAsState(
        targetValue = if (dynamicColorsEnabled) {
            coverAccent.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        animationSpec = tween(durationMillis = 420),
        label = "heroActionIdleContainer",
    )

    // Фон: при включённых динамических цветах — заметная тонировка цветом обложки
    // (28% сверху, 14% в середине), при выключенных — строго чистый background.
    val backgroundBrush = remember(dynamicColorsEnabled, coverAccent, themeBackground) {
        if (dynamicColorsEnabled) {
            Brush.verticalGradient(
                colors = listOf(
                    lerpColor(themeBackground, coverAccent, 0.28f),
                    lerpColor(themeBackground, coverAccent, 0.14f),
                    themeBackground,
                ),
            )
        } else {
            SolidColor(themeBackground)
        }
    }

    // Фон режима текста: градиент акцента палитры, проявляющийся на весь экран.
    // Отдельная анимация нужна, потому что слой живёт в корне экрана, а не в hero-зоне.
    val lyricsBackdrop by animateFloatAsState(
        targetValue = if (isLyricsMode) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricsBackdrop",
    )

    val surfaceColor = playerColors.surfaceContainerHigh
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    // ─── Свайп вниз для закрытия плеера ─────────────────────────────────
    // Жест живёт на корневом Box (он оборачивает весь экран вместе с шапкой)
    // и видит касание в Initial-проходе раньше LazyColumn/verticalScroll.
    // Поэтому свайп вниз работает и поверх текста, а вверх жест уходит списку.
    //
    // Смещение и прогресс живут в PlayerSwipeState (AppNavGraph): их читают
    // только graphicsLayer-блоки, поэтому drag и пружина не пересобирают
    // тяжёлые панели с текстом и кнопками — анимация идёт на RenderThread.
    LaunchedEffect(Unit) {
        swipeState.reset()
    }

    // Границы острова управления в координатах корня: касание из этой зоны
    // не запускает закрытие — кнопки и слайдер должны работать как обычно.
    var controlIslandBounds by remember { mutableStateOf<Rect?>(null) }
    // Границы обложки: свайп по ней переключает трек, а не закрывает плеер.
    var artworkBounds by remember { mutableStateOf<Rect?>(null) }
    val artworkBoundsState = rememberUpdatedState(artworkBounds)
    // Границы самого LazyColumn с текстом: только по нему решаем,
    // скроллить текст или тянуть плеер (шапка и пустые зоны — всегда плеер).
    var lyricsListBounds by remember { mutableStateOf<Rect?>(null) }
    // Состояние списка текста поднято сюда: по нему жест решает,
    // тянуть плеер или отдать движение списку.
    val lyricsListState = rememberLazyListState()

    // Обложка сжимается и уезжает вниз-влево к мини-плееру прямо во время
    // перетаскивания. Корневой Box уже едет на смещении свайпа, поэтому здесь
    // добавляем только дополнительный сдвиг к нижней панели. При закрытии
    // обложку забирает shared-переход: ручной сдвиг сбрасываем, чтобы морф
    // не начинался из двойной трансформации.
    val coverSwipeModifier = Modifier.graphicsLayer {
        val progress = if (swipeState.isDismissed) 0f else swipeState.progress
        if (progress > 0f) {
            val scale = 1f - 0.75f * progress
            scaleX = scale
            scaleY = scale
            translationX = -swipeState.screenWidthPx * 0.35f * progress
            translationY = swipeState.screenHeightPx * 0.4f * progress
            clip = false
        }
    }

    fun settlePlayer() {
        swipeState.settle()
    }

    fun dismissPlayer() {
        if (swipeState.isDismissed) return
        swipeState.dismiss()
        onBack()
    }

    /** Отпускание пальца: выше порога — закрыть, иначе вернуть пружиной. */
    fun finishPlayerDrag() {
        val height = swipeState.screenHeightPx
        val threshold = height * DISMISS_THRESHOLD
        if (height > 0f && swipeState.offsetPx > threshold) {
            dismissPlayer()
        } else {
            settlePlayer()
        }
    }

    val swipeToDismissModifier = if (showQueueSheet || swipeState.isDismissed) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // Initial-проход: корень получает событие раньше списка текста,
                // поэтому может забрать вертикальный жест себе.
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                // Остров управления не участвует в закрытии.
                val island = controlIslandBounds
                if (island != null && island.contains(down.position)) {
                    return@awaitEachGesture
                }
                // Обложка отдана свайпу-переключению трека.
                val artwork = artworkBoundsState.value
                if (artwork != null && artwork.contains(down.position)) {
                    return@awaitEachGesture
                }

                var dragStarted = false
                var totalDy = 0f

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            if (dragStarted) change.consume()
                            break
                        }
                        if (!change.pressed) break

                        val dy = change.positionChange().y
                        totalDy += dy

                        if (!dragStarted) {
                            if (abs(totalDy) < viewConfiguration.touchSlop) continue
                            // Вертикаль принадлежит списку только если касание
                            // началось внутри LazyColumn и текст уже проскроллен:
                            // тогда вниз листаем текст, а не тянем шторку.
                            // Шапка, обложка и пустые зоны всегда двигают плеер.
                            val inLyricsList =
                                lyricsListBounds?.contains(down.position) == true
                            val lyricsAtTop = !lyricsListState.canScrollBackward
                            if (totalDy <= 0f || (isLyricsMode && inLyricsList && !lyricsAtTop)) {
                                return@awaitEachGesture
                            }
                            dragStarted = true
                            swipeState.beginDrag()
                            // Накопленный до slop путь тоже отдаём плееру,
                            // чтобы он не «дёргался» после порога.
                            swipeState.dragTo(totalDy)
                        } else {
                            swipeState.dragTo(swipeState.offsetPx + dy)
                        }
                        // Пока жест наш — гасим его в Initial, иначе LazyColumn
                        // начнёт скроллиться параллельно со шторкой.
                        change.consume()
                    }

                    if (dragStarted) finishPlayerDrag()
                } finally {
                    // Отмена жеста не должна оставлять плеер висеть смещённым.
                    if (dragStarted && !swipeState.isDismissed) settlePlayer()
                }
            }
        }
    }

    // Остров управления гасит вертикальные драги: его кнопки и слайдер
    // не должны запускать закрытие (страховка к проверке bounds выше).
    val controlIslandSwipeGuard = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures { change, _ -> change.consume() }
    }

    // Панели (карточка трека и остров управления) при морфе рисуются в оверлее
    // shared-перехода выше обложки. Оверлей не наследует сдвиг и прозрачность
    // свайпа — повторяем их, чтобы панели не прыгали при закрытии жестом.
    val panelModifier = foregroundModifier.then(
        Modifier.graphicsLayer {
            if (isClosing) {
                translationY = swipeState.offsetPx
                alpha = 1f - swipeState.progress
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                swipeState.onSizeChanged(it.width, it.height)
            }
            // Плеер целиком едет за пальцем и тает: сквозь него видна вкладка,
            // которая остаётся скомпонованной под оверлеем (см. AppNavGraph).
            // Значения читаются внутри layer-блока — без рекомпозиции на кадр.
            .graphicsLayer {
                translationY = swipeState.offsetPx
                alpha = 1f - swipeState.progress
            }
            // Чистый фон Material 3: без градиентов от обложки и цветных заливок.
            .background(backgroundBrush)
            // Жест висит на корне и видит касание раньше скроллируемых детей.
            .then(swipeToDismissModifier),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            // contentColorFor(Color.Transparent) возвращает Unspecified, и весь текст
            // внутри плеера наследовал бы его как чёрный. Задаём явно.
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    },
                    title = {
                        // Надпись «Сейчас играет» и артист: имя светится мягким
                        // ореолом цвета обложки, чтобы шапка читалась и на тёмном фоне.
                        Column {
                            Text(
                                text = stringResource(R.string.player_now_playing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = track?.artist ?: "—",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    shadow = Shadow(
                                        color = coverAccent.copy(alpha = 0.55f),
                                        blurRadius = 18f,
                                    ),
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.player_menu_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlist_add_title)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = track != null,
                                    onClick = {
                                        showMenu = false
                                        showAddToPlaylist = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_radio_start)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Radio,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = track != null && !uiState.isRadioLoading,
                                    onClick = {
                                        showMenu = false
                                        track?.let { viewModel.startWaveFromTrack(it) }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_tuning)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            text = "${playerState.tuningHz} Hz",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showTuning = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_variations)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            text = stringResource(
                                                variationLabelRes(playerState.variation),
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showVariations = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sleepTimerEndsAtText?.let { time ->
                                                stringResource(R.string.player_sleep_timer_active, time)
                                            } ?: stringResource(R.string.player_sleep_timer),
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Bedtime,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = track != null,
                                    onClick = {
                                        showMenu = false
                                        showSleepTimer = true
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                    // ─── Hero-зона: обложка-задник + передний план (обложка/тексты) ──
                    ExpressiveHeroZone(
                        variant = PLAYER_LAYOUT_VARIANT,
                        title = track?.title ?: stringResource(R.string.player_no_track),
                        artist = track?.artist ?: "—",
                        source = track?.source,
                        artworkUrl = track?.artworkUrl,
                        isLiked = isLiked,
                        isDisliked = isDisliked,
                        canDownload = canDownload,
                        downloadState = downloadState,
                        isLyricsMode = isLyricsMode,
                        trackKey = track?.id,
                        lyricsResult = uiState.lyrics,
                        isLyricsLoading = uiState.isLyricsLoading,
                        lyricsError = uiState.lyricsError,
                        lyricsListState = lyricsListState,
                        palette = playerColors,
                        actionAccent = coverAccent,
                        actionContainer = heroActionContainer,
                        actionIdleContainer = heroActionIdleContainer,
                        notePainter = notePainter,
                        placeholderPainter = placeholderPainter,
                        currentPositionMs = { viewModel.currentPositionMs() },
                        onNext = { viewModel.playNext() },
                        onPrevious = { viewModel.playPrevious(force = true) },
                        onArtworkBoundsChanged = { artworkBounds = it },
                        isDismissDragging = swipeState.isDragging,
                        onSeekToLine = { timeMs -> viewModel.seekToMs(timeMs) },
                        onRetryLyrics = { viewModel.loadLyricsIfNeeded(track) },
                        onLyricsListBoundsChanged = { lyricsListBounds = it },
                        onToggleLike = { toggleLikeWithPermission() },
                        onToggleDislike = { viewModel.toggleDislike() },
                        onDownloadClick = { viewModel.toggleDownload() },
                        // Подпись артиста под названием ведёт на его экран.
                        // Без трека (плейсхолдер «—») она некликабельна.
                        onArtistClick = track?.let { current -> { onArtistClick(current) } },
                        // Общий shared-element модификатор для морфа обложки
                        // в мини-плеер при закрытии экрана. На время drag его
                        // отключаем: SharedTransitionLayout тяжёлый для 120 Гц,
                        // а обложку в этот момент двигает graphicsLayer.
                        coverModifier = if (swipeState.isDragging) Modifier else coverModifier,
                        // Сжатие и сдвиг обложки прямо во время свайпа.
                        coverSwipeModifier = coverSwipeModifier,
                        // Панель с текстом и кнопками при морфе идёт над обложкой.
                        panelModifier = panelModifier,
                        // Hero занимает всё доступное место: остров управления
                        // всегда прижат к низу, а обложка скроллится при нехватке высоты.
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
    
                    // ─── Подсказка про геопозицию для «Воспоминаний» ─────────────
                    if (!hasLocationPermission) {
                        LocationPermissionBanner(
                            actionLabel = if (permissionRequested) {
                                stringResource(R.string.player_permission_settings)
                            } else {
                                stringResource(R.string.player_permission_allow)
                            },
                            onAction = { onPermissionBannerAction() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 6.dp),
                        )
                    }
    
                    // ─── Сообщение об ошибке (скрывается, как только плеер в STATE_READY) ──
                    val isPlayerReady = playerState.playbackState == androidx.media3.common.Player.STATE_READY
                    val errorMessage = if (isPlayerReady) null else (uiState.error ?: playerState.error)
    
                    if (errorMessage != null) {
                        Text(
                            text = "⚠️ $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
    
                    // ─── Плавающий остров управления ─────────────────────────
                    PlayerControlIsland(
                        isPlaying = playerState.isPlaying,
                        isBuffering = isBuffering,
                        playbackSpeed = playerState.playbackSpeed,
                        containerColor = if (dynamicColorsEnabled) {
                            lerpColor(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                coverAccent,
                                0.14f,
                            )
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        accentColor = coverAccent,
                        onAccentColor = onCoverAccent,
                        borderColor = Color.Transparent,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onPrev = { viewModel.playPrevious() },
                        onNext = { viewModel.playNext() },
                        onSpeedClick = { viewModel.cyclePlaybackSpeed() },
                        onQueueClick = { showQueueSheet = true },
                        isLyricsMode = isLyricsMode,
                        onLyricsClick = {
                            val nextMode = !isLyricsMode
                            isLyricsMode = nextMode
                            if (nextMode) {
                                viewModel.loadLyricsIfNeeded(track)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(panelModifier)
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { controlIslandBounds = it.boundsInRoot() }
                            .padding(top = 8.dp)
                            .then(controlIslandSwipeGuard),
                    ) {
                        PlayerProgressSection(
                            isPlaying = playerState.isPlaying,
                            isBuffering = isBuffering,
                            isResolving = uiState.isResolving,
                            trackId = track?.id,
                            durationMs = track?.durationMs ?: 0L,
                            primaryColor = coverAccent,
                            currentFraction = { viewModel.currentFraction() },
                            currentPositionMs = { viewModel.currentPositionMs() },
                            bufferedFraction = { viewModel.bufferedFraction() },
                            onSeek = { fraction -> viewModel.seekTo(fraction) },
                        )
                    }
                }
        }
    }

    // Шторка закрывается только мгновенным удалением из композиции (snap),
    // без hide-анимации: анимированное скрытие во время выхода из плеера
    // оставляло бы промежуточные кадры «полуплеера» без контролов.
    if (showTuning) {
        TuningDialog(
            selectedHz = playerState.tuningHz,
            onSelect = { hz ->
                viewModel.setTuning(hz)
                showTuning = false
            },
            onDismiss = { showTuning = false },
        )
    }

    if (showVariations) {
        VariationsSheet(
            selected = playerState.variation,
            onSelect = { variation ->
                viewModel.setVariation(variation)
                showVariations = false
            },
            onDismiss = { showVariations = false },
        )
    }

    if (showQueueSheet) {
        PlayerQueueSheet(
            queue = playerState.queue,
            currentIndex = playerState.currentIndex,
            isShuffle = playerState.isShuffle,
            repeatMode = playerState.repeatMode,
            queueOrigin = playerState.queueOrigin,
            onTrackClick = { index -> viewModel.playQueueTrack(index) },
            onRemoveTrack = { index -> viewModel.removeQueueTrack(index) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onDismiss = { showQueueSheet = false },
        )
    }

    // «Добавить в плейлист» из overflow-меню шапки. Шторка общая с поиском
    // и медиатекой; после успеха закрываем её и показываем Snackbar плеера.
    val playlistTrack = track
    if (showAddToPlaylist && playlistTrack != null) {
        AddToPlaylistSheet(
            track = playlistTrack,
            onDismiss = { showAddToPlaylist = false },
            onAdded = { playlistName ->
                showAddToPlaylist = false
                snackbarScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.playlist_added_to, playlistName),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
        )
    }

    // Таймер сна: пресеты и отключение. Шторка живёт вне Scaffold — как
    // и «Добавить в плейлист», она рисуется в отдельном окне.
    if (showSleepTimer) {
        SleepTimerSheet(
            state = sleepTimer,
            onSelectMinutes = { minutes ->
                showSleepTimer = false
                viewModel.startSleepTimer(minutes)
                snackbarScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.sleep_timer_started, minutes),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onCancelTimer = {
                showSleepTimer = false
                viewModel.cancelSleepTimer()
                snackbarScope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.sleep_timer_cancelled),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
            onDismiss = { showSleepTimer = false },
        )
    }
}

/**
 * Ненавязчивая подсказка: без геопозиции лайк сохранится без места,
 * а «Воспоминания» не смогут привязать трек к точке на карте.
 */
@Composable
private fun LocationPermissionBanner(
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_location_off),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.player_location_off_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Снимок визуала трека — цель анимации при переключении. */
private data class TrackDisplay(
    val key: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

/**
 * Hero-зона плеера: обложка, название и артист. Смена трека анимируется
 * мягким fade + slide, тексты и обложка меняются одним блоком.
 */
@Composable
private fun ExpressiveHeroZone(
    variant: Int,
    trackKey: String?,
    title: String,
    artist: String,
    source: Source?,
    artworkUrl: String?,
    isLiked: Boolean,
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    isLyricsMode: Boolean,
    lyricsResult: LyricsResult?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    lyricsListState: LazyListState,
    palette: PlayerDynamicColors,
    // Цвета кнопок под обложкой: акцент обложки/темы, его контейнер и подложка покоя.
    actionAccent: Color,
    actionContainer: Color,
    actionIdleContainer: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onArtworkBoundsChanged: (Rect?) -> Unit,
    isDismissDragging: Boolean = false,
    onSeekToLine: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onLyricsListBoundsChanged: (Rect?) -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
    /** Тап по имени артиста; null — подпись не кликабельна. */
    onArtistClick: (() -> Unit)? = null,
    coverModifier: Modifier = Modifier,
    coverSwipeModifier: Modifier = Modifier,
    panelModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val heroScroll = rememberScrollState()
    val artworkShape = MaterialTheme.shapes.extraLarge
    val display = remember(trackKey, title, artist, artworkUrl) {
        TrackDisplay(trackKey, title, artist, artworkUrl)
    }

    // Переключение трека свайпом по обложке: вверх — следующий, вниз — предыдущий.
    // Жест живёт только на обложке, чтобы не мешать закрытию плеера.
    val haptics = LocalHapticFeedback.current
    val switchThresholdPx = with(LocalDensity.current) { SWIPE_TRACK_THRESHOLD.toPx() }
    var swipeDirection by remember(trackKey) { mutableIntStateOf(0) }
    var swipeDistancePx by remember(trackKey) { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = isLyricsMode,
            modifier = Modifier
                .then(panelModifier)
                .then(if (isLyricsMode) Modifier.fillMaxSize() else Modifier),
            transitionSpec = {
                fadeIn(tween(durationMillis = 220)) togetherWith
                    fadeOut(tween(durationMillis = 160))
            },
            label = "playerModeContent",
        ) { lyricsMode ->
            if (lyricsMode) {
                LyricsForeground(
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    lyricsResult = lyricsResult,
                    isLoading = isLyricsLoading,
                    error = lyricsError,
                    listState = lyricsListState,
                    highlightColor = MaterialTheme.colorScheme.primary,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                    currentPositionMs = currentPositionMs,
                    onSeekToLine = onSeekToLine,
                    onRetry = onRetryLyrics,
                    onListBoundsChanged = onLyricsListBoundsChanged,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(heroScroll)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(
                        targetState = display,
                        transitionSpec = {
                            ((fadeIn(tween(durationMillis = 340)) +
                                slideInVertically(tween(durationMillis = 340)) { it / 12 }) togetherWith
                                (fadeOut(tween(durationMillis = 220)) +
                                    slideOutVertically(tween(durationMillis = 220)) { -it / 12 }))
                                .using(SizeTransform(clip = false))
                        },
                        contentAlignment = Alignment.TopCenter,
                        label = "trackSwitch",
                    ) { state ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .aspectRatio(1f)
                                    .then(coverSwipeModifier)
                                    .onGloballyPositioned {
                                        // Во время закрытия обложка едет/сжимается:
                                        // не дёргаем границы на каждый кадр.
                                        if (!isDismissDragging) {
                                            onArtworkBoundsChanged(it.boundsInRoot())
                                        }
                                    }
                                    .pointerInput(trackKey) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                swipeDistancePx = 0f
                                                swipeDirection = 0
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeDistancePx += dragAmount
                                                val direction = when {
                                                    swipeDistancePx <= -switchThresholdPx -> -1
                                                    swipeDistancePx >= switchThresholdPx -> 1
                                                    else -> 0
                                                }
                                                if (direction != swipeDirection) {
                                                    swipeDirection = direction
                                                    if (direction != 0) {
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove,
                                                        )
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                when {
                                                    swipeDirection < 0 -> onNext()
                                                    swipeDirection > 0 -> onPrevious()
                                                }
                                                swipeDirection = 0
                                                swipeDistancePx = 0f
                                            },
                                            onDragCancel = {
                                                swipeDirection = 0
                                                swipeDistancePx = 0f
                                            },
                                        )
                                    },
                            ) {
                                DisposableEffect(trackKey) {
                                    onDispose { onArtworkBoundsChanged(null) }
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Морф в мини-плеер — только на актуальном треке:
                                        // уходящий кадр не должен дублировать shared-element.
                                        .then(if (state.key == trackKey) coverModifier else Modifier),
                                    shape = artworkShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 0.dp,
                                ) {
                                    ArtworkImage(
                                        artworkUrl = state.artworkUrl,
                                        shape = artworkShape,
                                        notePainter = notePainter,
                                        placeholderPainter = placeholderPainter,
                                    )
                                }
                                // HUD переключения: какой трек сейчас будет запущен.
                                Crossfade(
                                    targetState = swipeDirection,
                                    modifier = Modifier.align(Alignment.Center),
                                    animationSpec = tween(140),
                                    label = "trackSwipeHud",
                                ) { direction ->
                                    if (direction != 0) {
                                        Surface(
                                            shape = MaterialTheme.shapes.large,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                .copy(alpha = 0.94f),
                                            tonalElevation = 4.dp,
                                            shadowElevation = 10.dp,
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 18.dp,
                                                    vertical = 12.dp,
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = if (direction < 0) {
                                                        Icons.Filled.SkipNext
                                                    } else {
                                                        Icons.Filled.SkipPrevious
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(
                                                        if (direction < 0) {
                                                            R.string.player_swipe_next
                                                        } else {
                                                            R.string.player_swipe_previous
                                                        },
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(4.dp))
                            // Имя артиста — точка входа на его экран.
                            val artistClick = onArtistClick
                            val artistModifier = if (artistClick != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { artistClick() }
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            Text(
                                text = state.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = artistModifier,
                            )
                            if (source != null && !source.isMetadataOnly) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.player_source, sourceLabel(source)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canDownload) {
                            DownloadSquircle(
                                state = downloadState,
                                primaryColor = actionAccent,
                                primaryContainerColor = actionContainer,
                                idleContainerColor = actionIdleContainer,
                                onClick = onDownloadClick,
                            )
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        LikeSquircle(
                            isLiked = isLiked,
                            primaryColor = actionAccent,
                            primaryContainerColor = actionContainer,
                            idleContainerColor = actionIdleContainer,
                            onToggleLike = onToggleLike,
                        )
                        DislikeSquircle(
                            isDisliked = isDisliked,
                            idleContainerColor = actionIdleContainer,
                            onToggleDislike = onToggleDislike,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Передний план режима обложки: прозрачный «слот» под обложку (сама обложка — в заднем слое),
 * под ним — стеклянная карточка с Display-названием и артистом.
 */
@Composable
private fun CoverForeground(
    style: HeroStyle,
    heroScroll: ScrollState,
    title: String,
    artist: String,
    isLiked: Boolean,
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(heroScroll)
            .padding(horizontal = 24.dp),
        horizontalAlignment = if (style.alignment == Alignment.TopEnd) Alignment.End else Alignment.Start,
    ) {
        Spacer(Modifier.height(style.topPadding))

        // Слот под обложку: точно совпадает с задним слоем
        Spacer(
            modifier = Modifier
                .fillMaxWidth(style.widthFraction)
                .aspectRatio(1f),
        )

        if (style.titleOnArtwork) {
            // Вариант 3: название уже на обложке — снизу только артист и лайк
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Слот скачивания сохраняет ширину даже для локального трека:
                // иначе лайк и дизлайк прыгают между разными композициями.
                if (canDownload) {
                    DownloadSquircle(
                        state = downloadState,
                        primaryColor = primaryColor,
                        primaryContainerColor = primaryContainerColor,
                        onClick = onDownloadClick,
                    )
                } else {
                    Spacer(Modifier.size(46.dp))
                }
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
                Spacer(Modifier.width(8.dp))
                DislikeSquircle(
                    isDisliked = isDisliked,
                    onToggleDislike = onToggleDislike,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            TrackGlassCard(
                title = title,
                artist = artist,
                isLiked = isLiked,
                isDisliked = isDisliked,
                canDownload = canDownload,
                downloadState = downloadState,
                primaryColor = primaryColor,
                primaryContainerColor = primaryContainerColor,
                surfaceColor = surfaceColor,
                onToggleLike = onToggleLike,
                onToggleDislike = onToggleDislike,
                onDownloadClick = onDownloadClick,
                modifier = Modifier.offset(y = -style.overlap),
            )
        }

        // Гарантированный воздух между карточкой и островом управления
        Spacer(Modifier.height(24.dp))
    }
}

/** Матово-стеклянная карточка трека с огромной Display-типографикой. */
@Composable
private fun TrackGlassCard(
    title: String,
    artist: String,
    isLiked: Boolean,
    isDisliked: Boolean,
    canDownload: Boolean,
    downloadState: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    surfaceColor: Color,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(26.dp)) {
            // Крупная, но спокойная типографика: 30sp SemiBold вместо 40sp ExtraBold
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                // Явный цвет: contentColorFor полупрозрачной «стеклянной» карточки
                // давал чёрный текст на тёмном фоне при светлых обложках.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Акцентная полоска-градиент под названием
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(primaryColor, primaryColor.copy(alpha = 0f)),
                            ),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // Резерв под кнопку скачивания: без него ряд действий «прыгает»
                // у локальных треков, которые уже лежат на диске.
                if (canDownload) {
                    DownloadSquircle(
                        state = downloadState,
                        primaryColor = primaryColor,
                        primaryContainerColor = primaryContainerColor,
                        onClick = onDownloadClick,
                    )
                } else {
                    Spacer(Modifier.size(46.dp))
                }
                Spacer(Modifier.width(8.dp))
                LikeSquircle(
                    isLiked = isLiked,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    onToggleLike = onToggleLike,
                )
                Spacer(Modifier.width(8.dp))
                DislikeSquircle(
                    isDisliked = isDisliked,
                    onToggleDislike = onToggleDislike,
                )
            }
        }
    }
}

/** Визуальные состояния кнопки: прогресс не перезапускает анимацию иконки. */
private enum class DownloadButtonKind {
    Download,
    Progress,
    Done,
    Error,
}

private fun DownloadState.toButtonKind(): DownloadButtonKind = when (this) {
    is DownloadState.Downloaded -> DownloadButtonKind.Done
    is DownloadState.InProgress -> DownloadButtonKind.Progress
    is DownloadState.Error -> DownloadButtonKind.Error
    DownloadState.NotDownloaded -> DownloadButtonKind.Download
}

/**
 * Кнопка скачивания 46dp: стрелка → прогресс → галочка,
 * при отмене/ошибке — крестик, который через секунду морфится обратно.
 */
@Composable
private fun DownloadSquircle(
    state: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onClick: () -> Unit,
) {
    val kind = state.toButtonKind()
    val progress = (state as? DownloadState.InProgress)?.progress ?: 0f
    val contentColor = when (kind) {
        DownloadButtonKind.Done -> primaryColor
        DownloadButtonKind.Error -> MaterialTheme.colorScheme.error
        DownloadButtonKind.Download, DownloadButtonKind.Progress ->
            MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when (kind) {
        DownloadButtonKind.Done -> primaryContainerColor
        DownloadButtonKind.Error ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        DownloadButtonKind.Download, DownloadButtonKind.Progress -> idleContainerColor
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
            },
            label = "downloadIcon",
        ) { target ->
            when (target) {
                DownloadButtonKind.Download -> Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.player_download_cd),
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Progress -> if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                    )
                }

                DownloadButtonKind.Done -> Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = stringResource(R.string.player_download_done_cd),
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Error -> Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.player_download_cancelled_cd),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Лайк-сквиркл 46dp с тональным контейнером в цветах плеера. */
@Composable
private fun LikeSquircle(
    isLiked: Boolean,
    primaryColor: Color,
    primaryContainerColor: Color,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onToggleLike: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onToggleLike,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isLiked) primaryContainerColor else idleContainerColor,
            contentColor = if (isLiked) primaryColor else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.player_like_cd),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Дизлайк-сквиркл 46dp: красный, пока трек исключён из рекомендаций. */
@Composable
private fun DislikeSquircle(
    isDisliked: Boolean,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onToggleDislike: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    FilledTonalIconButton(
        onClick = onToggleDislike,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isDisliked) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                idleContainerColor
            },
            contentColor = if (isDisliked) error else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.ThumbDown,
            contentDescription = if (isDisliked) {
                stringResource(R.string.player_undislike_cd)
            } else {
                stringResource(R.string.player_dislike_cd)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Чистый экран текстов поверх «утопленной» размытой обложки:
 * без стеклянных панелей — только парящая шапка трека и строки, растворяющиеся в фоне.
 */
@Composable
private fun LyricsForeground(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    highlightColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
    onListBoundsChanged: (Rect?) -> Unit,
) {
    val miniShape = MaterialTheme.shapes.medium
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Парящая шапка: миниатюра и подпись без подложки
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(miniShape),
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = miniShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.44f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Строки скользят под мягкими градиентными масками, не упираясь в контролы.
        // Маски — это DstIn-затухание самого контента, а не серая плёнка surface:
        // на светлой обложке цветная подложка давала видимый прямоугольник.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LyricsListView(
                lyricsResult = lyricsResult,
                isLoading = isLoading,
                error = error,
                listState = listState,
                highlightColor = highlightColor,
                currentPositionMs = currentPositionMs,
                onSeekToLine = onSeekToLine,
                onRetry = onRetry,
                onListBoundsChanged = onListBoundsChanged,
            )
        }

        // Воздух до острова управления: тексты не налезают на язычок и волну
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ArtworkImage(
    artworkUrl: String?,
    shape: Shape,
    notePainter: Painter,
    placeholderPainter: Painter,
) {
    val context = LocalContext.current
    if (!artworkUrl.isNullOrBlank()) {
        // Сначала пробуем увеличенную обложку, при ошибке — исходную.
        var useOriginal by remember(artworkUrl) { mutableStateOf(false) }
        val targetUrl = if (useOriginal) {
            artworkUrl
        } else {
            com.facet.music.core.ui.artwork.ArtworkUrl.highRes(artworkUrl) ?: artworkUrl
        }
        val imageRequest = remember(targetUrl) {
            ImageRequest.Builder(context)
                .data(targetUrl)
                .size(1024, 1024)
                .crossfade(240)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = stringResource(R.string.player_cover_cd),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = notePainter,
            // maxres/500×500 есть не у всех треков: откатываемся на исходный URL.
            onError = { useOriginal = true },
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = notePainter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

/**
 * Список строк текста без панелей и подложек:
 * - крупная базовая строка, активная выделяется масштабом, весом и свечением;
 * - дальние строки приглушаются и мягко размываются;
 * - сверху и снизу строки растворяются в градиентных масках;
 * - автоцентрирование активной строки по вертикали (как в Apple Music);
 * - тап = перемотка.
 */
@Composable
private fun LyricsListView(
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    highlightColor: Color,
    currentPositionMs: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
    onListBoundsChanged: (Rect?) -> Unit,
) {
    var activeIndex by remember { mutableIntStateOf(-1) }
    var activeProgress by remember { mutableFloatStateOf(0f) }
    var hasCentered by remember { mutableStateOf(false) }

    // Высота видимой области — для центрирующего сдвига автоскролла
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val halfLinePx = with(density) { 22.dp.roundToPx() }
    // Активная строка стоит чуть выше геометрического центра: так перенесённая
    // строка не попадает под нижнюю градиентную маску у контролов.
    val centerLiftPx = with(density) { 24.dp.roundToPx() }

    // Опрос позиции: индекс активной строки и прогресс внутри неё
    LaunchedEffect(lyricsResult) {
        activeIndex = -1
        activeProgress = 0f
        hasCentered = false
        while (true) {
            val lines = lyricsResult?.lines
            if (lyricsResult?.isSynced == true && !lines.isNullOrEmpty()) {
                val curTime = currentPositionMs()
                val idx = lines.indexOfLast { it.timeMs <= curTime }
                if (idx != activeIndex) activeIndex = idx
                activeProgress = if (idx in lines.indices) {
                    val startMs = lines[idx].timeMs.coerceAtLeast(0L)
                    val endMs = if (idx + 1 < lines.size) lines[idx + 1].timeMs else startMs + 5000L
                    ((curTime - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L))
                        .coerceIn(0f, 1f)
                } else {
                    0f
                }
            } else {
                activeProgress = 0f
            }
            delay(150)
        }
    }

    // Скролл — реакция только на смену индекса; строка встаёт по центру видимой области
    LaunchedEffect(activeIndex, viewportHeightPx) {
        if (activeIndex < 0 || viewportHeightPx <= 0) return@LaunchedEffect
        val centerOffset = (viewportHeightPx / 2 - halfLinePx - centerLiftPx).coerceAtLeast(0)
        if (!hasCentered) {
            // Первое позиционирование — мгновенно, без «пролёта» через весь текст
            listState.scrollToItem(index = activeIndex, scrollOffset = -centerOffset)
            hasCentered = true
        } else {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -centerOffset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Мягкое растворение строк у верхнего и нижнего краёв: маска режет
            // прозрачность самого контента, поэтому фон под ним остаётся чистым.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.07f to Color.Black,
                        0.87f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), color = highlightColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.player_lyrics_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lyricsResult?.isInstrumental == true -> {
                Text(
                    stringResource(R.string.player_lyrics_instrumental),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyricsResult != null && lyricsResult.lines.isNotEmpty() -> {
                // Список ушёл из композиции (загрузка/ошибка/смена режима) —
                // сбрасываем его границы, иначе жест продолжит считать их живыми.
                DisposableEffect(Unit) {
                    onDispose { onListBoundsChanged(null) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height }
                        // Корневой жест сверяет точку касания с этой областью:
                        // только внутри неё вертикаль принадлежит списку.
                        .onGloballyPositioned { onListBoundsChanged(it.boundsInRoot()) },
                    // Воздух до шапки и до нижней градиентной маски над контролами
                    contentPadding = PaddingValues(
                        top = if (lyricsResult.isSynced) 64.dp else 28.dp,
                        bottom = 136.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    // Modifier.animateItem() здесь сознательно не используется:
                    // список не добавляет и не удаляет строки, а на каждый
                    // animateScrollToItem (смена активной строки) аниматор
                    // появления гасит уже видимые строки в ноль и проявляет их
                    // заново ~250 мс — текст «исчезал» и возвращался.
                    itemsIndexed(
                        items = lyricsResult.lines,
                        key = { index, line -> "$index-${line.timeMs}" },
                    ) { index, line ->
                        LyricLineItem(
                            text = line.text.ifBlank { "♪" },
                            isSynced = lyricsResult.isSynced,
                            isActive = lyricsResult.isSynced && index == activeIndex,
                            indexDistance = if (lyricsResult.isSynced && activeIndex >= 0) {
                                kotlin.math.abs(index - activeIndex)
                            } else {
                                0
                            },
                            lineProgress = { activeProgress },
                            highlightColor = highlightColor,
                            onClick = if (lyricsResult.isSynced && line.timeMs >= 0) {
                                { onSeekToLine(line.timeMs) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error ?: stringResource(R.string.player_lyrics_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.player_lyrics_retry))
                    }
                }
            }
        }
    }
}

/**
 * Одна строка синхронного текста:
 * - размер, цвет и прозрачность анимируются — строка «вырастает» и гаснет плавно;
 * - полоска прогресса живёт в постоянном слоте высотой 12dp и проявляется альфой:
 *   раньше она въезжала через expandVertically, из-за чего у строки менялась высота,
 *   LazyColumn пересчитывал смещения и список дёргался на каждой смене строки;
 * - альфа читается в graphicsLayer, поэтому анимация не дёргает весь список.
 */
@Composable
private fun LyricLineItem(
    text: String,
    isSynced: Boolean,
    isActive: Boolean,
    indexDistance: Int,
    lineProgress: () -> Float,
    highlightColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Прогресс читает только активная строка — остальные не подписаны на опрос плеера
    val fillTarget = if (isActive && isSynced) lineProgress().coerceIn(0f, 1f) else 0f
    val fill by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 240, easing = LinearEasing),
        label = "lyricFill",
    )

    // Размер строки анимируется: активная плавно вырастает до 22sp,
    // соседние подтягиваются к 17sp, дальние остаются компактными.
    val targetSize = when {
        !isSynced -> 18f
        isActive -> 22f
        indexDistance == 1 -> 17f
        else -> 15f
    }
    val animatedSize by animateFloatAsState(
        targetValue = targetSize,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "lyricSize",
    )

    val lineAlpha = animateFloatAsState(
        targetValue = when {
            !isSynced -> 0.85f
            isActive -> 1f
            indexDistance == 1 -> 0.72f
            else -> 0.42f
        },
        animationSpec = tween(durationMillis = 300),
        label = "lyricAlpha",
    )

    // Полоска не занимает и не освобождает место — она просто проявляется
    // и гаснет внутри своего постоянного слота.
    val barAlpha = animateFloatAsState(
        targetValue = if (isActive && isSynced) 1f else 0f,
        animationSpec = tween(durationMillis = if (isActive) 200 else 140),
        label = "lyricBar",
    )

    val targetColor = when {
        isActive -> onSurface
        isSynced -> onSurfaceVariant
        else -> onSurface
    }
    val lineColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 280),
        label = "lyricColor",
    )

    val lineStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = animatedSize.sp,
        lineHeight = (animatedSize * 1.36f).sp,
        letterSpacing = 0.sp,
        fontWeight = when {
            isActive -> FontWeight.SemiBold
            indexDistance == 1 -> FontWeight.Medium
            else -> FontWeight.Normal
        },
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = lineStyle,
            color = lineColor,
            textAlign = TextAlign.Center,
            // Без жёстких ограничений: длинные строки свободно переносятся
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                // Альфа — в фазе отрисовки, без рекомпозиции на каждый кадр.
                .graphicsLayer { alpha = lineAlpha.value },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LyricBarSlot)
                .graphicsLayer { alpha = barAlpha.value },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .background(highlightColor),
                )
            }
        }
    }
}

/**
 * Изолированная секция прогресса для «острова»:
 * таймлайн Material3 Slider + таймкоды.
 * Оставшееся время справа отображается с минусом ("-m:ss").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgressSection(
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    isResolving: Boolean = false,
    trackId: String? = null,
    durationMs: Long,
    primaryColor: Color,
    currentFraction: () -> Float,
    currentPositionMs: () -> Long,
    bufferedFraction: () -> Float = { 0f },
    onSeek: (Float) -> Unit,
) {
    var sliderState by remember(trackId) { mutableStateOf(PlayerSliderState()) }
    // Новый трек стартует с нуля: старые позиция и буфер не должны переезжать
    // в следующий трек — иначе не срабатывает морф индикатора загрузки.
    var playbackFraction by remember(trackId) { mutableFloatStateOf(0f) }
    var playbackPositionMs by remember(trackId) { mutableLongStateOf(0L) }
    var bufferFrac by remember(trackId) { mutableFloatStateOf(0f) }
    val latestTrackId by rememberUpdatedState(trackId)
    val latestFraction by rememberUpdatedState(currentFraction)
    val latestPosition by rememberUpdatedState(currentPositionMs)
    val latestBuffer by rememberUpdatedState(bufferedFraction)
    val latestSeek by rememberUpdatedState(onSeek)

    LaunchedEffect(trackId) {
        while (true) {
            // Playback продолжает обновляться даже во время preview, чтобы Cancel был актуальным.
            playbackFraction = latestFraction()
            playbackPositionMs = latestPosition()
            bufferFrac = latestBuffer()
            delay(150)
        }
    }

    val displayPosition = sliderState.display(playbackFraction)
    val displayedMs = sliderState.preview?.let { (it * durationMs).toLong() } ?: playbackPositionMs

    val remainingMs = (durationMs - displayedMs).coerceAtLeast(0L)
    // Индикатор загрузки живёт только на старте трека и не возвращается назад:
    // пауза, перемотка в начало и повторная буферизация не перезапускают морф.
    val isInitialBuffering = (isBuffering || isResolving) && displayedMs == 0L
    var hasStarted by remember(trackId) { mutableStateOf(false) }
    LaunchedEffect(trackId, isInitialBuffering) {
        if (!isInitialBuffering) hasStarted = true
    }
    val showLoading = isInitialBuffering && !hasStarted

    Column(modifier = Modifier.fillMaxWidth()) {
        Crossfade(
            targetState = showLoading,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "progressCrossfade",
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Высота совпадает со слайдером — лейаут не прыгает при Crossfade
                        .height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    )
                }
            } else {
                key(trackId) {
                    val gestureTrack = trackId
                    ExpressivePlayerSlider(
                        value = displayPosition.coerceIn(0f, 1f),
                        onValueChange = { value ->
                            if (latestTrackId == gestureTrack) sliderState = sliderState.change(value)
                        },
                        onDragInteraction = { interaction ->
                            if (latestTrackId == gestureTrack) {
                                sliderState = when (interaction) {
                                    is DragInteraction.Start -> sliderState.start()
                                    is DragInteraction.Stop -> sliderState.stop()
                                    is DragInteraction.Cancel -> {
                                        playbackFraction = latestFraction()
                                        playbackPositionMs = latestPosition()
                                        sliderState.cancel()
                                    }
                                    else -> sliderState
                                }
                            }
                        },
                        onValueChangeFinished = {
                            // Cancel уже обработан тем же interactionSource и убрал preview.
                            if (latestTrackId == gestureTrack) {
                                sliderState.preview?.let { latestSeek(it) }
                                sliderState = sliderState.cancel()
                                playbackFraction = latestFraction()
                                playbackPositionMs = latestPosition()
                            }
                        },
                        activeColor = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        thumbColor = MaterialTheme.colorScheme.onSurface,
                        bufferedFraction = bufferFrac,
                        enabled = durationMs > 0,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatMs(displayedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatMs(remainingMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Градиент караоке для всей строки:
 * слева — пропетая часть в акценте, справа — непропетая, между ними мягкая кромка.
 * Без явных start/end градиент растягивается по границам самой строки.
 */
private fun buildLyricsBrush(
    fillFraction: Float,
    sungColor: Color,
    unsungColor: Color,
): Brush {
    val fill = fillFraction.coerceIn(0f, 1f)
    if (fill <= 0f) {
        return SolidColor(unsungColor)
    }
    if (fill >= 1f) {
        return SolidColor(sungColor)
    }

    // Полуширина мягкой кромки в долях ширины строки
    val edge = 0.05f
    val edgeStart = (fill - edge).coerceAtLeast(0f)
    val edgeEnd = (fill + edge).coerceAtMost(1f)

    val stops = buildList {
        if (edgeStart > 0f) add(0f to sungColor)
        add(edgeStart to sungColor)
        add(edgeEnd to unsungColor)
        if (edgeEnd < 1f) add(1f to unsungColor)
    }
    // Горизонтальная заливка: перенесённая строка не становится серой справа.
    return Brush.horizontalGradient(colorStops = stops.toTypedArray())
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

/**
 * Смешивает палитру обложки с собственным акцентом пользователя (40% акцента):
 * цвета плеера остаются «живыми» от обложки, но не конфликтуют с выбранной темой.
 * Прозрачные роли сохраняют свою альфу — смещается только оттенок.
 * Контрастные роли не смешиваются, а пересчитываются от светлоты результата:
 * lerp к акценту уводил их в цвет контейнера и ронял контраст текста.
 */
private fun blendPaletteWithAccent(
    palette: PlayerDynamicColors,
    accent: Color,
): PlayerDynamicColors {
    fun tinted(color: Color): Color =
        lerpColor(color, accent.copy(alpha = color.alpha), 0.4f)

    val primary = lerpColor(palette.primary, accent, 0.4f)
    val onTint = if (primary.luminance() > 0.5f) Color.Black else Color.White

    return palette.copy(
        dominant = lerpColor(palette.dominant, accent, 0.4f),
        backgroundTop = tinted(palette.backgroundTop),
        backgroundBottom = tinted(palette.backgroundBottom),
        primary = primary,
        onPrimary = onTint,
        primaryContainer = tinted(palette.primaryContainer),
        onPrimaryContainer = onTint,
        secondaryContainer = tinted(palette.secondaryContainer),
        onSecondaryContainer = onTint,
    )
}

/** Название источника для строки «Источник» под артистом. */
private fun sourceLabel(source: Source): String = when (source) {
    Source.SOUNDCLOUD -> "SoundCloud"
    Source.YOUTUBE -> "YouTube"
    Source.SPOTIFY -> "Spotify"
    Source.LOCAL -> "Local"
    else -> source.name
}

/** Строковый ресурс названия вариации. */
private fun variationLabelRes(variation: PlaybackVariation): Int = when (variation) {
    PlaybackVariation.NORMAL -> R.string.variation_normal
    PlaybackVariation.SPEED_UP -> R.string.variation_speed_up
    PlaybackVariation.SLOWED -> R.string.variation_slowed
    PlaybackVariation.NIGHTCORE -> R.string.variation_nightcore
}

/**
 * Детюн: выбор строя. 440 Гц — стандарт, остальные сдвигают питч
 * через PlaybackParameters, скорость не меняется.
 */
@Composable
private fun TuningDialog(
    selectedHz: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tuning_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tuning_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PlaybackTuning.FREQUENCIES.forEach { hz ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hz) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = hz == selectedHz,
                            onClick = { onSelect(hz) },
                        )
                        Text(
                            text = if (hz == PlaybackTuning.REFERENCE_HZ.toInt()) {
                                stringResource(R.string.tuning_standard, hz)
                            } else {
                                "$hz Hz"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

/** Вариации трека: скорость и питч одной настройкой. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VariationsSheet(
    selected: PlaybackVariation,
    onSelect: (PlaybackVariation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.variations_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            PlaybackVariation.entries.forEach { variation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(variation) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = variation == selected,
                        onClick = { onSelect(variation) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(variationLabelRes(variation)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "×${variation.speed} · pitch ${variation.pitch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
