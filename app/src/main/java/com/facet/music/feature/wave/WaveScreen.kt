package com.facet.music.feature.wave

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facet.music.BuildConfig
import com.facet.music.R
import com.facet.music.core.ui.components.AppLoadingIndicator
import com.facet.music.core.ui.components.TrackArtwork
import com.facet.music.core.wave.WaveCandidateDetails
import com.facet.music.core.wave.WaveMood
import com.facet.music.data.model.Track
import com.facet.music.navigation.LocalBottomDockHeight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class WaveSpec(
    val heightFraction: Float,
    val amplitudeDp: Float,
    val frequency: Float,
    val phaseOffset: Float,
    val color: Color,
    val strokeDp: Float,
)

@Composable
fun WaveScreen(
    onArtistClick: (Track) -> Unit,
    viewModel: WaveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Инсеты ставим сами: корневого Scaffold больше нет. Низ — запас под плавающий док.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalBottomDockHeight.current
    val haptic = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    var debugTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enter = fadeIn(tween(420)) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessLow,
            ),
        ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset, bottom = 28.dp + bottomInset),
        ) {
            item(key = "wave_header") {
                WaveHeader()
            }

            item(key = "wave_hero") {
                WaveHero(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp)
                        .padding(horizontal = 20.dp),
                )
            }

            item(key = "wave_action") {
                WaveActionButton(
                    isGenerating = uiState.isGenerating,
                    hasTracks = uiState.tracks.isNotEmpty(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.refreshWave()
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }

            uiState.error?.let { message ->
                item(key = "wave_error") {
                    WaveMessageCard(
                        message = message,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            if (uiState.tracks.isNotEmpty()) {
                item(key = "wave_mood") {
                    WaveMoodSelector(
                        mood = uiState.mood,
                        onMood = viewModel::setMood,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }

                item(key = "wave_meta") {
                    WaveMetaBadges(
                        genres = uiState.genres,
                        onReset = viewModel::resetWave,
                    )
                }

                item(key = "wave_breakdown") {
                    WaveBreakdown(
                        fromLikes = uiState.fromLikes,
                        fromHistory = uiState.fromHistory,
                        fromGenres = uiState.fromGenres,
                        fromSimilar = uiState.fromSimilar,
                    )
                }

                if (uiState.isExhausted) {
                    item(key = "wave_exhausted") {
                        Text(
                            text = stringResource(R.string.wave_ended),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp,
                            ),
                        )
                    }
                }

                item(key = "wave_list_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.wave_in_wave),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.track_count,
                                uiState.tracks.size,
                                uiState.tracks.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(
                    items = uiState.tracks,
                    key = { _, track -> track.id },
                ) { index, track ->
                    WaveTrackRow(
                        index = index,
                        track = track,
                        onClick = { viewModel.playTrack(track) },
                        onLongClick = if (BuildConfig.DEBUG) {
                            { debugTrack = track }
                        } else {
                            null
                        },
                        onArtistClick = { onArtistClick(track) },
                    )
                }
            } else if (!uiState.isGenerating && uiState.error == null) {
                item(key = "wave_hint") {
                    WaveHintCard(modifier = Modifier.padding(horizontal = 20.dp))
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

private fun trackKey(track: Track): String =
    com.facet.music.core.text.MusicNormalizer.trackKey(track.artist, track.title)

@Composable
private fun WaveHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.wave_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.wave_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveHero(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val outlineColor = MaterialTheme.colorScheme.onPrimaryContainer

    val waves = remember(primary, tertiary, secondary) {
        listOf(
            WaveSpec(
                heightFraction = 0.60f,
                amplitudeDp = 16f,
                frequency = 1.15f,
                phaseOffset = 0f,
                color = primary.copy(alpha = 0.85f),
                strokeDp = 2.5f,
            ),
            WaveSpec(
                heightFraction = 0.73f,
                amplitudeDp = 22f,
                frequency = 0.85f,
                phaseOffset = 1.7f,
                color = tertiary.copy(alpha = 0.55f),
                strokeDp = 2f,
            ),
            WaveSpec(
                heightFraction = 0.84f,
                amplitudeDp = 12f,
                frequency = 1.55f,
                phaseOffset = 3.1f,
                color = secondary.copy(alpha = 0.45f),
                strokeDp = 1.6f,
            ),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveMotion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wavePulse",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.17f * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 2.8f,
                ),
                radius = baseRadius * 2.8f,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryContainer,
                        primary.copy(alpha = 0.72f),
                    ),
                    center = Offset(center.x - baseRadius * 0.28f, center.y - baseRadius * 0.32f),
                    radius = baseRadius * 1.6f,
                ),
                radius = baseRadius,
                center = center,
            )

            drawCircle(
                color = outlineColor.copy(alpha = 0.30f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            drawCircle(
                color = primary.copy(alpha = 0.16f),
                radius = baseRadius * 1.36f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            waves.forEach { spec ->
                val path = Path()
                val waveY = size.height * spec.heightFraction
                val amplitude = spec.amplitudeDp.dp.toPx() * (0.75f + 0.25f * pulse)
                val step = 5f
                var x = 0f
                var first = true
                while (x <= size.width) {
                    val progress = x / size.width
                    val envelope = sin(progress * PI).toFloat()
                    val y = waveY + sin(progress * 2f * PI.toFloat() * spec.frequency + phase + spec.phaseOffset) *
                        amplitude * envelope
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += step
                }
                drawPath(
                    path = path,
                    color = spec.color,
                    style = Stroke(
                        width = spec.strokeDp.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            repeat(8) { index ->
                val angle = phase * 0.35f + index * (2f * PI.toFloat() / 8f)
                val radius = baseRadius * (1.55f + 0.18f * (index % 3))
                val particle = Offset(
                    x = center.x + cos(angle) * radius,
                    y = center.y + sin(angle) * radius * 0.55f,
                )
                drawCircle(
                    color = if (index % 2 == 0) primary.copy(alpha = 0.45f)
                    else tertiary.copy(alpha = 0.38f),
                    radius = 2.4.dp.toPx(),
                    center = particle,
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.Waves,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun WaveActionButton(
    isGenerating: Boolean,
    hasTracks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val shape = MaterialTheme.shapes.extraLarge
    val brush = remember(primary, tertiary) {
        Brush.linearGradient(listOf(primary, tertiary))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveButton")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveButtonGlow",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "waveButtonScale",
    )

    val glowAlpha = if (isGenerating) 0.38f + glow * 0.25f else 0.14f + glow * 0.18f
    val glowScale = 1f + glow * 0.04f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                    alpha = glowAlpha
                }
                .clip(shape)
                .background(brush),
        )

        Button(
            onClick = onClick,
            enabled = !isGenerating,
            interactionSource = interactionSource,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = onPrimary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .background(brush),
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    isGenerating -> stringResource(R.string.wave_generating)
                    hasTracks -> stringResource(R.string.wave_new)
                    else -> stringResource(R.string.wave_start)
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 0.2.sp,
                ),
            )
        }
    }
}

@Composable
private fun WaveMoodSelector(
    mood: WaveMood,
    onMood: (WaveMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wave_mood_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WaveMood.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = mood == item,
                    onClick = { onMood(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, WaveMood.entries.size),
                    label = {
                        Text(
                            text = stringResource(
                                when (item) {
                                    WaveMood.FAMILIAR -> R.string.wave_mood_familiar
                                    WaveMood.BALANCE -> R.string.wave_mood_balance
                                    WaveMood.DISCOVERY -> R.string.wave_mood_discovery
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun WaveMetaBadges(
    genres: List<String>,
    onReset: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.distinctBy { it.lowercase() }.take(3).forEach { genre ->
            item(key = "meta_genre_$genre") {
                BreakdownChip(
                    icon = Icons.Rounded.MusicNote,
                    label = genre.replaceFirstChar { it.uppercase() },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "meta_reset") {
            Surface(
                onClick = onReset,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wave_reset),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveBreakdown(
    fromLikes: Int,
    fromHistory: Int,
    fromGenres: Int,
    fromSimilar: Int,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (fromSimilar > 0) {
            item(key = "breakdown_similar") {
                BreakdownChip(
                    icon = Icons.Rounded.Waves,
                    label = stringResource(R.string.wave_from_similar, fromSimilar),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (fromLikes > 0) {
            item(key = "breakdown_likes") {
                BreakdownChip(
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.wave_from_likes, fromLikes),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (fromHistory > 0) {
            item(key = "breakdown_history") {
                BreakdownChip(
                    icon = Icons.Rounded.History,
                    label = stringResource(R.string.wave_from_history, fromHistory),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (fromGenres > 0) {
            item(key = "breakdown_genres") {
                BreakdownChip(
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
private fun BreakdownChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaveTrackRow(
    index: Int,
    track: Track,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onArtistClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "waveRowScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 160),
        label = "waveRowColor",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            TrackArtwork(
                url = track.artworkUrl,
                contentDescription = stringResource(R.string.track_artwork_cd, track.title),
                size = 52,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
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
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.common_play_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
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
        title = { Text(stringResource(R.string.wave_cold_start_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.wave_cold_start_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (loading) {
                    AppLoadingIndicator(
                        size = 24.dp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    artists.take(24).forEach { artist ->
                        FilterChip(
                            selected = artist in selected,
                            onClick = { onToggle(artist) },
                            label = { Text(artist) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selected.size in 3..5,
            ) {
                Text(stringResource(R.string.wave_cold_start_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.textButtonColors()) {
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
        title = { Text(track.title) },
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
            Button(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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
private fun WaveHintCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = stringResource(R.string.wave_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WaveMessageCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
