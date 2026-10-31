package com.facet.music.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.ui.components.formatDuration
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.core.util.SeasonProvider
import com.facet.music.data.model.SeasonMemory
import com.facet.music.data.model.Track
import com.facet.music.data.source.external.DeezerRelease
import com.facet.music.navigation.LocalBottomDockHeight
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ScreenPadding = 16.dp
private val BlockGap = 32.dp
private const val MEMORY_PREVIEW_LIMIT = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onSettingsClick: () -> Unit,
    onWaveClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentTracks = uiState.recentTracks
    val heroTrack = recentTracks.firstOrNull()
    val homeListState = rememberLazyListState()
    val recommendationsListState = rememberLazyListState()

    // Новинка — альбом: треки приходят из ViewModel, открываем плеер здесь.
    LaunchedEffect(Unit) {
        viewModel.openPlayer.collect { (track, queue) -> onTrackClick(track, queue) }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        // Статус-бар раньше обеспечивал корневой Scaffold; теперь отступ ставит сам экран.
        // Фон — тоже: без него экран прозрачный и просвечивает окно приложения.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 20.dp,
                // Запас снизу под плавающий док (его больше нет в слоте Scaffold).
                bottom = 24.dp + LocalBottomDockHeight.current,
            ),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            item(key = "header") {
                RevealBlock(index = 0) {
                    HomeHeader(onSettingsClick = onSettingsClick)
                }
            }

            if (heroTrack != null) {
                item(key = "hero") {
                    RevealBlock(index = 1) {
                        ContinueListeningCard(
                            track = heroTrack,
                            queue = recentTracks,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            // Волна: вход в бесконечный поток.
            item(key = "wave_promo") {
                RevealBlock(index = 2) {
                    WavePromoCard(onClick = onWaveClick)
                }
            }

            // Новинки и чарты: пока грузятся — скелетоны, а не пустота.
            if (uiState.isExtrasLoading) {
                item(key = "extras_skeleton") {
                    ExtrasSkeleton()
                }
            } else {
                if (uiState.newReleases.isNotEmpty()) {
                    item(key = "new_releases") {
                        NewReleasesBlock(
                            releases = uiState.newReleases,
                            onClick = viewModel::playRelease,
                        )
                    }
                }
                if (uiState.charts.isNotEmpty()) {
                    item(key = "charts") {
                        ChartsBlock(
                            tracks = uiState.charts,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            if (uiState.genreSections.isNotEmpty()) {
                item(key = "genre_playlists") {
                    GenrePlaylistsBlock(
                        sections = uiState.genreSections,
                        onTrackClick = onTrackClick,
                    )
                }
            }

            item(key = "recommendations") {                RevealBlock(index = 2) {
                    RecommendationsBlock(
                        tracks = uiState.recommendations,
                        isLoading = uiState.isRecommendationsLoading,
                        error = uiState.recommendationsError,
                        listState = recommendationsListState,
                        onTrackClick = onTrackClick,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            if (recentTracks.isNotEmpty()) {
                item(key = "recent") {
                    RevealBlock(index = 3) {
                        RecentBlock(
                            tracks = recentTracks,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            if (uiState.memories.isNotEmpty()) {
                item(key = "memory") {
                    RevealBlock(index = 4) {
                        Column {
                            HomeSectionHeader(title = stringResource(R.string.home_memories))
                            MemoryCard(
                                memories = uiState.memories,
                                season = uiState.memorySeason,
                                onTrackClick = onTrackClick,
                            )
                        }
                    }
                }
            } else if (uiState.likedTracks.isNotEmpty()) {
                item(key = "liked") {
                    RevealBlock(index = 4) {
                        Column {
                            HomeSectionHeader(title = stringResource(R.string.home_favourites))
                            LikedCard(
                                tracks = uiState.likedTracks,
                                onTrackClick = onTrackClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Мягкое появление блока: только прозрачность и небольшой подъём.
 */
@Composable
private fun RevealBlock(
    index: Int,
    content: @Composable () -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (revealed) return@LaunchedEffect

        delay(index * 35L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
        revealed = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = (1f - progress.value) * 12.dp.toPx()
            },
    ) {
        content()
    }
}

@Composable
private fun HomeHeader(onSettingsClick: () -> Unit) {
    // Язык даты берём из конфигурации: AppLocale подменяет её вместе с языком UI,
    // поэтому «ru» и «en» дают разные названия дней и месяцев.
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val dateLabel = remember(locale) {
        SimpleDateFormat("EEEE, d MMMM", locale)
            .format(Date())
            .replaceFirstChar { it.uppercase(locale) }
    }
    val greetingKey = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_day
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        }
    }
    val greeting = stringResource(greetingKey)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.home_settings_cd),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Заголовок секции Home — стандартный titleLarge. Без надзаголовков и капса:
 * иерархию задают размер и воздух между секциями.
 */
@Composable
private fun WavePromoCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Waves,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_wave_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.home_wave_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun NewReleasesBlock(
    releases: List<DeezerRelease>,
    onClick: (DeezerRelease) -> Unit,
) {
    Column {
        HomeSectionHeader(title = stringResource(R.string.home_new_releases))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = releases, key = { it.id }) { release ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onClick(release) },
                ) {
                    AsyncImage(
                        model = release.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = release.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = release.artist,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsBlock(
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    Column {
        HomeSectionHeader(title = stringResource(R.string.home_charts))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = tracks, key = { it.id }) { track ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onTrackClick(track, tracks) },
                ) {
                    Box {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.large),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                        ) {
                            Text(
                                text = (tracks.indexOf(track) + 1).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = track.artist,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenrePlaylistsBlock(
    sections: List<GenreSection>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    Column {
        HomeSectionHeader(title = stringResource(R.string.home_genre_playlists))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = sections, key = { it.genre }) { section ->
                Surface(
                    onClick = { onTrackClick(section.tracks.first(), section.tracks) },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(164.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = section.genre,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.home_genre_tracks,
                                section.tracks.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Скелетоны блоков: пульсирующие прямоугольники вместо пустого экрана. */
@Composable
private fun ExtrasSkeleton() {
    val transition = rememberInfiniteTransition(label = "extrasSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "extrasSkeletonAlpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(width = 132.dp, height = 132.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    queue: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Строку для скринридера считаем заранее: лямбда semantics не @Composable.
    val continueCd = stringResource(R.string.home_continue_cd, track.title)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { onTrackClick(track, queue) },
            )
            .semantics {
                contentDescription = continueCd
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.home_continue),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = { onTrackClick(track, queue) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.common_play_cd),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendationsBlock(
    tracks: List<Track>,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRetry: () -> Unit,
) {
    Column {
        HomeSectionHeader(title = stringResource(R.string.home_for_you))
        when {
            tracks.isNotEmpty() -> RecommendationsRow(
                tracks = tracks,
                listState = listState,
                onTrackClick = onTrackClick,
            )
            isLoading -> RecommendationsSkeleton()
            error != null -> RecommendationsMessage(
                title = stringResource(R.string.home_recs_error),
                message = error,
                actionLabel = stringResource(R.string.home_retry),
                onAction = onRetry,
            )
            else -> RecommendationsMessage(
                title = stringResource(R.string.home_recs_empty),
                message = stringResource(R.string.home_recs_empty_hint),
                actionLabel = stringResource(R.string.home_refresh),
                onAction = onRetry,
            )
        }
    }
}

@Composable
private fun RecommendationsRow(
    tracks: List<Track>,
    listState: LazyListState,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = tracks,
            key = { it.id },
        ) { track ->
            RecommendationCard(
                track = track,
                onClick = { onTrackClick(track, tracks) },
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    track: Track,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val playCd = stringResource(R.string.home_play_recommendation_cd, track.title, track.artist)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recommendationScale",
    )

    Column(modifier = Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(MaterialTheme.shapes.medium)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = playCd
                },
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecommendationsSkeleton() {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        repeat(4) {
            item {
                Column(modifier = Modifier.width(140.dp)) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(placeholder),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(14.dp)
                            .clip(PillShape)
                            .background(placeholder),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .height(10.dp)
                            .clip(PillShape)
                            .background(placeholder.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationsMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun RecentBlock(
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    Column {
        HomeSectionHeader(title = stringResource(R.string.home_recent))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = tracks.take(10),
                key = { it.id },
            ) { track ->
                RecentCover(
                    track = track,
                    onClick = { onTrackClick(track, tracks) },
                )
            }
        }
    }
}

@Composable
private fun RecentCover(
    track: Track,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val playCd = stringResource(R.string.home_play_track_cd, track.title)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recentScale",
    )

    val coverShape = MaterialTheme.shapes.medium

    Column(modifier = Modifier.width(132.dp)) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(coverShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = playCd
                },
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = coverShape,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                lineHeight = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MemoryCard(
    memories: List<SeasonMemory>,
    season: String,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val memoryTracks = remember(memories) { memories.map { it.track } }
    val preview = remember(memories) { memories.take(MEMORY_PREVIEW_LIMIT) }
    val firstTrack = memoryTracks.firstOrNull() ?: return
    val subtitle = pluralStringResource(
        R.plurals.home_memories_subtitle,
        memories.size,
        seasonName(season),
        memories.size,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_memories),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(
                    onClick = { onTrackClick(firstTrack, memoryTracks) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.home_play_memories_cd),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            preview.forEach { memory ->
                MemoryTrackRow(
                    track = memory.track,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { onTrackClick(memory.track, memoryTracks) },
                )
            }
            if (memories.size > preview.size) {
                Text(
                    text = stringResource(R.string.home_more, memories.size - preview.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 56.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MemoryTrackRow(
    track: Track,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.small,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                append(track.title)
                if (track.artist.isNotBlank()) {
                    append(" — ")
                    withStyle(SpanStyle(color = contentColor.copy(alpha = 0.68f))) {
                        append(track.artist)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        if (track.durationMs > 0) {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.68f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LikedCard(
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_favourites),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = { onTrackClick(tracks.first(), tracks) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_play_favourites_cd),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayBadge(
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
    contentDescription: String = stringResource(R.string.common_play_cd),
    size: Dp = 44.dp,
) {
    val baseModifier = Modifier
        .size(size)
        .clip(PillShape)
        .background(containerColor)
    val modifier = if (onClick == null) {
        baseModifier
    } else {
        baseModifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
            }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val model = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(true)
                .build()
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = notePainter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun seasonName(season: String): String = when (season) {
    SeasonProvider.WINTER -> stringResource(R.string.home_season_winter)
    SeasonProvider.SPRING -> stringResource(R.string.home_season_spring)
    SeasonProvider.SUMMER -> stringResource(R.string.home_season_summer)
    else -> stringResource(R.string.home_season_autumn)
}
