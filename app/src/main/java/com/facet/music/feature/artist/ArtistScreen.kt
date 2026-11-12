package com.facet.music.feature.artist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.ui.components.AppLoadingIndicator
import com.facet.music.core.ui.components.EmptyState
import com.facet.music.core.ui.components.PlaylistCover
import com.facet.music.core.ui.components.SectionHeader
import com.facet.music.core.ui.components.TrackArtwork
import com.facet.music.core.ui.components.TrackCard
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistProfile
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.coroutines.launch
import java.util.Locale

private const val POPULAR_LIMIT = 10
private const val RELEASE_CARD_SIZE = 160
private const val ARTIST_BUBBLE_SIZE = 96

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onTrackArtistClick: (Track) -> Unit,
    onWaveClick: (String) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val followedKeys by viewModel.followedKeys.collectAsState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isFollowed = state.profile?.key?.let { it in followedKeys } == true
    val collapsed by remember {
        derivedStateOf { scrollBehavior.state.collapsedFraction > 0.55f }
    }

    val queue = (state.popular + state.allTracks).distinctBy { it.id }
    val canPlay = queue.isNotEmpty()

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
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "artist_header") {
                ArtistHero(
                    state = state,
                    isFollowed = isFollowed,
                    canPlay = canPlay,
                    onPlayAll = {
                        queue.firstOrNull()?.let { first -> onTrackClick(first, queue) }
                    },
                    onShuffle = {
                        val shuffled = queue.shuffled()
                        shuffled.firstOrNull()?.let { first -> onTrackClick(first, shuffled) }
                    },
                    onWave = {
                        state.profile?.name
                            ?.takeIf { it.isNotBlank() }
                            ?.let(onWaveClick)
                    },
                    onToggleFollow = viewModel::toggleFollow,
                )
            }

            when {
                state.isLoading && state.profile == null -> item(key = "artist_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator()
                    }
                }

                state.failed && state.profile == null -> item(key = "artist_failed") {
                    EmptyState(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.artist_load_failed),
                        message = stringResource(R.string.artist_load_failed_hint),
                        actionLabel = stringResource(R.string.artist_retry),
                        onAction = viewModel::retry,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    if (state.popular.isNotEmpty()) {
                        item(key = "artist_popular_header") {
                            SectionHeader(title = stringResource(R.string.artist_popular))
                        }
                        itemsIndexed(
                            items = state.popular.take(POPULAR_LIMIT),
                            key = { _, track -> "popular_${track.id}" },
                        ) { index, track ->
                            PopularTrackRow(
                                position = index + 1,
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                isPaused = track.id == currentTrackId && !isPlaying,
                                onClick = { onTrackClick(track, queue) },
                                onArtistClick = { onTrackArtistClick(track) },
                            )
                        }
                    }

                    if (state.releases.isNotEmpty()) {
                        item(key = "artist_releases_header") {
                            SectionHeader(title = stringResource(R.string.artist_releases))
                        }
                        item(key = "artist_releases_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.releases, key = { it.url }) { release ->
                                    ReleaseCard(
                                        release = release,
                                        onClick = { viewModel.openRelease(release) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.allTracks.isNotEmpty()) {
                        item(key = "artist_tracks_header") {
                            SectionHeader(title = stringResource(R.string.artist_all_tracks))
                        }
                        items(
                            state.allTracks.take(state.visibleTrackCount),
                            key = { it.id },
                        ) { track ->
                            TrackCard(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                isPaused = track.id == currentTrackId && !isPlaying,
                                artworkSize = 56,
                                onClick = { onTrackClick(track, state.allTracks) },
                                onArtistClick = { onTrackArtistClick(track) },
                                onPlayNext = { handlePlayNext(track) },
                                onAddToQueue = { handleAddToQueue(track) },
                            )
                        }
                        if (state.visibleTrackCount < state.allTracks.size || state.hasMore) {
                            item(key = "artist_tracks_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        FilledTonalIconButton(
                                            onClick = viewModel::showMoreTracks,
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ExpandMore,
                                                contentDescription = stringResource(
                                                    R.string.artist_show_more,
                                                ),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.playlists.isNotEmpty()) {
                        item(key = "artist_playlists_header") {
                            SectionHeader(title = stringResource(R.string.artist_playlists))
                        }
                        item(key = "artist_playlists_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.playlists, key = { it.url }) { playlist ->
                                    PlaylistCard(
                                        playlist = playlist,
                                        onClick = {
                                            viewModel.openRelease(
                                                ArtistRelease(
                                                    id = playlist.id,
                                                    title = playlist.title,
                                                    coverUrl = playlist.coverUrl,
                                                    url = playlist.url,
                                                    trackCount = playlist.trackCount,
                                                    isAlbum = false,
                                                ),
                                            )
                                        },
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
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.related, key = { "${it.source}:${it.url}" }) { artist ->
                                    ArtistBubble(
                                        artist = artist,
                                        onClick = { onArtistClick(artist) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.popular.isEmpty() && state.allTracks.isEmpty()) {
                        item(key = "artist_empty") {
                            EmptyState(
                                icon = Icons.Rounded.MusicNote,
                                title = stringResource(R.string.artist_no_tracks),
                                message = stringResource(R.string.artist_load_failed_hint),
                                actionLabel = stringResource(R.string.artist_retry),
                                onAction = viewModel::retry,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        LargeTopAppBar(
            title = {
                if (collapsed) {
                    Text(
                        text = state.displayName.ifBlank { stringResource(R.string.artist_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                Surface(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                ) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                }
            },
            actions = {
                if (state.profile != null) {
                    androidx.compose.material3.IconButton(onClick = viewModel::toggleFollow) {
                        Icon(
                            imageVector = if (isFollowed) Icons.Filled.Check else Icons.Rounded.Add,
                            contentDescription = stringResource(
                                if (isFollowed) R.string.artist_following else R.string.artist_follow,
                            ),
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    if (state.selectedReleaseTitle != null) {
        ReleaseTracksSheet(
            title = state.selectedReleaseTitle.orEmpty(),
            tracks = state.releaseTracks,
            isLoading = state.isReleaseLoading,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onDismiss = viewModel::closeRelease,
            onTrackClick = { track ->
                onTrackClick(track, state.releaseTracks)
                viewModel.closeRelease()
            },
        )
    }
}

@Composable
private fun ArtistHero(
    state: ArtistUiState,
    isFollowed: Boolean,
    canPlay: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onWave: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val profile = state.profile
    val displayName = state.displayName.ifBlank { stringResource(R.string.artist_title) }
    val backdrop = profile?.bannerUrl ?: profile?.avatarUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp),
    ) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdrop)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp),
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.32f to background.copy(alpha = 0.32f),
                        0.72f to background.copy(alpha = 0.9f),
                        1f to background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtistAvatar(profile = profile, name = displayName)
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            ArtistMeta(profile = profile)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPlayAll,
                    enabled = canPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.artist_listen), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onShuffle,
                    enabled = canPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.artist_shuffle), maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onWave,
                    enabled = canPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.artist_wave), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onToggleFollow,
                    enabled = profile != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (isFollowed) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                ) {
                    Icon(
                        imageVector = if (isFollowed) Icons.Filled.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (isFollowed) R.string.artist_following else R.string.artist_follow,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAvatar(profile: ArtistProfile?, name: String) {
    Surface(
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    ) {
        TrackArtwork(
            url = profile?.avatarUrl,
            contentDescription = stringResource(R.string.artist_avatar_cd, name),
            size = 120,
            cornerDp = 60,
        )
    }
}

@Composable
private fun ArtistMeta(profile: ArtistProfile?) {
    if (profile == null) return
    val listeners = profile.subscriberCount?.takeIf { it > 0 }?.let {
        stringResource(R.string.artist_followers, formatCompactCount(it))
    }
    val stats = listOfNotNull(
        if (profile.source == Source.YOUTUBE) "YouTube" else "SoundCloud",
        listeners,
    )
    Text(
        text = stats.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PopularTrackRow(
    position: Int,
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPlaying) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )
        TrackArtwork(
            url = track.artworkUrl,
            contentDescription = stringResource(R.string.track_artwork_cd, track.title),
            size = 52,
            cornerDp = 12,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onArtistClick),
            )
        }
        if (isPlaying) {
            com.facet.music.core.ui.components.PlayingEqualizerIcon()
        } else if (isPaused) {
            com.facet.music.core.ui.components.PausedEqualizerDots()
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.common_play_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ArtistRelease,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RELEASE_CARD_SIZE.dp)
            .clickable(onClick = onClick),
    ) {
        PlaylistCover(
            name = release.title,
            coverUrl = release.coverUrl,
            modifier = Modifier.size(RELEASE_CARD_SIZE.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = release.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val countLabel = release.trackCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.track_count, it, it)
        }
        Text(
            text = listOfNotNull(release.year, countLabel).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: ArtistPlaylist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(RELEASE_CARD_SIZE.dp)
            .clickable(onClick = onClick),
    ) {
        PlaylistCover(
            name = playlist.title,
            coverUrl = playlist.coverUrl,
            modifier = Modifier.size(RELEASE_CARD_SIZE.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playlist.trackCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.track_count,
                    playlist.trackCount,
                    playlist.trackCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArtistBubble(
    artist: Artist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(ARTIST_BUBBLE_SIZE.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(
            url = artist.avatarUrl,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseTracksSheet(
    title: String,
    tracks: List<Track>,
    isLoading: Boolean,
    currentTrackId: String?,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onTrackClick: (Track) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.82f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppLoadingIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        TrackCard(
                            track = track,
                            isPlaying = track.id == currentTrackId,
                            isPaused = track.id == currentTrackId && !isPlaying,
                            artworkSize = 52,
                            onClick = { onTrackClick(track) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.US, "%.0fK", value / 1_000f)
    else -> value.toString()
}
