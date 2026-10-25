package com.my.music.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.ui.components.formatDuration
import com.my.music.core.ui.theme.PillShape
import com.my.music.core.ui.theme.SquircleShape
import com.my.music.core.util.SeasonProvider
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Track
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ScreenPadding = 20.dp
private val BlockGap = 26.dp
private const val MEMORY_PREVIEW_LIMIT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentTracks = uiState.recentTracks
    val heroTrack = recentTracks.firstOrNull()
    val homeListState = rememberLazyListState()
    val recommendationsListState = rememberLazyListState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 10.dp,
                bottom = 30.dp,
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

            item(key = "recommendations") {
                RevealBlock(index = 2) {
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
                        MemoryCard(
                            memories = uiState.memories,
                            season = uiState.memorySeason,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            } else if (uiState.likedTracks.isNotEmpty()) {
                item(key = "liked") {
                    RevealBlock(index = 4) {
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
    val locale = remember { Locale.forLanguageTag("ru-RU") }
    val dateLabel = remember(locale) {
        SimpleDateFormat("EEEE · d MMMM", locale)
            .format(Date())
            .uppercase(locale)
    }
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Настройки",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    queue: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroScale",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { onTrackClick(track, queue) },
            )
            .semantics {
                contentDescription = "Продолжить слушать ${track.title}"
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                shape = SquircleShape,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Продолжить слушать",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PlayBadge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
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
        Text(
            text = "Для вас",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        when {
            tracks.isNotEmpty() -> RecommendationsRow(
                tracks = tracks,
                listState = listState,
                onTrackClick = onTrackClick,
            )
            isLoading -> RecommendationsSkeleton()
            error != null -> RecommendationsMessage(
                title = "Не удалось загрузить рекомендации",
                message = error,
                actionLabel = "Повторить",
                onAction = onRetry,
            )
            else -> RecommendationsMessage(
                title = "Пока нет рекомендаций",
                message = "Попробуйте обновить подборку.",
                actionLabel = "Обновить",
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
                .clip(SquircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = "Воспроизвести ${track.title}, ${track.artist}"
                },
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = SquircleShape,
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
                            .clip(SquircleShape)
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
        Text(
            text = "Недавнее",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recentScale",
    )

    val coverShape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .size(126.dp)
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
                contentDescription = "Воспроизвести ${track.title}"
            },
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = coverShape,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(52.dp)
                .background(Color.Black.copy(alpha = 0.56f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 14.dp,
                    end = 14.dp,
                    bottom = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Воспоминания",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = seasonDisplayName(season),
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "За этот сезон",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.68f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = memories.size.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                    Text(
                        text = tracksWord(memories.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                PlayBadge(
                    containerColor = contentColor,
                    contentColor = containerColor,
                    onClick = { onTrackClick(firstTrack, memoryTracks) },
                    contentDescription = "Слушать воспоминания",
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = contentColor.copy(alpha = 0.16f),
            )

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                preview.forEach { memory ->
                    MemoryTrackRow(
                        track = memory.track,
                        contentColor = contentColor,
                        onClick = { onTrackClick(memory.track, memoryTracks) },
                    )
                }
                if (memories.size > preview.size) {
                    Text(
                        text = "…",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                    )
                }
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
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
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "likedScale",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { onTrackClick(tracks.first(), tracks) },
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Любимое",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tracks.size.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = contentColor,
                    maxLines = 1,
                )
                Text(
                    text = tracksWord(tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.68f),
                    maxLines = 1,
                )
            }
            PlayBadge(
                containerColor = contentColor,
                contentColor = containerColor,
            )
        }
    }
}

@Composable
private fun PlayBadge(
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)? = null,
    contentDescription: String = "Воспроизвести",
) {
    val baseModifier = Modifier
        .size(44.dp)
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
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape,
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

private fun seasonDisplayName(season: String): String = when (season) {
    SeasonProvider.WINTER -> "Зима"
    SeasonProvider.SPRING -> "Весна"
    SeasonProvider.SUMMER -> "Лето"
    else -> "Осень"
}

private fun tracksWord(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> "треков"
        last == 1 -> "трек"
        last in 2..4 -> "трека"
        else -> "треков"
    }
}
