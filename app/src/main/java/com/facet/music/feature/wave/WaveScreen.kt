@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.facet.music.feature.wave

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.facet.music.BuildConfig
import com.facet.music.R
import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.ui.components.AppLoadingIndicator
import com.facet.music.core.ui.components.EmptyState
import com.facet.music.core.ui.components.formatDuration
import com.facet.music.core.ui.theme.CloudReveal
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.core.ui.theme.cloudPress
import com.facet.music.core.wave.WaveCandidateDetails
import com.facet.music.core.wave.WaveExplanation
import com.facet.music.core.wave.WaveMood
import com.facet.music.core.wave.WaveReason
import com.facet.music.core.wave.WaveReasonType
import com.facet.music.data.model.Track
import com.facet.music.navigation.LocalBottomDockHeight

private val WaveScreenPadding = 24.dp
private val WaveGridGap = 14.dp

@Composable
fun WaveScreen(
    onArtistClick: (Track) -> Unit,
    viewModel: WaveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalBottomDockHeight.current
    val haptic = LocalHapticFeedback.current
    var debugTrack by remember { mutableStateOf<Track?>(null) }

    val tracks = uiState.tracks
    val hasTracks = tracks.isNotEmpty()
    val rows = remember(tracks) { tracks.drop(1).chunked(2) }
    val hasMeta = hasTracks &&
        (
            uiState.genres.isNotEmpty() ||
                uiState.fromSimilar > 0 ||
                uiState.fromLikes > 0 ||
                uiState.fromHistory > 0 ||
                uiState.fromGenres > 0
            )

    val onStartWave: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.generateWave()
    }
    val onRestartWave: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.refreshWave()
    }
    val onHeroPlay: () -> Unit = {
        if (tracks.isEmpty()) {
            onStartWave()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.playTrack(tracks.first())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = WaveScreenPadding,
                end = WaveScreenPadding,
                top = topInset + 16.dp,
                bottom = 128.dp + bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(WaveGridGap),
        ) {
            item(key = "wave_hero") {
                CloudReveal(index = 0) {
                    WaveHero(
                        mood = uiState.mood,
                        trackCount = tracks.size,
                        isGenerating = uiState.isGenerating,
                        onPlay = onHeroPlay,
                        onRestart = onRestartWave,
                    )
                }
            }

            item(key = "wave_mood") {
                CloudReveal(index = 1) {
                    WaveMoodSection(
                        mood = uiState.mood,
                        onMood = viewModel::setMood,
                    )
                }
            }

            if (hasMeta) {
                item(key = "wave_meta") {
                    CloudReveal(index = 2) {
                        WaveMetaRow(
                            genres = uiState.genres,
                            fromSimilar = uiState.fromSimilar,
                            fromLikes = uiState.fromLikes,
                            fromHistory = uiState.fromHistory,
                            fromGenres = uiState.fromGenres,
                        )
                    }
                }
            }

            uiState.error?.let { message ->
                item(key = "wave_error") {
                    CloudReveal(index = 3) {
                        WaveStatusBanner(
                            message = message,
                            actionLabel = stringResource(R.string.home_retry),
                            onAction = onRestartWave,
                            icon = Icons.Rounded.ErrorOutline,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            if (uiState.isGenerating) {
                item(key = "wave_loading") {
                    CloudReveal(index = 2) {
                        WaveGeneratingPanel(modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }

            if (!uiState.isGenerating && !hasTracks && uiState.error == null) {
                item(key = "wave_empty") {
                    CloudReveal(index = 2) {
                        EmptyState(
                            icon = Icons.Rounded.Waves,
                            title = stringResource(R.string.wave_title),
                            message = stringResource(R.string.wave_empty_hint),
                            actionLabel = stringResource(R.string.wave_start),
                            actionIcon = Icons.Rounded.PlayArrow,
                            onAction = onStartWave,
                        )
                    }
                }
            }

            if (hasTracks) {
                item(key = "wave_queue_header") {
                    CloudReveal(index = 3) {
                        WaveQueueHeader(
                            count = tracks.size,
                            isGenerating = uiState.isGenerating,
                            onReset = viewModel::resetWave,
                        )
                    }
                }

                item(key = "wave_wide") {
                    CloudReveal(index = 4) {
                        WaveTrackCard(
                            track = tracks.first(),
                            index = 0,
                            hero = true,
                            debugEnabled = BuildConfig.DEBUG,
                            onClick = { viewModel.playTrack(tracks.first()) },
                            onLongClick = { debugTrack = tracks.first() },
                            onArtistClick = { onArtistClick(tracks.first()) },
                            onExplain = { viewModel.openExplanation(tracks.first()) },
                        )
                    }
                }

                itemsIndexed(
                    items = rows,
                    key = { index, row ->
                        "wave_row_${index}_${row.joinToString("|") { it.id }}"
                    },
                ) { rowIndex, row ->
                    WaveReveal(index = 5 + rowIndex) {
                        WaveBentoRow(
                            tracks = row,
                            startIndex = 1 + rowIndex * 2,
                            debugEnabled = BuildConfig.DEBUG,
                            onPlay = viewModel::playTrack,
                            onLongClick = { debugTrack = it },
                            onArtistClick = onArtistClick,
                            onExplain = viewModel::openExplanation,
                        )
                    }
                }

                if (uiState.isExhausted) {
                    item(key = "wave_exhausted") {
                        WaveStatusBanner(
                            message = stringResource(R.string.wave_ended),
                            actionLabel = stringResource(R.string.wave_new),
                            onAction = onRestartWave,
                            icon = Icons.Rounded.Waves,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            debugTrack?.let { track ->
                CandidateDebugDialog(
                    track = track,
                    details = uiState.details[trackKey(track)],
                    onDismiss = { debugTrack = null },
                )
            }
        }

        val explainTrack = uiState.explanationTrack
        if (explainTrack != null) {
            WaveExplanationDialog(
                track = explainTrack,
                explanation = uiState.explanation,
                isLoading = uiState.isExplanationLoading,
                onMoreLikeThis = viewModel::moreLikeThis,
                onLessLikeThis = viewModel::lessLikeThis,
                onBlockArtist = viewModel::blockFromExplanation,
                onDismiss = viewModel::dismissExplanation,
            )
        }

        if (uiState.showColdStart) {
            ColdStartDialog(
                artists = uiState.coldStartArtists,
                selected = uiState.selectedColdStartArtists,
                loading = uiState.isColdStartLoading,
                onToggle = viewModel::toggleColdStartArtist,
                onConfirm = viewModel::confirmColdStart,
                onDismiss = viewModel::dismissColdStart,
            )
        }
    }
}

@Composable
private fun WaveReveal(
    index: Int,
    content: @Composable () -> Unit,
) {
    if (index <= 6) {
        CloudReveal(index = index) { content() }
    } else {
        content()
    }
}

private fun trackKey(track: Track): String =
    MusicNormalizer.trackKey(track.artist, track.title)

@Composable
private fun WaveHero(
    mood: WaveMood,
    trackCount: Int,
    isGenerating: Boolean,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
) {
    val hasTracks = trackCount > 0
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val playLabel = stringResource(
        when {
            isGenerating -> R.string.wave_generating
            hasTracks -> R.string.common_play_cd
            else -> R.string.wave_start
        },
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            WaveCloudBackdrop(modifier = Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "FACET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.2.sp,
                        ),
                        color = primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .background(primary.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = moodIcon(mood),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = moodLabel(mood),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    text = stringResource(R.string.wave_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.wave_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(132.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.12f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(primary)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(color = onPrimary),
                                    enabled = !isGenerating,
                                    onClick = onPlay,
                                )
                                .cloudPress(interactionSource),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = onPrimary,
                                    strokeWidth = 3.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = playLabel,
                                    tint = onPrimary,
                                    modifier = Modifier.size(52.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playLabel,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasTracks) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.track_count,
                                    trackCount,
                                    trackCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hasTracks && !isGenerating) {
                            Spacer(Modifier.height(12.dp))
                            WavePillAction(
                                icon = Icons.Rounded.Refresh,
                                label = stringResource(R.string.wave_new),
                                onClick = onRestart,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveCloudBackdrop(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val puff = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(modifier = modifier) {
        val topCenter = Offset(size.width * 0.84f, size.height * 0.10f)
        val topRadius = size.width * 0.58f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.26f), Color.Transparent),
                center = topCenter,
                radius = topRadius,
            ),
            radius = topRadius,
            center = topCenter,
        )

        val leftCenter = Offset(size.width * 0.06f, size.height * 0.52f)
        val leftRadius = size.width * 0.44f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.20f), Color.Transparent),
                center = leftCenter,
                radius = leftRadius,
            ),
            radius = leftRadius,
            center = leftCenter,
        )

        val rightCenter = Offset(size.width * 0.92f, size.height * 0.74f)
        val rightRadius = size.width * 0.42f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.16f), Color.Transparent),
                center = rightCenter,
                radius = rightRadius,
            ),
            radius = rightRadius,
            center = rightCenter,
        )

        drawCircle(
            color = puff.copy(alpha = 0.44f),
            radius = size.width * 0.26f,
            center = Offset(size.width * 0.10f, size.height * 0.94f),
        )
        drawCircle(
            color = puff.copy(alpha = 0.32f),
            radius = size.width * 0.20f,
            center = Offset(size.width * 0.34f, size.height * 1.02f),
        )
        drawCircle(
            color = puff.copy(alpha = 0.24f),
            radius = size.width * 0.18f,
            center = Offset(size.width * 0.86f, size.height * 0.98f),
        )
    }
}

@Composable
private fun WaveMoodSection(
    mood: WaveMood,
    onMood: (WaveMood) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "01",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.wave_mood_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items = WaveMood.entries, key = { it.name }) { item ->
                WaveMoodChip(
                    mood = item,
                    selected = mood == item,
                    onClick = { onMood(item) },
                )
            }
        }
    }
}

