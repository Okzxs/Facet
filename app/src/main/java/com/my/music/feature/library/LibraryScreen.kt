package com.my.music.feature.library

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.R
import com.my.music.core.download.DownloadState
import com.my.music.core.import.ImportError
import com.my.music.core.spotify.SpotifyPlaylistSummary
import com.my.music.core.import.ImportedPlaylist
import com.my.music.core.ui.components.AddToPlaylistSheet
import com.my.music.core.ui.components.CollectionChipCard
import com.my.music.core.ui.components.CollectionSummary
import com.my.music.core.ui.components.PlaylistCover
import com.my.music.core.ui.components.PlaylistTile
import com.my.music.core.ui.components.SectionHeader
import com.my.music.core.ui.components.TrackCard
import com.my.music.core.ui.components.TrackDownloadButton
import com.my.music.data.model.Playlist
import com.my.music.data.model.Track
import com.my.music.data.model.toTrack
import com.my.music.navigation.LocalBottomDockHeight
import kotlinx.coroutines.launch

/**
 * Медиатека — единый экран коллекции без вкладок.
 *
 * Сверху крупный чипс «Любимые треки» (открывает отдельный экран), ниже
 * секция «Плейлисты» с сеткой сохранённых плейлистов и секция «Скачанное».
 * Раньше это были три сегмента одной вкладки, и содержимое каждого
 * начиналось с собственного заголовка — теперь один заголовок и общий ритм.
 */
