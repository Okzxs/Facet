package com.my.music.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.core.ui.theme.BentoCardShape
import com.my.music.core.ui.theme.HeroShape
import com.my.music.core.ui.theme.PillShape
import com.my.music.core.ui.theme.SquircleShape
import com.my.music.core.util.SeasonProvider
import com.my.music.data.model.SeasonMemory
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val ScreenPadding = 20.dp
private val SectionGap = 30.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentTracks = uiState.recentTracks
    val firstRecent = recentTracks.firstOrNull()
    val firstGenre = uiState.genreSections.firstOrNull { it.tracks.isNotEmpty() }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(SectionGap),
        ) {
            item(key = "header") {
                RevealSection(index = 0) {
                    HomeHeader(onSettingsClick = onSettingsClick)
                }
            }

            if (firstRecent != null) {
                item(key = "hero") {
                    RevealSection(index = 1) {
                        ContinueListeningHero(
                            track = firstRecent,
                            queue = recentTracks,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            item(key = "charts") {
                RevealSection(index = 2) {
                    ChartsSection(
                        topRu = uiState.topRu,
                        topUs = uiState.topUs,
                        isLoading = uiState.isChartsLoading,
                        error = uiState.chartsError,
                        onTrackClick = onTrackClick,
                        onRetry = viewModel::refresh,
                    )
                }
            }

            if (recentTracks.isNotEmpty()) {
                item(key = "chronicle") {
                    RevealSection(index = 3) {
                        ChronicleSection(
                            tracks = recentTracks,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            if (uiState.memories.isNotEmpty()) {
                item(key = "memories") {
                    RevealSection(index = 4) {
                        MemoriesBentoCard(
                            memories = uiState.memories,
                            season = uiState.memorySeason,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }

            if (uiState.likedTracks.isNotEmpty() || firstGenre != null) {
                item(key = "collection") {
                    RevealSection(index = 5) {
                        CollectionBentoRow(
                            likedTracks = uiState.likedTracks,
                            genre = firstGenre,
                            onTrackClick = onTrackClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Появление секции на пружине: лёгкий подъём, масштаб и stagger по индексу.
 */
@Composable
private fun RevealSection(
    index: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(index * 45L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 26.dp.toPx()
            val scale = 0.98f + 0.02f * progress.value
            scaleX = scale
            scaleY = scale
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Чарты, любимое и немного ностальгии.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(50.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Настройки",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ContinueListeningHero(
    track: Track,
    queue: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroScale",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (pressed) 22.dp else 32.dp,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heroCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(dampingRatio = 0.8f),
        label = "heroContainer",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
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
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(26.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ПРОДОЛЖИТЬ СЛУШАТЬ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.source.displayName(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Включить",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsSection(
    topRu: List<Track>,
    topUs: List<Track>,
    isLoading: Boolean,
    error: String?,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRetry: () -> Unit,
) {
    Column {
        SectionHeader(
            eyebrow = "YOUTUBE",
            title = "Чарты",
            subtitle = "Top RU и Top US прямо сейчас",
        )
        Spacer(Modifier.height(14.dp))

        when {
            isLoading && topRu.isEmpty() && topUs.isEmpty() -> ChartSkeletonRow()
            topRu.isEmpty() && topUs.isEmpty() && error != null -> ChartErrorCard(
                message = error,
                onRetry = onRetry,
            )
            else -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChartBentoCard(
                    region = "RU",
                    tracks = topRu,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    queue = topRu,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.weight(1f),
                )
                ChartBentoCard(
                    region = "US",
                    tracks = topUs,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    queue = topUs,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartBentoCard(
    region: String,
    tracks: List<Track>,
    containerColor: Color,
    contentColor: Color,
    queue: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = SquircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = contentColor.copy(alpha = 0.62f),
                    )
                    Text(
                        text = region,
                        style = MaterialTheme.typography.displaySmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = "Нет данных",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                )
            } else {
                tracks.take(3).forEachIndexed { index, track ->
                    ChartTrackRow(
                        index = index,
                        track = track,
                        contentColor = contentColor,
                        onClick = { onTrackClick(track, queue) },
                    )
                    if (index < minOf(tracks.size, 3) - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 32.dp),
                            color = contentColor.copy(alpha = 0.14f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartTrackRow(
    index: Int,
    track: Track,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            ),
            color = contentColor.copy(alpha = 0.58f),
            modifier = Modifier.width(29.dp),
        )
        Spacer(Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 0.2.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = contentColor.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChartSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) { index ->
            val containerColor = if (index == 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            val contentColor = if (index == 0) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(220.dp),
                shape = SquircleShape,
                color = containerColor,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SkeletonBlock(
                        color = contentColor.copy(alpha = 0.22f),
                        widthFraction = 0.38f,
                        height = 12.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(
                        color = contentColor.copy(alpha = 0.30f),
                        widthFraction = 0.72f,
                        height = 38.dp,
                    )
                    Spacer(Modifier.height(18.dp))
                    repeat(3) {
                        SkeletonBlock(
                            color = contentColor.copy(alpha = 0.18f),
                            widthFraction = 1f,
                            height = 18.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    color: Color,
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(PillShape)
            .background(color),
    )
}

@Composable
private fun ChartErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape = BentoCardShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Чарты не загрузились",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@Composable
private fun ChronicleSection(
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    Column {
        SectionHeader(
            eyebrow = "ВАША ИСТОРИЯ",
            title = "Хроника",
            subtitle = "Последние прослушивания",
        )
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
            ) { index, track ->
                ChronicleCard(
                    track = track,
                    index = index,
                    onClick = { onTrackClick(track, tracks) },
                )
            }
        }
    }
}

@Composable
private fun ChronicleCard(
    track: Track,
    index: Int,
    onClick: () -> Unit,
) {
    val isWide = index % 3 != 1
    val cardWidth = if (isWide) 160.dp else 120.dp
    val imageHeight = if (isWide) 190.dp else 144.dp
    val topOffset = if (isWide) 0.dp else 26.dp
    val restCorner = if (isWide) 28.dp else 22.dp

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chronicleScale",
    )
    val corner by animateDpAsState(
        targetValue = if (pressed) restCorner - 6.dp else restCorner,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chronicleCorner",
    )
    val shape = RoundedCornerShape(corner)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .padding(top = topOffset)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(shape),
        ) {
            Artwork(
                url = track.artworkUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .background(Color.Black.copy(alpha = 0.42f), PillShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun MemoriesBentoCard(
    memories: List<SeasonMemory>,
    season: String,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    val memoryTracks = remember(memories) { memories.map { it.track } }
    val firstTrack = memoryTracks.firstOrNull() ?: return
    val containerColor = seasonContainerColor(season)
    val contentColor = seasonContentColor(season)
    val locale = remember { Locale.forLanguageTag("ru-RU") }
    val seasonYear = remember { SimpleDateFormat("yyyy", Locale.US).format(Date()) }
    val seasonName = seasonDisplayName(season)
    val coordinates = remember(memories) { memories.formatCoordinates() }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "memoryScale",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { onTrackClick(firstTrack, memoryTracks) },
            ),
        shape = SquircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box {
            Text(
                text = seasonName.uppercase(locale),
                style = MaterialTheme.typography.displayLarge,
                color = contentColor.copy(alpha = 0.08f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 16.dp),
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ВОСПОМИНАНИЯ · ${seasonName.uppercase(locale)} $seasonYear",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        color = contentColor.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = PillShape,
                        color = contentColor.copy(alpha = 0.13f),
                        contentColor = contentColor,
                    ) {
                        Text(
                            text = memories.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = seasonHeadline(season),
                            style = MaterialTheme.typography.headlineMedium,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "${memories.size} ${tracksWord(memories.size)} за сезон",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.76f),
                        )
                    }
                    if (memories.count { !it.track.artworkUrl.isNullOrBlank() } >= 2) {
                        Spacer(Modifier.width(14.dp))
                        MemoryArtworkStack(
                            memories = memories,
                            borderColor = containerColor,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.82f),
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = coordinates ?: "Геометка не сохранена",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.82f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = PillShape,
                        color = contentColor.copy(alpha = 0.13f),
                        contentColor = contentColor,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Слушать",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryArtworkStack(
    memories: List<SeasonMemory>,
    borderColor: Color,
) {
    val artworks = remember(memories) {
        memories.mapNotNull { it.track.artworkUrl }
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
    }
    val stackShape = RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .width(94.dp)
            .height(96.dp),
    ) {
        Artwork(
            url = artworks.getOrNull(0),
            contentDescription = null,
            modifier = Modifier
                .size(68.dp)
                .align(Alignment.TopStart)
                .rotate(-7f)
                .border(2.dp, borderColor, stackShape),
            shape = stackShape,
        )
        if (artworks.size > 1) {
            Artwork(
                url = artworks[1],
                contentDescription = null,
                modifier = Modifier
                    .size(68.dp)
                    .align(Alignment.BottomEnd)
                    .rotate(6f)
                    .border(2.dp, borderColor, stackShape),
                shape = stackShape,
            )
        }
    }
}

@Composable
private fun CollectionBentoRow(
    likedTracks: List<Track>,
    genre: GenreSection?,
    onTrackClick: (Track, List<Track>) -> Unit,
) {
    if (likedTracks.isEmpty() && genre == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (likedTracks.isNotEmpty()) {
            CollectionTile(
                label = "ЛЮБИМОЕ",
                value = likedTracks.size.toString().padStart(2, '0'),
                subtitle = "${likedTracks.size} ${tracksWord(likedTracks.size)}",
                action = "Включить",
                icon = Icons.Rounded.Favorite,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                valueStyle = MaterialTheme.typography.displaySmall,
                onClick = { onTrackClick(likedTracks.first(), likedTracks) },
                modifier = Modifier.weight(1.05f),
            )
        }
        if (genre != null) {
            CollectionTile(
                label = "ВАШ ЖАНР",
                value = genre.genre,
                subtitle = "${genre.tracks.size} ${tracksWord(genre.tracks.size)}",
                action = "Слушать",
                icon = Icons.Rounded.GraphicEq,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                valueStyle = MaterialTheme.typography.titleLarge,
                onClick = { onTrackClick(genre.tracks.first(), genre.tracks) },
                modifier = Modifier.weight(if (likedTracks.isNotEmpty()) 0.95f else 1f),
            )
        }
    }
}

@Composable
private fun CollectionTile(
    label: String,
    value: String,
    subtitle: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    valueStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "collectionScale",
    )

    Surface(
        modifier = modifier
            .heightIn(min = 158.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = SquircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = valueStyle,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
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
private fun seasonContainerColor(season: String): Color = when (season) {
    SeasonProvider.WINTER -> MaterialTheme.colorScheme.secondaryContainer
    SeasonProvider.SPRING -> MaterialTheme.colorScheme.tertiaryContainer
    SeasonProvider.SUMMER -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun seasonContentColor(season: String): Color = when (season) {
    SeasonProvider.WINTER -> MaterialTheme.colorScheme.onSecondaryContainer
    SeasonProvider.SPRING -> MaterialTheme.colorScheme.onTertiaryContainer
    SeasonProvider.SUMMER -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onTertiaryContainer
}

private fun seasonDisplayName(season: String): String = when (season) {
    SeasonProvider.WINTER -> "Зима"
    SeasonProvider.SPRING -> "Весна"
    SeasonProvider.SUMMER -> "Лето"
    else -> "Осень"
}

private fun seasonHeadline(season: String): String = when (season) {
    SeasonProvider.WINTER -> "Зима, которую вы сохранили"
    SeasonProvider.SPRING -> "Весна в вашем плейлисте"
    SeasonProvider.SUMMER -> "Летний саундтрек"
    else -> "Осень, которая звучит"
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

private fun List<SeasonMemory>.formatCoordinates(): String? {
    val memory = firstOrNull { it.latitude != null && it.longitude != null } ?: return null
    val latitude = formatCoordinate(memory.latitude!!, "N", "S")
    val longitude = formatCoordinate(memory.longitude!!, "E", "W")
    return "$latitude · $longitude"
}

private fun formatCoordinate(value: Double, positive: String, negative: String): String {
    val hemisphere = if (value >= 0) positive else negative
    return String.format(Locale.US, "%.2f° %s", abs(value), hemisphere)
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun Source.displayName(): String = when (this) {
    Source.SOUNDCLOUD -> "SoundCloud"
    Source.YOUTUBE -> "YouTube"
    Source.SPOTIFY -> "Spotify"
    Source.YANDEX -> "Яндекс Музыка"
    Source.VK -> "ВКонтакте"
    Source.LOCAL -> "Локальный файл"
}
