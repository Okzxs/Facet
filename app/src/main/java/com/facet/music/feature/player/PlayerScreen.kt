package com.facet.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.rounded.PersonOff
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.core.ui.theme.bestContentColor
import com.facet.music.core.ui.theme.contrastContentColor
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

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
        if (track != null && uiState.lyricsData == null && !uiState.isLyricsLoading) {
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
    val onCoverAccent = contrastContentColor(coverAccent)
    val themeBackground = MaterialTheme.colorScheme.background

    // Единый источник позиции: кадры идут только пока трек играет (9.12б).
    val playbackClock = rememberPlaybackClock(
        positionMs = { viewModel.currentPositionMs() },
        isPlaying = playerState.isPlaying,
        playbackSpeed = playerState.playbackSpeed * playerState.variation.speed,
    )

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
    val controlIslandBounds = remember { BoundsHolder() }
    // Границы обложки: свайп по ней переключает трек, а не закрывает плеер.
    val artworkBounds = remember { BoundsHolder() }
    // Границы самого LazyColumn с текстом: только по нему решаем,
    // скроллить текст или тянуть плеер (шапка и пустые зоны — всегда плеер).
    val lyricsListBounds = remember { BoundsHolder() }
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

    fun settlePlayer(velocity: Float = 0f) {
        swipeState.settle(velocity)
    }

    fun dismissPlayer() {
        if (swipeState.isDismissed) return
        swipeState.dismiss()
        onBack()
    }

    /** Отпускание пальца: выше порога (30%) или резкий вниз (>1200 px/s) — закрыть, иначе вернуть пружиной. */
    fun finishPlayerDrag(velocityY: Float = 0f) {
        val height = swipeState.screenHeightPx
        val threshold = height * DISMISS_THRESHOLD
        val fastDown = velocityY > CLOSE_VELOCITY_PX_S
        if (height > 0f && (swipeState.offsetPx > threshold || fastDown)) {
            dismissPlayer()
        } else {
            settlePlayer(velocityY)
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
                val island = controlIslandBounds.value
                if (island != null && island.contains(down.position)) {
                    return@awaitEachGesture
                }
                // Обложка отдана свайпу-переключению трека.
                val artwork = artworkBounds.value
                if (artwork != null && artwork.contains(down.position)) {
                    return@awaitEachGesture
                }

                var dragStarted = false
                var totalDy = 0f
                val velocityTracker = VelocityTracker()

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
                        if (dragStarted) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                        }

                        if (!dragStarted) {
                            if (abs(totalDy) < viewConfiguration.touchSlop) continue
                            // Вертикаль принадлежит списку только если касание
                            // началось внутри LazyColumn и текст уже проскроллен:
                            // тогда вниз листаем текст, а не тянем шторку.
                            // Шапка, обложка и пустые зоны всегда двигают плеер.
                            val inLyricsList =
                                lyricsListBounds.value?.contains(down.position) == true
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

                    if (dragStarted) {
                        finishPlayerDrag(velocityTracker.calculateVelocity().y)
                    }
                } finally {
                    // Отмена жеста не должна оставлять плеер висеть смещённым.
                    if (dragStarted && !swipeState.isDismissed) settlePlayer()
                }
            }
        }
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
            // ModulateAlpha: альфа не рендерит весь экран во внеэкранный буфер.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                translationY = swipeState.offsetPx
                val progress = swipeState.progress
                alpha = 1f - progress
                val scale = 1f - 0.06f * progress
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
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
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.player_block_artist)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonOff,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = track != null,
                                    onClick = {
                                        showMenu = false
                                        viewModel.blockCurrentArtist()
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
                        lyricsData = uiState.lyricsData,
                        isLyricsLoading = uiState.isLyricsLoading,
                        lyricsError = uiState.lyricsError?.let { stringResource(it.messageRes) },
                        lyricsListState = lyricsListState,
                        palette = playerColors,
                        actionAccent = coverAccent,
                        actionContainer = heroActionContainer,
                        actionIdleContainer = heroActionIdleContainer,
                        notePainter = notePainter,
                        placeholderPainter = placeholderPainter,
                        isPlaying = playerState.isPlaying,
                        playbackClock = playbackClock,
                        onNext = { viewModel.playNext() },
                        onPrevious = { viewModel.playPrevious(force = true) },
                        previousArtworkUrl = playerState.queue.getOrNull(playerState.currentIndex - 1)?.artworkUrl,
                        nextArtworkUrl = playerState.queue.getOrNull(playerState.currentIndex + 1)?.artworkUrl,
                        onArtworkBoundsChanged = { artworkBounds.value = it },
                        isDismissDragging = swipeState.isDragging,
                        onSeekToLine = { timeMs -> viewModel.seekToMs(timeMs) },
                        onRetryLyrics = { viewModel.loadLyricsIfNeeded(track) },
                        onLyricsListBoundsChanged = { lyricsListBounds.value = it },
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
                    val error = if (isPlayerReady) null else (uiState.error ?: playerState.error)

                    if (error != null) {
                        Text(
                            text = "⚠️ ${stringResource(error.messageRes)}",
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
                        onPrev = { viewModel.playPrevious(force = true) },
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
                            .onGloballyPositioned { controlIslandBounds.value = it.boundsInRoot() }
                            .padding(top = 8.dp),
                    ) {
                        PlayerProgressSection(
                            isPlaying = playerState.isPlaying,
                            playbackClock = playbackClock,
                            positionMs = { viewModel.currentPositionMs() },
                            trackId = track?.id,
                            durationMs = track?.durationMs ?: 0L,
                            primaryColor = coverAccent,
                            isBuffering = isBuffering,
                            isResolving = uiState.isResolving,
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
internal data class TrackDisplay(
    val key: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

private fun blendPaletteWithAccent(
    palette: PlayerDynamicColors,
    accent: Color,
): PlayerDynamicColors {
    fun tinted(color: Color): Color =
        lerpColor(color, accent.copy(alpha = color.alpha), 0.4f)

    val primary = lerpColor(palette.primary, accent, 0.4f)
    val (accessiblePrimary, onTint) = bestContentColor(primary)

    return palette.copy(
        dominant = lerpColor(palette.dominant, accent, 0.4f),
        backgroundTop = tinted(palette.backgroundTop),
        backgroundBottom = tinted(palette.backgroundBottom),
        primary = accessiblePrimary,
        onPrimary = onTint,
        primaryContainer = tinted(palette.primaryContainer),
        onPrimaryContainer = onTint,
        secondaryContainer = tinted(palette.secondaryContainer),
        onSecondaryContainer = onTint,
    )
}