@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenSearch: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    onLikedTracksClick: () -> Unit,
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
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val spotifyConnected by viewModel.spotifyConnected.collectAsState()
    val spotifyPlaylists by viewModel.spotifyPlaylists.collectAsState()
    val spotifyLoading by viewModel.spotifyLoading.collectAsState()
    val spotifyError by viewModel.spotifyError.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Track?>(null) }
    // Трек, для которого открыта шторка «Добавить в плейлист».
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    // Диалог ввода ссылки для импорта и сам текст ссылки.
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importLink by rememberSaveable { mutableStateOf("") }
    // Диалог создания плейлиста прямо из медиатеки.
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newPlaylistName by rememberSaveable { mutableStateOf("") }

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
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.consumeDownloadError()
    }

    LaunchedEffect(likeError) {
        val message = likeError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.consumeLikeError()
    }

    LaunchedEffect(playlistError) {
        val message = playlistError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.consumePlaylistError()
    }

    // Причина ошибки импорта → локализованный текст. Строку считаем в
    // композиции: stringResource нельзя вызывать из корутины Snackbar.
    val importErrorMessage = importError?.let { error -> stringResource(importErrorRes(error)) }
    LaunchedEffect(importErrorMessage) {
        val message = importErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.consumeImportError()
    }

    val importSuccessMessage = importSuccess?.let { name ->
        stringResource(R.string.import_success, name)
    }
    LaunchedEffect(importSuccessMessage) {
        val message = importSuccessMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        viewModel.consumeImportSuccess()
    }

    val spotifyErrorMessage = stringResource(R.string.spotify_import_failed)
    LaunchedEffect(spotifyError) {
        if (!spotifyError) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = spotifyErrorMessage,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeSpotifyError()
    }

    Scaffold(
        // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomDockHeight.current),
        ) {
            item(key = "library_header") {
                LibraryHeader(
                    trackCount = likedTracks.size,
                    playlistCount = playlists.size,
                )
            }

            // ─── Любимые треки: большой чипс на всю ширину ────────────────
            item(key = "library_favorites") {
                CollectionChipCard(
                    title = stringResource(R.string.library_favorites),
                    subtitle = likedTracksSubtitle(likedTracks),
                    icon = Icons.Filled.Favorite,
                    onClick = onLikedTracksClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ─── Плейлисты ───────────────────────────────────────────────
            item(key = "library_playlists_header") {
                SectionHeader(
                    title = stringResource(R.string.library_tab_playlists),
                    actionLabel = stringResource(R.string.library_import_short),
                    actionIcon = Icons.Rounded.AddLink,
                    onAction = { showImportDialog = true },
                )
            }
            if (playlists.isEmpty()) {
                item(key = "library_playlists_empty") {
                    PlaylistsEmptyCard(
                        onCreate = {
                            newPlaylistName = ""
                            showCreateDialog = true
                        },
                        onImport = { showImportDialog = true },
                    )
                }
            } else {
                playlists.chunked(PLAYLIST_GRID_COLUMNS).forEachIndexed { index, row ->
                    item(key = "library_playlists_row_$index") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { playlist ->
                                PlaylistTile(
                                    playlistName = playlist.name,
                                    coverUrl = playlist.coverUrl,
                                    trackCount = playlist.trackCount,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Нечётное число плиток: пустая колонка держит ширину.
                            if (row.size < PLAYLIST_GRID_COLUMNS) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ─── Плейлисты Spotify (только после входа) ──────────────────
            if (spotifyConnected) {
                item(key = "library_spotify_header") {
                    SectionHeader(
                        title = stringResource(R.string.spotify_import_row),
                        actionIcon = Icons.Rounded.Refresh,
                        onAction = viewModel::refreshSpotifyPlaylists,
                    )
                }
                item(key = "library_spotify_row") {
                    when {
                        spotifyLoading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                        spotifyPlaylists.isEmpty() -> Text(
                            text = stringResource(R.string.spotify_import_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        else -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            spotifyPlaylists.forEach { playlist ->
                                SpotifyPlaylistTile(
                                    playlist = playlist,
                                    onClick = { viewModel.importSpotifyPlaylist(playlist.id) },
                                )
                            }
                        }
                    }
                }
            }

            // ─── Скачанное ───────────────────────────────────────────────
            item(key = "library_downloaded_header") {
                SectionHeader(title = stringResource(R.string.library_tab_downloaded))
            }
            if (downloadedTracks.isEmpty()) {
                item(key = "library_downloaded_empty") {
                    DownloadsEmptyCard(onOpenSearch = onOpenSearch)
                }
            } else {
                items(items = downloadedTracks.map { it.toTrack() }, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        isDownloaded = true,
                        artworkSize = 60,
                        artworkCornerDp = 16,
                        onClick = {
                            onTrackClick(track, downloadedTracks.map { it.toTrack() })
                        },
                        onAddToPlaylist = { playlistTrack = track },
                        onArtistClick = { onArtistClick(track.artist) },
                        onPlayNext = { handlePlayNext(track) },
                        onAddToQueue = { handleAddToQueue(track) },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = track }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.library_downloaded_menu,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }

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

    if (showCreateDialog) {
        CreatePlaylistDialog(
            name = newPlaylistName,
            onNameChange = { newPlaylistName = it },
            onConfirm = {
                viewModel.createPlaylist(newPlaylistName)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
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

/** Шапка медиатеки: крупный заголовок и общая подпись коллекции. */
@Composable
private fun LibraryHeader(trackCount: Int, playlistCount: Int) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = CollectionSummary(trackCount = trackCount, playlistCount = playlistCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Подпись чипса «Любимые треки»: количество и суммарная длительность. */
@Composable
private fun likedTracksSubtitle(tracks: List<Track>): String {
    val count = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size)
    val totalMs = tracks.sumOf { it.durationMs }
    return if (totalMs > 0L) "$count · ${formatTotalDuration(totalMs)}" else count
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

/** Пустая секция «Плейлисты»: создание и импорт прямо из медиатеки. */
@Composable
private fun PlaylistsEmptyCard(
    onCreate: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.playlist_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.playlist_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCreate, shape = CircleShape) {
                    Text(stringResource(R.string.playlist_create))
                }
                TextButton(onClick = onImport) {
                    Text(stringResource(R.string.library_import_short))
                }
            }
        }
    }
}

/** Пустая секция «Скачанное»: объясняем, как сохранить музыку офлайн. */
@Composable
private fun DownloadsEmptyCard(onOpenSearch: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.library_downloaded_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.library_downloaded_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenSearch, shape = CircleShape) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.library_find_music))
            }
        }
    }
}

/** Плитка плейлиста Spotify: обложка, название и счётчик треков. */
@Composable
private fun SpotifyPlaylistTile(
    playlist: SpotifyPlaylistSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick),
    ) {
        PlaylistCover(
            name = playlist.name,
            coverUrl = playlist.coverUrl,
            modifier = Modifier.size(132.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralStringResource(
                R.plurals.track_count,
                playlist.trackCount,
                playlist.trackCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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

/** Диалог создания плейлиста из медиатеки. */
@Composable
private fun CreatePlaylistDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_create_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_create_name)) },
                placeholder = { Text(stringResource(R.string.playlist_default_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.playlist_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.playlist_create_cancel))
            }
        },
    )
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

/** Сколько плиток плейлистов в строке сетки медиатеки. */
private const val PLAYLIST_GRID_COLUMNS = 2
