package com.my.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.toTrack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.R
import com.my.music.core.download.DownloadState
import com.my.music.core.ui.components.AddToPlaylistSheet
import com.my.music.core.ui.components.TrackCard
import com.my.music.core.ui.components.TrackDownloadButton
import com.my.music.data.model.Playlist
import com.my.music.data.model.Track
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.my.music.core.import.ImportError
import com.my.music.core.import.ImportedPlaylist
import com.my.music.navigation.LocalBottomDockHeight
import kotlinx.coroutines.launch

/**
 * Экран медиатеки: любимые треки с быстрыми действиями,
 * подсветкой играющего трека и пустым состоянием с переходом в поиск.
 */
@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenSearch: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val likeError by viewModel.likeError.collectAsState()
    val playlistError by viewModel.playlistError.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlayerPlaying by viewModel.isPlaying.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(LibraryTab.MEDIA) }
    var pendingDelete by remember { mutableStateOf<Track?>(null) }
    var pendingPlaylistDelete by remember { mutableStateOf<Playlist?>(null) }
    // Трек, для которого открыта шторка «Добавить в плейлист».
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    // Диалог ввода ссылки для импорта и сам текст ссылки.
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importLink by rememberSaveable { mutableStateOf("") }

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

    LaunchedEffect(downloadError) {
        val message = downloadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeDownloadError()
    }

    LaunchedEffect(likeError) {
        val message = likeError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeLikeError()
    }

    LaunchedEffect(playlistError) {
        val message = playlistError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumePlaylistError()
    }

    // Причина ошибки импорта → локализованный текст. Строку считаем в
    // композиции: stringResource нельзя вызывать из корутины Snackbar.
    val importErrorMessage = importError?.let { error -> stringResource(importErrorRes(error)) }
    LaunchedEffect(importErrorMessage) {
        val message = importErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeImportError()
    }

    val importSuccessMessage = importSuccess?.let { name ->
        stringResource(R.string.import_success, name)
    }
    LaunchedEffect(importSuccessMessage) {
        val message = importSuccessMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeImportSuccess()
    }

    Scaffold(
        // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Статус-бар обеспечивает сам экран (корневого Scaffold больше нет);
        // низ — через contentPadding списка с запасом под плавающий док.
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LibraryHeader(
                trackCount = when (tab) {
                    LibraryTab.MEDIA -> likedTracks.size
                    LibraryTab.DOWNLOADED -> downloadedTracks.size
                    LibraryTab.PLAYLISTS -> playlists.size
                },
                totalMs = when (tab) {
                    LibraryTab.MEDIA -> likedTracks.sumOf { it.durationMs }
                    LibraryTab.DOWNLOADED -> downloadedTracks.sumOf { it.durationMs }
                    LibraryTab.PLAYLISTS -> 0L
                },
                playlistsMode = tab == LibraryTab.PLAYLISTS,
            )

            LibraryTabs(
                selected = tab,
                onSelect = { tab = it },
            )

            // Подтверждение удаления скачанного файла с устройства.
            pendingDelete?.let { track ->
                DeleteDownloadDialog(
                    title = track.title,
                    onConfirm = {
                        viewModel.removeDownload(track.id)
                        pendingDelete = null
                    },
                    onDismiss = { pendingDelete = null },
                )
            }

            // Подтверждение удаления плейлиста: треки уходят каскадом.
            pendingPlaylistDelete?.let { playlist ->
                DeletePlaylistDialog(
                    name = playlist.name,
                    onConfirm = {
                        viewModel.deletePlaylist(playlist.id)
                        pendingPlaylistDelete = null
                    },
                    onDismiss = { pendingPlaylistDelete = null },
                )
            }

            when (tab) {
                LibraryTab.MEDIA -> if (likedTracks.isEmpty()) {
                LibraryEmptyState(
                    onOpenSearch = onOpenSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalBottomDockHeight.current),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Быстрые кнопки: «Слушать всё» шире «Перемешать» (1.6 к 1.0)
                    item(key = "library_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    onTrackClick(likedTracks.first(), likedTracks)
                                },
                                modifier = Modifier
                                    .weight(1.6f)
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

                            FilledTonalButton(
                                onClick = {
                                    val shuffled = likedTracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.library_shuffle))
                            }
                        }
                    }

                    items(
                        items = likedTracks,
                        key = { it.id },
                    ) { track ->
                        TrackCard(
                            track = track,
                            isDownloaded = track.id in downloadedIds,
                            isPlaying = track.id == currentTrackId,
                            isPaused = track.id == currentTrackId && !isPlayerPlaying,
                            artworkSize = 60,
                            artworkCornerDp = 16,
                            onClick = { onTrackClick(track, likedTracks) },
                            onAddToPlaylist = { playlistTrack = track },
                            // Долгий тап по карточке открывает экран артиста.
                            onArtistClick = { onArtistClick(track.artist) },
                            onPlayNext = { handlePlayNext(track) },
                            onAddToQueue = { handleAddToQueue(track) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TrackDownloadButton(
                                        state = downloadStates[track.id]
                                            ?: DownloadState.NotDownloaded,
                                        onClick = { viewModel.downloadTrack(track) },
                                    )
                                    IconButton(onClick = { viewModel.removeLike(track) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = stringResource(R.string.library_remove_like),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                }

                LibraryTab.DOWNLOADED -> DownloadedContent(
                    downloaded = downloadedTracks,
                    currentTrackId = currentTrackId,
                    onTrackClick = onTrackClick,
                    onDelete = { track -> pendingDelete = track },
                    onOpenSearch = onOpenSearch,
                    onAddToPlaylist = { track -> playlistTrack = track },
                    onArtistClick = onArtistClick,
                    onPlayNext = handlePlayNext,
                    onAddToQueue = handleAddToQueue,
                )

                LibraryTab.PLAYLISTS -> PlaylistsContent(
                    playlists = playlists,
                    onPlaylistClick = onPlaylistClick,
                    onDelete = { playlist -> pendingPlaylistDelete = playlist },
                    onImportClick = { showImportDialog = true },
                )
            }
        }
    }

    // ─── Импорт плейлиста по ссылке ───────────────────────────────────────
    if (showImportDialog) {
        ImportLinkDialog(
            link = importLink,
            onLinkChange = { importLink = it },
            onConfirm = {
                showImportDialog = false
                viewModel.startImport(importLink)
            },
            onDismiss = { showImportDialog = false },
        )
    }

    when (val state = importState) {
        PlaylistImportState.Idle -> Unit
        PlaylistImportState.Loading -> ImportProgressDialog(
            text = stringResource(R.string.import_link_loading),
        )
        PlaylistImportState.Saving -> ImportProgressDialog(
            text = stringResource(R.string.import_link_saving),
        )
        is PlaylistImportState.Preview -> ImportPreviewDialog(
            playlist = state.playlist,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }

    // Шторка живёт вне Scaffold: ModalBottomSheet рисуется в отдельном окне,
    // а Snackbar «Добавлено в …» показывает сама медиатека.
    playlistTrack?.let { track ->
        AddToPlaylistSheet(
            track = track,
            onDismiss = { playlistTrack = null },
            onAdded = { playlistName ->
                playlistTrack = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.playlist_added_to, playlistName),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
        )
    }
}

/**
 * Шапка медиатеки: крупный заголовок и подпись с количеством треков
 * и суммарной длительностью. Плюрализация — через ресурсы `track_count`
 * и `playlist_count`, поэтому форма слова верная в любом языке.
 */
@Composable
private fun LibraryHeader(trackCount: Int, totalMs: Long, playlistsMode: Boolean) {
    val label = if (playlistsMode) {
        pluralStringResource(R.plurals.playlist_count, trackCount, trackCount)
    } else {
        pluralStringResource(R.plurals.track_count, trackCount, trackCount)
    }
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // Для пустой коллекции не показываем «0 мин» — только количество.
            text = if (!playlistsMode && totalMs > 0L) {
                "$label · ${formatTotalDuration(totalMs)}"
            } else {
                label
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Пустое состояние: squircle-плашка, заголовок, подсказка и выход в поиск. */
@Composable
private fun LibraryEmptyState(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.library_empty_message),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onOpenSearch,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_find_music))
            }
        }
    }
}