@Composable
private fun WaveMoodChip(
    mood: WaveMood,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = Motion.bounce<Float>(),
        label = "waveMoodBounce",
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .cloudPress(interactionSource),
        shape = PillShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = moodIcon(mood),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = moodLabel(mood),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WaveMetaRow(
    genres: List<String>,
    fromSimilar: Int,
    fromLikes: Int,
    fromHistory: Int,
    fromGenres: Int,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = genres.distinctBy { it.lowercase() }.take(3),
            key = { "genre_$it" },
        ) { genre ->
            WaveMetaPill(
                icon = Icons.Rounded.MusicNote,
                label = genre.replaceFirstChar { it.uppercase() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fromSimilar > 0) {
            item(key = "meta_similar") {
                WaveMetaPill(
                    icon = Icons.Rounded.Waves,
                    label = stringResource(R.string.wave_from_similar, fromSimilar),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (fromLikes > 0) {
            item(key = "meta_likes") {
                WaveMetaPill(
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.wave_from_likes, fromLikes),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (fromHistory > 0) {
            item(key = "meta_history") {
                WaveMetaPill(
                    icon = Icons.Rounded.History,
                    label = stringResource(R.string.wave_from_history, fromHistory),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (fromGenres > 0) {
            item(key = "meta_discoveries") {
                WaveMetaPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.wave_from_genres, fromGenres),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WaveMetaPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WaveQueueHeader(
    count: Int,
    isGenerating: Boolean,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "02",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.wave_in_wave),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        WaveCountPill(count = count)
        Spacer(Modifier.width(8.dp))
        WavePillAction(
            icon = Icons.Rounded.Refresh,
            label = stringResource(R.string.wave_reset),
            onClick = onReset,
            enabled = !isGenerating,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveCountPill(count: Int) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun WavePillAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.cloudPress(interactionSource),
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WaveBentoRow(
    tracks: List<Track>,
    startIndex: Int,
    debugEnabled: Boolean,
    onPlay: (Track) -> Unit,
    onLongClick: (Track) -> Unit,
    onArtistClick: (Track) -> Unit,
    onExplain: (Track) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaveGridGap),
    ) {
        tracks.forEachIndexed { offset, track ->
            WaveTrackCard(
                track = track,
                index = startIndex + offset,
                hero = false,
                debugEnabled = debugEnabled,
                onClick = { onPlay(track) },
                onLongClick = { onLongClick(track) },
                onArtistClick = { onArtistClick(track) },
                onExplain = { onExplain(track) },
                modifier = Modifier.weight(1f),
            )
        }
        if (tracks.size == 1) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WaveTrackCard(
    track: Track,
    index: Int,
    hero: Boolean,
    debugEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArtistClick: () -> Unit,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = if (hero) {
        MaterialTheme.shapes.extraLarge
    } else {
        MaterialTheme.shapes.large
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .cloudPress(interactionSource)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = stringResource(R.string.common_play_cd),
                    onLongClickLabel = if (debugEnabled) {
                        stringResource(R.string.track_actions_cd)
                    } else {
                        null
                    },
                    onLongClick = if (debugEnabled) onLongClick else null,
                    onClick = onClick,
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            if (hero) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaveBentoArtwork(
                        track = track,
                        index = index,
                        onMenuClick = { menuExpanded = true },
                        onPlayClick = onClick,
                        modifier = Modifier.size(132.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onArtistClick),
                        )
                        Spacer(Modifier.height(12.dp))
                        WaveMetaChip(
                            text = buildString {
                                if (track.durationMs > 0) append(formatDuration(track.durationMs))
                                if (!track.genre.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(track.genre)
                                }
                            },
                        )
                    }
                }
            } else {
                Column {
                    WaveBentoArtwork(
                        track = track,
                        index = index,
                        onMenuClick = { menuExpanded = true },
                        onPlayClick = onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(onClick = onArtistClick),
                        )
                        Spacer(Modifier.height(8.dp))
                        WaveMetaChip(
                            text = if (track.durationMs > 0) formatDuration(track.durationMs) else "",
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_play_cd)) },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onClick()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.wave_explain_title)) },
                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onExplain()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.artist_open)) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onArtistClick()
                },
            )
        }
    }
}

@Composable
private fun WaveBentoArtwork(
    track: Track,
    index: Int,
    onMenuClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(30.dp),
            )
        }
        if (!track.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = stringResource(R.string.track_artwork_cd, track.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.track_actions_cd),
                modifier = Modifier.size(18.dp),
            )
        }
        FilledIconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.common_play_cd),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun WaveMetaChip(text: String) {
    if (text.isBlank()) return
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WaveGeneratingPanel(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveSkeleton")
    val skeletonAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveSkeletonAlpha",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLoadingIndicator(size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.wave_generating),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.graphicsLayer { alpha = skeletonAlpha },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(WaveGridGap)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(118.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
                Spacer(Modifier.height(WaveGridGap))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
    }
}

