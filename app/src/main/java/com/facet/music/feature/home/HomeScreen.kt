@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.facet.music.feature.home

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.home.SmartCollections
import com.facet.music.core.ui.theme.CloudReveal
import com.facet.music.core.ui.theme.cloudPress
import com.facet.music.core.ui.theme.cloudCardMotion
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.source.external.DeezerRelease
import com.facet.music.navigation.LocalBottomDockHeight
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private val HomePadding = 16.dp
private val HomeGap = 24.dp

@Composable
fun HomeScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onSettingsClick: () -> Unit,
    onWaveClick: () -> Unit,
    sections: List<String> = AppSettings.DEFAULT_HOME_SECTION_ORDER,
    heroStyle: String = AppSettings.HERO_EDITORIAL,
    showWavePromo: Boolean = true,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentTracks = uiState.recentTracks
    val heroTrack = recentTracks.firstOrNull()
    val recentIds = remember(recentTracks) { recentTracks.map { it.id }.toSet() }
    val freshLikes = remember(uiState.likedTracks) {
        SmartCollections.freshLikes(uiState.likedTracks)
    }
    val forgotten = remember(uiState.likedTracks, recentIds, freshLikes) {
        SmartCollections.forgotten(
            liked = uiState.likedTracks,
            recentIds = recentIds,
            excludeIds = freshLikes.map { it.id }.toSet(),
        )
    }
    val orderedSections = remember(
        sections,
        heroTrack,
        showWavePromo,
        uiState.newReleases,
        uiState.charts,
        uiState.genreSections,
        recentTracks,
        uiState.memories,
        freshLikes,
        forgotten,
    ) {
        sections.filter { section ->
            section != AppSettings.SECTION_WAVE && when (section) {
                AppSettings.SECTION_HERO -> heroTrack != null
                AppSettings.SECTION_NEW_RELEASES ->
                    uiState.isExtrasLoading || uiState.newReleases.isNotEmpty()
                AppSettings.SECTION_CHARTS ->
                    uiState.isExtrasLoading || uiState.charts.isNotEmpty()
                AppSettings.SECTION_GENRES -> uiState.genreSections.isNotEmpty()
                AppSettings.SECTION_RECOMMENDATIONS -> true
                AppSettings.SECTION_RECENT -> recentTracks.isNotEmpty()
                AppSettings.SECTION_MEMORIES -> uiState.memories.isNotEmpty()
                AppSettings.SECTION_FRESH_LIKES -> freshLikes.isNotEmpty()
                AppSettings.SECTION_FORGOTTEN -> forgotten.isNotEmpty()
                else -> false
            }
        }
    }
    val homeListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.openPlayer.collect { (track, queue) -> onTrackClick(track, queue) }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HomePadding,
                end = HomePadding,
                top = 24.dp,
                bottom = 120.dp + LocalBottomDockHeight.current,
            ),
            verticalArrangement = Arrangement.spacedBy(HomeGap),
        ) {
            item(key = "cloud_header") {
                CloudReveal(index = 0) {
                    CloudHomeHeader(onSettingsClick = onSettingsClick)
                }
            }

            if (showWavePromo) {
                item(key = "wave_hero") {
                    CloudReveal(index = 1) {
                        CloudWaveHero(onClick = onWaveClick)
                    }
                }
            }

            orderedSections.forEachIndexed { index, section ->
                item(key = "cloud_section_$section") {
                    androidx.compose.runtime.key(section) {
                        when (section) {
                            AppSettings.SECTION_HERO -> heroTrack?.let { track ->
                                CloudContinueCard(
                                    track = track,
                                    queue = recentTracks,
                                    onClick = { onTrackClick(track, recentTracks) },
                                    compact = heroStyle == AppSettings.HERO_MINIMAL,
                                )
                            }

                            AppSettings.SECTION_CHARTS -> CloudChartsBlock(
                                tracks = uiState.charts,
                                isLoading = uiState.isExtrasLoading,
                                onTrackClick = onTrackClick,
                                index = index + 1,
                                listState = homeListState,
                                sectionKey = section,
                            )

                            AppSettings.SECTION_NEW_RELEASES -> CloudReleasesBlock(
                                releases = uiState.newReleases,
                                isLoading = uiState.isExtrasLoading,
                                onClick = viewModel::playRelease,
                                index = index + 1,
                                listState = homeListState,
                                sectionKey = section,
                            )

                            AppSettings.SECTION_GENRES -> CloudGenresBlock(
                                sections = uiState.genreSections,
                                onTrackClick = onTrackClick,
                                index = index + 1,
                            )

                            AppSettings.SECTION_RECOMMENDATIONS -> CloudRecommendationsBlock(
                                tracks = uiState.recommendations,
                                isLoading = uiState.isRecommendationsLoading,
                                error = uiState.recommendationsError,
                                onTrackClick = onTrackClick,
                                onRetry = viewModel::refresh,
                                index = index + 1,
                            )

                            AppSettings.SECTION_RECENT -> CloudTracksBlock(
                                title = stringResource(R.string.home_recent),
                                tracks = recentTracks,
                                onTrackClick = onTrackClick,
                                index = index + 1,
                                sectionKey = section,
                            )

                            AppSettings.SECTION_MEMORIES -> CloudTracksBlock(
                                title = stringResource(R.string.home_memories),
                                tracks = uiState.memories.map { it.track },
                                onTrackClick = onTrackClick,
                                index = index + 1,
                                sectionKey = section,
                            )

                            AppSettings.SECTION_FRESH_LIKES -> CloudTracksBlock(
                                title = stringResource(R.string.home_fresh_likes),
                                tracks = freshLikes,
                                onTrackClick = onTrackClick,
                                index = index + 1,
                                sectionKey = section,
                            )

                            AppSettings.SECTION_FORGOTTEN -> CloudTracksBlock(
                                title = stringResource(R.string.home_forgotten),
                                tracks = forgotten,
                                onTrackClick = onTrackClick,
                                index = index + 1,
                                sectionKey = section,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudHomeHeader(onSettingsClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val dateLabel = remember(locale) {
        SimpleDateFormat("EEEE, d MMMM", locale)
            .format(Date())
            .replaceFirstChar { it.uppercase(locale) }
    }
    val greeting = stringResource(
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..17 -> R.string.home_greeting_day
            in 18..22 -> R.string.home_greeting_evening
            else -> R.string.home_greeting_night
        },
    )

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FACET",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.4.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = dateLabel.uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.home_settings_cd),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CloudWaveHero(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .cloudPress(interactionSource),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WavePattern(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Waves,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = onClick,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.home_wave_title),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.home_wave_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.home_wave_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WavePattern(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val base = size.height * 0.58f
        val amplitude = size.height * 0.11f
        repeat(3) { wave ->
            val path = Path()
            val step = 12f
            var x = 0f
            while (x <= size.width) {
                val y = base + wave * 26.dp.toPx() +
                    sin((x / size.width) * PI * 3.2 + wave * 1.1).toFloat() * amplitude
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun CloudContinueCard(
    track: Track,
    queue: List<Track>,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val height = if (compact) 104.dp else 128.dp
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .cloudPress(interactionSource),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloudArtwork(
                url = track.artworkUrl,
                modifier = Modifier.size(if (compact) 72.dp else 88.dp),
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_continue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_continue),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun CloudChartsBlock(
    tracks: List<Track>,
    isLoading: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    index: Int,
    listState: LazyListState,
    sectionKey: String,
) {
    Column {
        CloudSectionHeader(
            title = stringResource(R.string.home_charts),
            index = index,
            count = tracks.size,
        )
        Spacer(Modifier.height(12.dp))
        when {
            tracks.isNotEmpty() -> {
                val pages = tracks.chunked(CHART_PAGE_SIZE)
                val chartRowState = rememberLazyListState()
                LazyRow(
                    state = chartRowState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(288.dp),
                ) {
                    items(pages.size) { page ->
                        Column(
                            modifier = Modifier.fillParentMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            pages[page].forEachIndexed { position, track ->
                                ChartRow(
                                    rank = page * CHART_PAGE_SIZE + position + 1,
                                    track = track,
                                    onClick = { onTrackClick(track, tracks) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pages.size) { dot ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (chartRowState.firstVisibleItemIndex == dot) 14.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (chartRowState.firstVisibleItemIndex == dot) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    },
                                ),
                        )
                    }
                }
            }
            isLoading -> CloudSkeletonRow()
        }
    }
}

@Composable
private fun ChartRow(
    rank: Int,
    track: Track,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(MaterialTheme.shapes.large)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp),
        )
        CloudArtwork(
            url = track.artworkUrl,
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
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
}

private const val CHART_PAGE_SIZE = 5

@Composable
private fun CloudReleasesBlock(
    releases: List<DeezerRelease>,
    isLoading: Boolean,
    onClick: (DeezerRelease) -> Unit,
    index: Int,
    listState: LazyListState,
    sectionKey: String,
) {
    Column {
        CloudSectionHeader(
            title = stringResource(R.string.home_new_releases),
            index = index,
            count = releases.size,
        )
        Spacer(Modifier.height(16.dp))
        when {
            releases.isNotEmpty() -> {
                val rowState = rememberLazyListState()
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(items = releases, key = { _, item -> item.id }) { i, release ->
                        CloudReleaseCard(
                            release = release,
                            width = 160.dp,
                            state = rowState,
                            onClick = { onClick(release) },
                        )
                    }
                }
            }
            isLoading -> CloudSkeletonRow()
        }
    }
}

@Composable
private fun CloudReleaseCard(
    release: DeezerRelease,
    width: Dp,
    state: LazyListState,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(width)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            CloudArtwork(
                url = release.coverUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = release.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CloudGenresBlock(
    sections: List<GenreSection>,
    onTrackClick: (Track, List<Track>) -> Unit,
    index: Int,
) {
    Column {
        CloudSectionHeader(
            title = stringResource(R.string.home_genre_playlists),
            index = index,
            count = sections.size,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            sections.chunked(2).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { section ->
                        val track = section.tracks.firstOrNull() ?: return@forEach
                        CloudGenreCard(
                            section = section,
                            height = if (rowIndex == 0) 132.dp else 112.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { onTrackClick(track, section.tracks) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CloudGenreCard(
    section: GenreSection,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .cloudPress(interactionSource),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = section.genre,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.track_count,
                        section.tracks.size,
                        section.tracks.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CloudRecommendationsBlock(
    tracks: List<Track>,
    isLoading: Boolean,
    error: String?,
    onTrackClick: (Track, List<Track>) -> Unit,
    onRetry: () -> Unit,
    index: Int,
) {
    Column {
        CloudSectionHeader(
            title = stringResource(R.string.home_for_you),
            index = index,
            count = tracks.size,
        )
        Spacer(Modifier.height(16.dp))
        when {
            tracks.isNotEmpty() -> CloudTrackRow(
                tracks = tracks,
                onTrackClick = onTrackClick,
                showRank = false,
            )
            isLoading -> CloudSkeletonRow()
            else -> CloudMessageCard(
                title = stringResource(
                    if (error != null) R.string.home_recs_error else R.string.home_recs_empty,
                ),
                message = error ?: stringResource(R.string.home_recs_empty_hint),
                actionLabel = stringResource(
                    if (error != null) R.string.home_retry else R.string.home_refresh,
                ),
                onAction = onRetry,
            )
        }
    }
}

@Composable
private fun CloudTracksBlock(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
    index: Int,
    sectionKey: String,
) {
    Column {
        CloudSectionHeader(title = title, index = index, count = tracks.size)
        Spacer(Modifier.height(16.dp))
        CloudTrackRow(
            tracks = tracks,
            onTrackClick = onTrackClick,
            showRank = false,
        )
    }
}

@Composable
private fun CloudTrackRow(
    tracks: List<Track>,
    onTrackClick: (Track, List<Track>) -> Unit,
    showRank: Boolean,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            CloudTrackCard(
                track = track,
                rank = if (showRank) index + 1 else null,
                width = 160.dp,
                state = rowState,
                onClick = { onTrackClick(track, tracks) },
            )
        }
    }
}

@Composable
private fun CloudTrackCard(
    track: Track,
    rank: Int?,
    width: Dp,
    state: LazyListState,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(width)
            .cloudPress(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(MaterialTheme.shapes.extraLarge),
        ) {
            CloudArtwork(
                url = track.artworkUrl,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.84f),
                        ),
                    ),
            )
            if (rank != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = rank.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CloudSectionHeader(
    title: String,
    index: Int,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CloudSkeletonRow() {
    val transition = rememberInfiniteTransition(label = "cloudSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloudSkeletonAlpha",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(160.dp, 160.dp).forEach { width ->
            Box(
                modifier = Modifier
                    .width(width)
                    .aspectRatio(0.82f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                    ),
            )
        }
    }
}

@Composable
private fun CloudMessageCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onAction,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun CloudArtwork(
    url: String?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val note = rememberVectorPainter(Icons.Rounded.MusicNote)
    val request = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context)
                .data(it)

                .build()
        }
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center,
    ) {
        if (request == null) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = note,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
