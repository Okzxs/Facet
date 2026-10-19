package com.my.music.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.data.model.Track
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item(key = "greeting_header") {
                RevealSection(index = 0) {
                    GreetingHeader(
                        onRefresh = { viewModel.refresh() },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }

            if (uiState.error != null) {
                item(key = "error_banner") {
                    RevealSection(index = 1) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "⚠️ ${uiState.error}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.refresh() }) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.recentTracks.isNotEmpty()) {
                item(key = "section_recent") {
                    RevealSection(index = 2) {
                        Column {
                            SectionHeader(
                                title = "Недавние",
                                subtitle = "То, что вы слушали недавно",
                            )
                            Spacer(Modifier.height(12.dp))
                            TracksCarousel(
                                tracks = uiState.recentTracks,
                                onTrackClick = { track -> onTrackClick(track, uiState.recentTracks) },
                            )
                        }
                    }
                }
            }

            if (uiState.likedTracks.isNotEmpty()) {
                item(key = "section_liked") {
                    RevealSection(index = 3) {
                        Column {
                            SectionHeader(
                                title = "Любимые треки",
                                subtitle = "${uiState.likedTracks.size} сохранено в медиатеке",
                            )
                            Spacer(Modifier.height(12.dp))
                            TracksCarousel(
                                tracks = uiState.likedTracks,
                                onTrackClick = { track -> onTrackClick(track, uiState.likedTracks) },
                            )
                        }
                    }
                }
            }

            uiState.genreSections.take(3).forEachIndexed { index, genreSection ->
                item(key = "genre_${genreSection.genre}") {
                    RevealSection(index = 4 + index) {
                        Column {
                            SectionHeader(
                                title = genreSection.genre,
                                subtitle = "Подборка на основе ваших предпочтений",
                            )
                            Spacer(Modifier.height(12.dp))
                            TracksCarousel(
                                tracks = genreSection.tracks,
                                onTrackClick = { track -> onTrackClick(track, genreSection.tracks) },
                            )
                        }
                    }
                }
            }

            item(key = "section_new_and_hot") {
                RevealSection(index = 7) {
                    Column {
                        SectionHeader(
                            title = "New & Hot",
                            subtitle = "Свежие тренды и новинки на SoundCloud",
                        )
                        Spacer(Modifier.height(12.dp))
                        if (uiState.isLoading && uiState.newAndHot.isEmpty()) {
                            SkeletonCarousel()
                        } else {
                            TracksCarousel(
                                tracks = uiState.newAndHot,
                                onTrackClick = { track -> onTrackClick(track, uiState.newAndHot) },
                            )
                        }
                    }
                }
            }

            item(key = "section_top_50") {
                RevealSection(index = 8) {
                    Column {
                        SectionHeader(
                            title = "Top 50 SoundCloud",
                            subtitle = "Самые прослушиваемые треки сейчас",
                        )
                        Spacer(Modifier.height(12.dp))
                        if (uiState.isLoading && uiState.top50.isEmpty()) {
                            SkeletonCarousel()
                        } else {
                            TopChartCarousel(
                                tracks = uiState.top50,
                                onTrackClick = { track -> onTrackClick(track, uiState.top50) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Каскадное появление секции: пружинный fade + подъём + микро-масштаб.
 * Размер секции резервируется сразу, поэтому список не «прыгает».
 */
@Composable
private fun RevealSection(
    index: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 55).toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 34.dp.toPx()
            val scale = 0.97f + 0.03f * progress.value
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

/**
 * Выразительная шапка-приветствие по времени суток с крупной типографикой.
 */
@Composable
private fun GreetingHeader(
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val (greetingText, greetingEmoji) = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Доброе утро" to "☀️"
            in 12..17 -> "Добрый день" to "🌤️"
            in 18..22 -> "Добрый вечер" to "🌆"
            else -> "Доброй ночи" to "🌙"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "$greetingText $greetingEmoji",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
            )
            Text(
                text = "Что послушаем сегодня?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onRefresh,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Горизонтальная карусель карточек треков (148×148 dp) со скруглением 20dp.
 */
@Composable
private fun TracksCarousel(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Нет треков в этой подборке",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(
            items = tracks,
            key = { it.id },
        ) { track ->
            TrackCarouselCard(
                track = track,
                onClick = { onTrackClick(track) },
            )
        }
    }
}

/**
 * Карточка трека в горизонтальной карусели: обложка 148×148, скругление 20dp.
 * При нажатии мягко сжимается и получает тонированную подсветку.
 */
@Composable
private fun TrackCarouselCard(
    track: Track,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.52f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "carouselCardScale",
    )
    val pressedTint by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else Color.Transparent,
        animationSpec = tween(160),
        label = "carouselCardTint",
    )

    Column(
        modifier = Modifier
            .width(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    bounded = true,
                    color = MaterialTheme.colorScheme.primary,
                ),
                onClick = onClick,
            ),
    ) {
        Surface(
            modifier = Modifier
                .size(148.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = surfaceColor,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    val imageRequest = remember(track.artworkUrl) {
                        ImageRequest.Builder(context)
                            .data(track.artworkUrl)
                            .crossfade(200)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        placeholder = placeholderPainter,
                        error = notePainter,
                        modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pressedTint),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
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

/**
 * Горизонтальная карусель Top 50 с крупными плакатными номерами позиций (1..50).
 * Топ-3 получают акцентную тонированную подложку.
 */
@Composable
private fun TopChartCarousel(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> track.id },
        ) { index, track ->
            val position = index + 1
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "chartCardScale",
            )
            val containerColor by animateColorAsState(
                targetValue = when {
                    pressed -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    position <= 3 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = tween(180),
                label = "chartCardColor",
            )

            Row(
                modifier = Modifier
                    .width(220.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(containerColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(
                            bounded = true,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    ) { onTrackClick(track) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$position",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                        ),
                        color = if (position <= 3) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    color = surfaceColor,
                ) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        val imageRequest = remember(track.artworkUrl) {
                            ImageRequest.Builder(context)
                                .data(track.artworkUrl)
                                .crossfade(200)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            placeholder = placeholderPainter,
                            error = notePainter,
                            modifier = Modifier.fillMaxSize(),
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
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
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
        }
    }
}

/**
 * Скелетон-карусель карточек при первичной загрузке без мерцания.
 */
@Composable
private fun SkeletonCarousel() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        items(
            count = 4,
            key = { index -> "skeleton_$index" },
        ) {
            Column(modifier = Modifier.width(148.dp)) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}