@Composable
private fun WaveStatusBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                onClick = onAction,
                shape = PillShape,
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
                tonalElevation = 0.dp,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ColdStartDialog(
    artists: List<String>,
    selected: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.wave_cold_start_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.wave_cold_start_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator(size = 28.dp)
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        artists.take(24).forEach { artist ->
                            FilterChip(
                                selected = artist in selected,
                                onClick = { onToggle(artist) },
                                label = { Text(artist) },
                                shape = PillShape,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selected.size in 3..5,
                shape = PillShape,
            ) {
                Text(stringResource(R.string.wave_cold_start_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun CandidateDebugDialog(
    track: Track,
    details: WaveCandidateDetails?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (details == null) {
                    Text(stringResource(R.string.wave_debug_empty))
                } else {
                    DebugRow("origin", details.origin.name)
                    DebugRow("seed", details.seedKey ?: "—")
                    DebugRow("score", "%.3f".format(details.score))
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DebugRow("artist", "%.3f".format(details.artistAffinity))
                    DebugRow("genre", "%.3f".format(details.genreAffinity))
                    DebugRow("origin prior", "%.3f".format(details.originPrior))
                    DebugRow("novelty", "%.3f".format(details.novelty))
                    DebugRow("seed weight", "%.3f".format(details.seedWeight))
                    DebugRow("popularity", "%.3f".format(details.popularity))
                    DebugRow("penalty", "%.3f".format(details.penalty))
                    DebugRow("noise", "%.3f".format(details.noise))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WaveExplanationDialog(
    track: Track,
    explanation: WaveExplanation?,
    isLoading: Boolean,
    onMoreLikeThis: () -> Unit,
    onLessLikeThis: () -> Unit,
    onBlockArtist: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.wave_explain_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator(size = 28.dp)
                    }

                    explanation == null || explanation.reasons.isEmpty() -> Text(
                        text = stringResource(R.string.wave_explain_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        explanation.reasons.forEach { reason ->
                            WaveReasonCard(reason = reason)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onMoreLikeThis,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PillShape,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wave_more_like))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLessLikeThis,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PillShape,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wave_less_like))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onBlockArtist,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.wave_block_artist),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun WaveReasonCard(reason: WaveReason) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = waveReasonText(reason),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun waveReasonText(reason: WaveReason): String = when (reason.type) {
    WaveReasonType.SEED_TRACK -> stringResource(
        R.string.wave_reason_seed_track,
        reason.value.orEmpty(),
    )

    WaveReasonType.SEED_ARTIST -> stringResource(
        R.string.wave_reason_seed_artist,
        reason.value.orEmpty(),
    )

    WaveReasonType.SIMILAR_ARTIST -> stringResource(
        R.string.wave_reason_similar_artist,
        reason.value.orEmpty(),
    )

    WaveReasonType.GENRE -> stringResource(R.string.wave_reason_genre, reason.value.orEmpty())
    WaveReasonType.NOVELTY -> stringResource(R.string.wave_reason_novelty)
    WaveReasonType.FAMILIAR -> stringResource(R.string.wave_reason_familiar)
    WaveReasonType.POPULAR -> stringResource(R.string.wave_reason_popular)
    WaveReasonType.FOLLOWED -> stringResource(R.string.wave_reason_followed)
    WaveReasonType.SECOND_HOP -> stringResource(R.string.wave_reason_second_hop)
    WaveReasonType.PENALTY -> stringResource(R.string.wave_reason_penalty, reason.value.orEmpty())
}

@Composable
private fun moodLabel(mood: WaveMood): String = stringResource(
    when (mood) {
        WaveMood.FAMILIAR -> R.string.wave_mood_familiar
        WaveMood.BALANCE -> R.string.wave_mood_balance
        WaveMood.DISCOVERY -> R.string.wave_mood_discovery
    },
)

private fun moodIcon(mood: WaveMood): ImageVector = when (mood) {
    WaveMood.FAMILIAR -> Icons.Rounded.Favorite
    WaveMood.BALANCE -> Icons.Rounded.Waves
    WaveMood.DISCOVERY -> Icons.Rounded.AutoAwesome
}