/** Суммарная длительность коллекции: «1 ч 5 мин» или «42 мин». */
@Composable
private fun formatTotalDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

/**
 * Вкладки медиатеки: коллекция лайков, офлайн-файлы и плейлисты.
 */
private enum class LibraryTab { MEDIA, DOWNLOADED, PLAYLISTS }

@Composable
private fun LibraryTabs(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val tabs = listOf(
            LibraryTab.MEDIA to stringResource(R.string.library_tab_library),
            LibraryTab.DOWNLOADED to stringResource(R.string.library_tab_downloaded),
            LibraryTab.PLAYLISTS to stringResource(R.string.library_tab_playlists),
        )
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
            ) {
                Text(label)
            }
        }
    }
}

/** Диалог удаления скачанного трека с устройства. */
@Composable
private fun DeleteDownloadDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_delete_cancel)) }
        },
    )
}

/** Диалог удаления плейлиста: треки удаляются вместе с ним. */
@Composable
private fun DeletePlaylistDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_delete_title)) },
        text = { Text(stringResource(R.string.playlist_delete_message, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.playlist_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_delete_cancel)) }
        },
    )
}

/**
 * Вкладка «Плейлисты»: кнопка импорта по ссылке, затем список плейлистов.
 * Тап по строке открывает экран плейлиста.
 */
