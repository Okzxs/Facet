package com.facet.music.feature.artist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.facet.music.core.ui.theme.CloudReveal
import com.facet.music.core.ui.theme.cloudPress
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
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "artist_header") {
                CloudReveal(index = 0) {
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
                        item(key = "artist_popular") {
                            CloudReveal(index = 1) {
                                Column {
                                    SectionHeader(title = stringResource(R.string.artist_popular))
                                    PopularSection(
                                        tracks = state.popular.take(POPULAR_LIMIT),
                                        currentTrackId = currentTrackId,
                                        isPlaying = isPlaying,
                                        onTrackClick = { track -> onTrackClick(track, queue) },
                                        onArtistClick = onTrackArtistClick,
                                    )
                                }
                            }
                        }
                    }

                    if (state.releases.isNotEmpty()) {
                        item(key = "artist_releases_header") {
                            SectionHeader(title = stringResource(R.string.artist_releases))
                        }
                        item(key = "artist_releases_row") {
                            CloudReveal(index = 2) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
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
                    }

                    if (state.allTracks.isNotEmpty()) {
                        item(key = "artist_tracks_header") {
                            SectionHeader(title = stringResource(R.string.artist_all_tracks))
                        }
                        itemsIndexed(
                            items = state.allTracks.take(state.visibleTrackCount),
                            key = { _, track -> track.id },
                        ) { index, track ->
                                                        TrackCard(
                                track = track,
                                isPlaying = track.id == currentTrackId,
                                isPaused = track.id == currentTrackId && !isPlaying,
                                artworkSize = 64,
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
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                    } else {
                                        FilledTonalButton(
                                            onClick = viewModel::showMoreTracks,
                                            shape = MaterialTheme.shapes.extraLarge,
                                            modifier = Modifier.height(44.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.artist_show_more))
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
                            CloudReveal(index = 3) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
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
                    }

                    if (state.related.isNotEmpty()) {
                        item(key = "artist_related_header") {
                            SectionHeader(title = stringResource(R.string.artist_similar_title))
                        }
                        item(key = "artist_related_row") {
                            CloudReveal(index = 4) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp),
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
    val profile = state.profile
    val displayName = state.displayName.ifBlank { stringResource(R.string.artist_title) }
    val backdrop = profile?.bannerUrl ?: profile?.avatarUrl
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!backdrop.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(backdrop)
                            .crossfade(300)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.42f,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                ),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.34f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.22f),
                                1f to MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtistAvatar(profile = profile, name = displayName)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Normal,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    ArtistMeta(profile = profile)
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onPlayAll,
                            enabled = canPlay,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.artist_listen), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onShuffle,
                            enabled = canPlay,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.artist_shuffle), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onWave,
                            enabled = canPlay,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Rounded.Waves, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.artist_wave), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onToggleFollow,
                            enabled = profile != null,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isFollowed) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isFollowed) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = if (isFollowed) Icons.Filled.Check else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
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
    }
}

@Composable
private fun ArtistAvatar(profile: ArtistProfile?, name: String) {
    Surface(
        modifier = Modifier.size(124.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (profile?.avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            TrackArtwork(
                url = profile.avatarUrl,
                contentDescription = stringResource(R.string.artist_avatar_cd, name),
                size = 124,
                cornerDp = 62,
            )
        }
    }
}

@Composable
private fun ArtistMeta(profile: ArtistProfile?) {
    if (profile == null) return
    val listeners = profile.subscriberCount?.takeIf { it > 0 }?.let {
        stringResource(R.string.artist_followers, formatCompactCount(it))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaPill(
            text = if (profile.source == Source.YOUTUBE) "YouTube" else "SoundCloud",
            emphasized = true,
        )
        if (listeners != null) {
            MetaPill(text = listeners, emphasized = false)
        }
    }
}

@Composable
private fun MetaPill(text: String, emphasized: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PopularSection(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    onArtistClick: (Track) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val featured = tracks.take(2)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            featured.forEachIndexed { index, track ->
                PopularBentoCard(
                    position = index + 1,
                    track = track,
                    isCurrent = track.id == currentTrackId,
                    isPaused = track.id == currentTrackId && !isPlaying,
                    onClick = { onTrackClick(track) },
                    onArtistClick = { onArtistClick(track) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        tracks.drop(2).forEachIndexed { index, track ->
            PopularTrackRow(
                position = index + 3,
                track = track,
                isPlaying = track.id == currentTrackId,
                isPaused = track.id == currentTrackId && !isPlaying,
                onClick = { onTrackClick(track) },
                onArtistClick = { onArtistClick(track) },
            )
        }
    }
}

@Composable
private fun PopularBentoCard(
    position: Int,
    track: Track,
    isCurrent: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = position.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (isCurrent) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(modifier = Modifier.padding(7.dp)) {
                            if (isPaused) {
                                com.facet.music.core.ui.components.PausedEqualizerDots()
                            } else {
                                com.facet.music.core.ui.components.PlayingEqualizerIcon()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onArtistClick),
            )
        }
    }
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
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.toString().padStart(2, '0'),
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
            TrackArtwork(
                url = track.artworkUrl,
                contentDescription = stringResource(R.string.track_artwork_cd, track.title),
                size = 56,
                cornerDp = 16,
            )
            Spacer(Modifier.width(14.dp))
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
}

@Composable
private fun ReleaseCard(
    release: ArtistRelease,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(184.dp)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            PlaylistCover(
                name = release.title,
                coverUrl = release.coverUrl,
                modifier = Modifier.fillMaxWidth(),
                corner = 24.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleMedium,
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
}

@Composable
private fun PlaylistCard(
    playlist: ArtistPlaylist,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(184.dp)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            PlaylistCover(
                name = playlist.title,
                coverUrl = playlist.coverUrl,
                modifier = Modifier.fillMaxWidth(),
                corner = 24.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
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
}

@Composable
private fun ArtistBubble(
    artist: Artist,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .width(148.dp)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrackArtwork(
                url = artist.avatarUrl,
                contentDescription = null,
                size = 88,
                cornerDp = 44,
            )
            Spacer(Modifier.height(10.dp))
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.82f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
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
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
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
