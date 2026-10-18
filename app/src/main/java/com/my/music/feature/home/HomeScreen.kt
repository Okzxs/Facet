package com.my.music.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // ─── 1. Приветствие по времени суток (M3 Expressive Headline) ───
                item(key = "greeting_header") {
                    GreetingHeader(onRefresh = { viewModel.refresh() })
                }

                // ─── 2. Сообщение об ошибке (если есть) ─────────────────────────
                if (uiState.error != null) {
                    item(key = "error_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = RoundedCornerShape(16.dp),
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

                // ─── 3. Личная секция: «Недавние» (показывать, если есть данные) ──
                if (uiState.recentTracks.isNotEmpty()) {
                    item(key = "section_recent") {
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

                // ─── 4. Личная секция: «Любимые треки» (показывать, если есть данные)
                if (uiState.likedTracks.isNotEmpty()) {
                    item(key = "section_liked") {
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

                // ─── 5. Личная секция: «Твои жанры» (группировка по genre) ───────
                if (uiState.genreSections.isNotEmpty()) {
                    uiState.genreSections.take(3).forEach { genreSection ->
                        item(key = "genre_${genreSection.genre}") {
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

                // ─── 6. Открытие: «New & hot» (всегда, со скелетонами при загрузке) ─
                item(key = "section_new_and_hot") {
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

                // ─── 7. Открытие: «Top 50» (номерные карточки чарта) ─────────────
                item(key = "section_top_50") {
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

/**
 * Выразительная шапка-приветствие по времени суток с крупной типографикой.
 */
@Composable
private fun GreetingHeader(
    onRefresh: () -> Unit,
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

        FilledTonalIconButton(
            onClick = onRefresh,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Обновить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
    ) {
        Surface(
            modifier = Modifier
                .size(148.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
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
                        modifier = Modifier.size(40.dp),
                    )
                }
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
 * Горизонтальная карусель Top 50 с крупными плакатными номерами позиций (1..50)
 * по дизайн-спеке Material 3 Expressive (Решение 1).
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

            Row(
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable { onTrackClick(track) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Номер позиции в чарте
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

                // Обложка трека 64×64 dp
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

                // Название и артист
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
        items(4) {
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
