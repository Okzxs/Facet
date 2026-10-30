package com.my.music.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.R
import com.my.music.core.ui.components.TrackArtwork
import com.my.music.core.ui.components.TrackCard
import com.my.music.data.model.Track
import kotlinx.coroutines.launch

/** Сколько элементов списка идёт до первого трека: шапка артиста и заголовок секции. */
private const val ARTIST_LIST_HEADER_ITEMS = 2

/**
 * Экран артиста: круглая обложка первого трека, имя, «Слушать всё»
 * и список популярных треков.
 *
 * Популярные треки приходят из Deezer (превью 30 с), при пустом ответе —
 * из поиска YouTube. Экран полноэкранный: нижний док навграф скрывает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val avatarUrl = state.tracks.firstOrNull()?.artworkUrl
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Экран открыли с играющим треком — сразу показываем его строку.
    // Шапка и заголовок секции занимают первые два элемента списка.
    LaunchedEffect(currentTrackId, state.tracks) {
        val index = state.tracks.indexOfFirst { it.id == currentTrackId }
        if (index >= 0) listState.animateScrollToItem(index + ARTIST_LIST_HEADER_ITEMS)
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
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
                    // Имя крупно показано в шапке — в топбаре не дублируем его.
                    Text(
                        text = stringResource(R.string.artist_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "artist_header") {
                ArtistHeader(
                    name = state.name,
                    avatarUrl = avatarUrl,
                    canPlay = state.tracks.isNotEmpty(),
                    onPlayAll = {
                        state.tracks.firstOrNull()?.let { first ->
                            onTrackClick(first, state.tracks)
                        }
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
                    ArtistFailedState(onRetry = viewModel::retry)
                }
            } else {
                item(key = "artist_section") {
                    Text(
                        text = stringResource(R.string.artist_top_tracks),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
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
            }
        }
    }
}

/** Шапка артиста: круглая обложка, имя и кнопка «Слушать всё». */
@Composable
private fun ArtistHeader(
    name: String,
    avatarUrl: String?,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(
            url = avatarUrl,
            contentDescription = stringResource(
                R.string.artist_avatar_cd,
                name.ifBlank { stringResource(R.string.artist_title) },
            ),
            size = 120,
            // Радиус равен половине стороны — получается ровный круг.
            cornerDp = 60,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = name.ifBlank { stringResource(R.string.artist_title) },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPlayAll,
            enabled = canPlay,
            modifier = Modifier.height(52.dp),
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
    }
}

/** Ошибка загрузки: ни Deezer, ни YouTube не дали треков. */
@Composable
private fun ArtistFailedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.artist_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry, shape = CircleShape) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.artist_retry))
        }
    }
}
