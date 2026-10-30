package com.facet.music.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facet.music.R
import com.facet.music.core.ui.components.EmptyState
import com.facet.music.core.ui.components.PlaylistCover
import com.facet.music.core.ui.components.TrackCard
import com.facet.music.data.model.Track
import kotlinx.coroutines.launch

/**
 * Экран одного плейлиста: шапка с обложкой и названием, быстрые действия,
 * треки в порядке добавления и удаление трека.
 *
 * Шапка повторяет ритм медиатеки и любимых треков — обложка слева, крупное
 * название справа; ниже те же кнопки «Слушать всё»/«Перемешать».
 * Экран полноэкранный: нижний док скрыт навграфом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val deleted by viewModel.deleted.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val updateFailedMessage = stringResource(R.string.playlist_update_failed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Один автопрокрут при открытии, а не на каждую смену трека.
    var scrolledToCurrent by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Плейлист удалён — возвращаемся в медиатеку, экрана больше нет.
    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    // Открыли плейлист с играющим треком — сразу показываем его строку,
    // как это делает шторка очереди. Шапка и кнопки занимают первый элемент.
    LaunchedEffect(currentTrackId, tracks) {
        if (scrolledToCurrent) return@LaunchedEffect
        val index = tracks.indexOfFirst { it.id == currentTrackId }
        if (index >= 0) {
            listState.animateScrollToItem(index + PLAYLIST_LIST_HEADER_ITEMS)
            scrolledToCurrent = true
        }
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

    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = updateFailedMessage,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeFailure()
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
                // Имя крупно показано в шапке — в топбаре не дублируем его.
                title = {
                    Text(
                        text = stringResource(R.string.playlist_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.track_actions_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_delete_action)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            item(key = "playlist_header") {
                PlaylistHeader(
                    name = playlist?.name ?: stringResource(R.string.playlist_title),
                    coverUrl = playlist?.coverUrl ?: tracks.firstOrNull()?.artworkUrl,
                    trackCount = tracks.size,
                    canPlay = tracks.isNotEmpty(),
                    onPlayAll = { tracks.firstOrNull()?.let { onTrackClick(it, tracks) } },
                    onShuffle = {
                        val shuffled = tracks.shuffled()
                        shuffled.firstOrNull()?.let { onTrackClick(it, shuffled) }
                    },
                )
            }

            if (tracks.isEmpty()) {
                item(key = "playlist_empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = stringResource(R.string.playlist_tracks_empty_title),
                        message = stringResource(R.string.playlist_tracks_empty_message),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(items = tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        isPlaying = track.id == currentTrackId,
                        isPaused = track.id == currentTrackId && !isPlaying,
                        artworkSize = 60,
                        artworkCornerDp = 16,
                        onClick = { onTrackClick(track, tracks) },
                        onArtistClick = { onArtistClick(track.artist) },
                        onPlayNext = { handlePlayNext(track) },
                        onAddToQueue = { handleAddToQueue(track) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.removeTrack(track.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(
                                        R.string.playlist_remove_track_cd,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.playlist_delete_message,
                        playlist?.name.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePlaylist()
                    },
                ) {
                    Text(stringResource(R.string.playlist_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.playlist_delete_cancel))
                }
            },
        )
    }
}

/**
 * Шапка плейлиста: обложка, название и счётчик. Кнопки повторяют ритм
 * «Любимых треков», поэтому коллекции выглядят одинаково.
 */
@Composable
private fun PlaylistHeader(
    name: String,
    coverUrl: String?,
    trackCount: Int,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(
                name = name,
                coverUrl = coverUrl,
                modifier = Modifier.size(104.dp),
                corner = 28.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.track_count, trackCount, trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
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
                Text(stringResource(R.string.library_play_all))
            }
            // Перемешать — иконка без подписи: подпись не влезает
            // в половину ширины и переносилась на вторую строку.
            FilledTonalIconButton(
                onClick = onShuffle,
                enabled = canPlay,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = stringResource(R.string.library_shuffle),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Шапка плейлиста и ряд кнопок занимают первые два элемента списка. */
private const val PLAYLIST_LIST_HEADER_ITEMS = 1