@Composable
private fun PlaylistsContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Long) -> Unit,
    onDelete: (Playlist) -> Unit,
    onImportClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Кнопка над списком: импорт доступен и на пустой вкладке.
        FilledTonalButton(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Rounded.AddLink,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_link_button))
        }

        if (playlists.isEmpty()) {
            PlaylistsEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp + LocalBottomDockHeight.current,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = playlists, key = { it.id }) { playlist ->
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ListItem(
                            modifier = Modifier.clickable { onPlaylistClick(playlist.id) },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.track_count,
                                        playlist.trackCount,
                                        playlist.trackCount,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(playlist) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(
                                            R.string.playlist_delete_cd,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Пустое состояние «Плейлисты»: подсказываем, откуда их брать. */
@Composable
private fun PlaylistsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.playlist_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.playlist_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Вкладка «Скачанное»: офлайн-файлы с удалением по кнопке. */
@Composable
private fun DownloadedContent(
    downloaded: List<DownloadedTrack>,
    currentTrackId: String?,
    onTrackClick: (Track, List<Track>) -> Unit,
    onDelete: (Track) -> Unit,
    onOpenSearch: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
) {
    if (downloaded.isEmpty()) {
        DownloadedEmptyState(onOpenSearch = onOpenSearch)
        return
    }
    val tracks = downloaded.map { it.toTrack() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomDockHeight.current),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackCard(
                track = track,
                isDownloaded = true,
                isPlaying = track.id == currentTrackId,
                artworkSize = 60,
                artworkCornerDp = 16,
                onClick = { onTrackClick(track, tracks) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onArtistClick = { onArtistClick(track.artist) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                trailingContent = {
                    IconButton(onClick = { onDelete(track) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.library_downloaded_menu),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

/** Пустое состояние «Скачанное». */
@Composable
private fun DownloadedEmptyState(onOpenSearch: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.library_downloaded_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.library_downloaded_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onOpenSearch, shape = CircleShape) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_find_music))
            }
        }
    }
}

/** Диалог ввода ссылки на плейлист. */
@Composable
private fun ImportLinkDialog(
    link: String,
    onLinkChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_link_title)) },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.import_link_label)) },
                placeholder = { Text(stringResource(R.string.import_link_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (link.isNotBlank()) onConfirm() },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = link.isNotBlank()) {
                Text(stringResource(R.string.import_link_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_link_cancel)) }
        },
    )
}

/** Прогресс загрузки и сохранения: диалог без кнопок и вне закрытия тапом. */
@Composable
private fun ImportProgressDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text) },
        text = {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {},
    )
}

/** Превью импорта: название, счётчик и первые 10 треков. */
@Composable
private fun ImportPreviewDialog(
    playlist: ImportedPlaylist,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_preview_title)) },
        text = {
            Column {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.track_count,
                        playlist.tracks.size,
                        playlist.tracks.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                playlist.tracks.take(PREVIEW_TRACK_COUNT).forEach { track ->
                    Text(
                        text = stringResource(
                            R.string.import_track_line,
                            track.title,
                            track.artist,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_link_cancel)) }
        },
    )
}

/** Код ошибки импорта → строковый ресурс: локализацию выбирает UI. */
private fun importErrorRes(error: ImportError): Int = when (error) {
    ImportError.EMPTY_LINK -> R.string.import_error_empty
    ImportError.UNSUPPORTED_LINK -> R.string.import_error_unsupported
    ImportError.YANDEX_UNSUPPORTED -> R.string.import_error_yandex
    ImportError.APPLE_PLAYLIST_UNSUPPORTED -> R.string.import_error_apple
    ImportError.SPOTIFY_NOT_CONFIGURED -> R.string.import_error_spotify_keys
    ImportError.EMPTY_PLAYLIST -> R.string.import_error_empty_playlist
    ImportError.NETWORK -> R.string.import_error_network
    ImportError.SAVE_FAILED -> R.string.import_error_save
}

/** Сколько треков показываем в превью до подтверждения импорта. */
private const val PREVIEW_TRACK_COUNT = 10
