package com.facet.music.feature.library

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facet.music.R
import com.facet.music.core.download.DownloadState
import com.facet.music.core.ui.components.AddToPlaylistSheet
import com.facet.music.core.ui.components.EmptyState
import com.facet.music.core.ui.components.TrackCard
import com.facet.music.core.ui.components.TrackDownloadButton
import com.facet.music.data.model.Track
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedTracksScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenSearch: () -> Unit,
    onArtistClick: (Track) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    bottomInset: Dp = 0.dp,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val likeError by viewModel.likeError.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var playlistTrack by remember { mutableStateOf<Track?>(null) }

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
                    Text(
                        text = stringResource(R.string.library_favorites),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "liked_hero") {
                LikedHero(tracks = likedTracks)
            }
            if (likedTracks.isNotEmpty()) {
                item(key = "liked_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onTrackClick(likedTracks.first(), likedTracks) },
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
                        FilledTonalIconButton(
                            onClick = {
                                val shuffled = likedTracks.shuffled()
                                onTrackClick(shuffled.first(), shuffled)
                            },
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
                items(items = likedTracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        isDownloaded = track.id in downloadedIds,
                        isPlaying = track.id == currentTrackId,
                        isPaused = track.id == currentTrackId && !isPlaying,
                        artworkSize = 60,
                        artworkCornerDp = 16,
                        onClick = { onTrackClick(track, likedTracks) },
                        onAddToPlaylist = { playlistTrack = track },
                        onArtistClick = { onArtistClick(track) },
                        onPlayNext = { handlePlayNext(track) },
                        onAddToQueue = { handleAddToQueue(track) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TrackDownloadButton(
                                    state = downloadStates[track.id] ?: DownloadState.NotDownloaded,
                                    onClick = { viewModel.downloadTrack(track) },
                                )
                                IconButton(onClick = { viewModel.removeLike(track) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = stringResource(
                                            R.string.library_remove_like,
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                    )
                }
            } else {
                item(key = "liked_empty") {
                    EmptyState(
                        icon = Icons.Filled.Favorite,
                        title = stringResource(R.string.library_empty_title),
                        message = stringResource(R.string.library_empty_message),
                        actionLabel = stringResource(R.string.library_find_music),
                        actionIcon = Icons.Rounded.Search,
                        onAction = onOpenSearch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

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

@Composable
private fun LikedHero(tracks: List<Track>) {
    val start = MaterialTheme.colorScheme.primaryContainer
    val end = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val totalMs = tracks.sumOf { it.durationMs }
    val countLabel = pluralStringResource(R.plurals.track_count, tracks.size, tracks.size)
    val durationLabel = if (totalMs > 0L) formatLikedDuration(totalMs) else null

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(start, end))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.library_favorites),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (durationLabel != null) "$countLabel · $durationLabel" else countLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatLikedDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}
