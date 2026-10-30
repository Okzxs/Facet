package com.my.music.feature.artist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.R
import com.my.music.core.ui.components.EmptyState
import com.my.music.core.ui.components.PlaylistCover
import com.my.music.core.ui.components.SectionHeader
import com.my.music.core.ui.components.TrackArtwork
import com.my.music.core.ui.components.TrackCard
import com.my.music.data.model.Track
import com.my.music.data.source.external.DeezerAlbum
import com.my.music.data.source.external.DeezerArtistRef
import kotlinx.coroutines.launch
import java.util.Locale

/** Шапка и заголовок секции занимают первые два элемента списка. */
private const val ARTIST_LIST_HEADER_ITEMS = 2

/** Высота hero-зоны артиста: хватает на аватар, имя, статистику и кнопки. */
private val ArtistHeroHeight = 372.dp

/**
 * Экран артиста: иммерсивная шапка с размытым аватаром-задником, статистика
 * Deezer, популярные треки, карусель альбомов и похожие артисты.
 *
 * Экран полноэкранный (нижний док скрыт навграфом), поэтому шапка уходит под
 * статус-бар, а кнопка «назад» живёт прямо в ней.
 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val albumError by viewModel.albumError.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Прокручиваем к текущему треку один раз при открытии: иначе каждый
    // следующий трек выдёргивал бы список из-под пальца.
    var scrolledToCurrent by remember { mutableStateOf(false) }

    // Экран открыли с играющим треком — сразу показываем его строку.
    LaunchedEffect(currentTrackId, state.tracks) {
        if (scrolledToCurrent) return@LaunchedEffect
        val index = state.tracks.indexOfFirst { it.id == currentTrackId }
        if (index >= 0) {
            listState.animateScrollToItem(index + ARTIST_LIST_HEADER_ITEMS)
            scrolledToCurrent = true
        }
    }

    // «Слушать альбом»: треки загружает ViewModel, экран запускает очередь.
    LaunchedEffect(viewModel) {
        viewModel.playQueue.collect { tracks ->
            tracks.firstOrNull()?.let { first -> onTrackClick(first, tracks) }
        }
    }

    LaunchedEffect(albumError) {
        if (!albumError) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = context.getString(R.string.artist_album_tracks_failed),
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeAlbumError()
    }

    // Очередь живёт в плеере: подтверждение действий — Snackbar.
    val handlePlayNext: (Track) -> Unit = { track ->
        if (onPlayNext(track)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.queue_play_next_done, track.title),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
    val handleAddToQueue: (Track) -> Unit = { track ->
        val added = onAddToQueue(track)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    if (added) R.string.queue_added_done else R.string.queue_already_added,
                    track.title,
                ),
                duration = SnackbarDuration.Short,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "artist_header") {
                ArtistHero(
                    state = state,
                    onBack = onBack,
                    onPlayAll = {
                        state.tracks.firstOrNull()?.let { first ->
                            onTrackClick(first, state.tracks)
                        }
                    },
                    onShuffle = {
                        val shuffled = state.tracks.shuffled()
                        shuffled.firstOrNull()?.let { first -> onTrackClick(first, shuffled) }
                    },
                )
            }

            if (state.isLoading) {
                item(key = "artist_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.failed) {
                item(key = "artist_failed") {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.artist_load_failed),
                        message = stringResource(R.string.artist_load_failed_hint),
                        actionLabel = stringResource(R.string.artist_retry),
                        onAction = viewModel::retry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item(key = "artist_section") {
                    SectionHeader(title = stringResource(R.string.artist_top_tracks))
                }
                items(items = state.tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        isPaused = track.id == currentTrackId && !isPlaying,
                        artworkSize = 60,
                        artworkCornerDp = 16,
                        onClick = { onTrackClick(track, state.tracks) },
                        onPlayNext = { handlePlayNext(track) },
                        onAddToQueue = { handleAddToQueue(track) },
                    )
                }

                if (state.albums.isNotEmpty()) {
                    item(key = "artist_albums_header") {
                        SectionHeader(title = stringResource(R.string.artist_albums_title))
                    }
                    item(key = "artist_albums_row") {
                        // Обычный Row с горизонтальным скроллом, а не LazyRow:
                        // в LazyColumn ленивый ряд обрезал подпись под обложкой.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.albums.forEach { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { viewModel.playAlbum(album.id) },
                                )
                            }
                        }
                    }
                }

                if (state.related.isNotEmpty()) {
                    item(key = "artist_related_header") {
                        SectionHeader(title = stringResource(R.string.artist_similar_title))
                    }
                    item(key = "artist_related_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.related.forEach { related ->
                                ArtistBubble(
                                    artist = related,
                                    onClick = { onArtistClick(related.name) },
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

/**
 * Иммерсивная шапка: размытый аватар на фоне, круглая аватарка, имя,
 * статистика Deezer и кнопки «Слушать всё»/«Перемешать».
 *
 * Фон затемняется к низу до цвета темы — поэтому текст читается на любом
 * фото, а имя из Deezer может быть светлым или тёмным.
 */
@Composable
private fun ArtistHero(
    state: ArtistUiState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val canPlay = state.tracks.isNotEmpty()
    val displayName = state.name.ifBlank { stringResource(R.string.artist_title) }

    val stats = listOfNotNull(
        if (state.fans > 0) {
            stringResource(R.string.artist_followers, formatCompactCount(state.fans))
        } else {
            null
        },
        if (state.albumCount > 0) {
            stringResource(R.string.artist_albums, formatCompactCount(state.albumCount))
        } else {
            null
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ArtistHeroHeight),
    ) {
        if (!state.pictureUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(state.pictureUrl)
                .crossfade(300)
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.65f,
                modifier = Modifier
                    .fillMaxSize()
                    // На Android ниже 12 blur игнорируется — остаётся полупрозрачное
                    // фото, которое всё равно закрыто градиентом.
                    .blur(56.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                background,
                            ),
                        ),
                    ),
            )
        }

        // Затемнение к низу: имя и кнопки всегда лежат на цвете фона.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to background.copy(alpha = 0.35f),
                        0.75f to background.copy(alpha = 0.92f),
                        1f to background,
                    ),
                ),
        )

        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
                .size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(124.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
            ) {
                TrackArtwork(
                    url = state.pictureUrl,
                    contentDescription = stringResource(R.string.artist_avatar_cd, displayName),
                    size = 124,
                    cornerDp = 62,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stats.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPlayAll,
                    enabled = canPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.artist_play_all))
                }
                FilledTonalIconButton(
                    onClick = onShuffle,
                    enabled = canPlay,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.artist_shuffle),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Карточка альбома в карусели: обложка, название и год релиза. */
@Composable
private fun AlbumCard(
    album: DeezerAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            // Без clip: скругление обрезало первую цифру года в нижнем углу.
            .clickable(onClick = onClick),
    ) {
        // Явный размер вместо aspectRatio: внутри горизонтального скролла
        // коэффициент сторон иногда съедал высоту подписи.
        PlaylistCover(
            name = album.title,
            coverUrl = album.coverUrl,
            modifier = Modifier.size(148.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!album.year.isNullOrBlank()) {
            Text(
                text = album.year,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Круглый аватар похожего артиста: тап открывает его экран. */
@Composable
private fun ArtistBubble(
    artist: DeezerArtistRef,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(
            url = artist.pictureUrl,
            contentDescription = null,
            size = 84,
            cornerDp = 42,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Компактная запись больших чисел: 1.2M, 850K, 1234. */
private fun formatCompactCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.US, "%.0fK", value / 1_000f)
    else -> value.toString()
}
