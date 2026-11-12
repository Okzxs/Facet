package com.facet.music.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.facet.music.R
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.data.model.LyricsResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

internal const val LYRIC_LEAD_MS = 200L
internal const val LYRIC_BAR_SLOT_DP = 12
internal const val LYRIC_IDLE_ALPHA = 0.35f
internal const val LYRIC_USER_PAUSE_MS = 4_000L

@Immutable
data class LyricsData(
    val lines: List<String>,
    val timesMs: LongArray,
    val isSynced: Boolean,
    val isInstrumental: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    fun activeIndexAt(positionMs: Long): Int {
        if (!isSynced || timesMs.isEmpty()) return -1
        val target = positionMs + LYRIC_LEAD_MS
        var lo = 0
        var hi = timesMs.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (timesMs[mid] <= target) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    fun progressAt(index: Int, positionMs: Long): Float {
        if (index !in lines.indices) return 0f
        val start = timesMs[index].coerceAtLeast(0L)
        val end = if (index + 1 < timesMs.size) timesMs[index + 1] else start + 5_000L
        val span = (end - start).coerceAtLeast(1L)
        return ((positionMs - start).toFloat() / span).coerceIn(0f, 1f)
    }
}

fun buildLyricsData(result: LyricsResult): LyricsData {
    val sorted = result.lines
        .asSequence()
        .sortedBy { if (result.isSynced) it.timeMs.coerceAtLeast(0L) else 0L }
        .toList()
    return LyricsData(
        lines = sorted.map { it.text.ifBlank { "♪" } },
        timesMs = LongArray(sorted.size) { sorted[it].timeMs.coerceAtLeast(0L) },
        isSynced = result.isSynced,
        isInstrumental = result.isInstrumental,
    )
}

@Composable
internal fun LyricsListView(
    lyrics: LyricsData?,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    isPlaying: Boolean,
    clock: () -> Long,
    highlightColor: Color,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
    onListBoundsChanged: (Rect?) -> Unit,
) {
    val synced = lyrics?.isSynced == true && !lyrics.isEmpty

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val barSlotPx = with(LocalDensity.current) { LYRIC_BAR_SLOT_DP.dp.roundToPx() }
    val timeState = remember { mutableLongStateOf(0L) }
    val fillState = remember { mutableFloatStateOf(0f) }

    val activeIndex = remember(lyrics) {
        derivedStateOf {
            if (lyrics == null || !lyrics.isSynced) -1 else lyrics.activeIndexAt(timeState.longValue)
        }
    }
    val currentIndex by activeIndex

    LaunchedEffect(synced, isPlaying) {
        if (!synced || !isPlaying) {
            timeState.longValue = clock()
            fillState.floatValue = 0f
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis {
                val position = clock()
                timeState.longValue = position
                val index = lyrics?.activeIndexAt(position) ?: -1
                fillState.floatValue = lyrics?.progressAt(index, position) ?: 0f
            }
        }
    }

    var userPaused by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    var recenterNonce by remember { mutableIntStateOf(0) }
    var hasCentered by remember { mutableStateOf(false) }

    // Смена трека: список и центрирование не должны тянуться из прошлого трека.
    LaunchedEffect(lyrics) {
        hasCentered = false
        userPaused = false
        if (lyrics != null && !lyrics.isEmpty) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !autoScrolling) userPaused = true
            }
    }

    LaunchedEffect(userPaused) {
        if (userPaused) {
            delay(LYRIC_USER_PAUSE_MS)
            userPaused = false
        }
    }

    LaunchedEffect(currentIndex, userPaused, recenterNonce, viewportHeightPx) {
        if (currentIndex < 0 || viewportHeightPx <= 0 || userPaused) return@LaunchedEffect
        // Активная строка — ровно по центру: offset «полвьюпорта минус полстроки».
        // animateScrollToItem сам доводит до цели, накопительной ошибки нет.
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        val scrollOffset = -(viewportHeightPx / 2 - (info?.size ?: barSlotPx) / 2)
        if (!hasCentered) {
            listState.scrollToItem(index = currentIndex, scrollOffset = scrollOffset)
            hasCentered = true
        } else {
            autoScrolling = true
            try {
                listState.animateScrollToItem(index = currentIndex, scrollOffset = scrollOffset)
            } finally {
                autoScrolling = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.07f to Color.Black,
                        0.87f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = highlightColor,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.player_lyrics_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lyrics?.isInstrumental == true -> {
                Text(
                    stringResource(R.string.player_lyrics_instrumental),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyrics != null && !lyrics.isEmpty -> {
                DisposableEffect(Unit) {
                    onDispose { onListBoundsChanged(null) }
                }
                Box {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewportHeightPx = it.height }
                            .onGloballyPositioned { onListBoundsChanged(it.boundsInRoot()) },
                        contentPadding = PaddingValues(
                            top = if (synced) 64.dp else 28.dp,
                            bottom = 136.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(
                            items = lyrics.lines,
                            key = { index, _ -> index },
                            contentType = { _, _ -> "lyric-line" },
                        ) { index, text ->
                            val distance = if (synced && currentIndex >= 0) {
                                abs(index - currentIndex)
                            } else {
                                Int.MAX_VALUE
                            }
                            LyricLineItem(
                                text = text,
                                isSynced = synced,
                                distance = distance,
                                isActive = synced && index == currentIndex,
                                highlightColor = highlightColor,
                                fill = { fillState.floatValue },
                                onClick = if (synced && lyrics.timesMs[index] >= 0) {
                                    {
                                        onSeekToLine(lyrics.timesMs[index])
                                        userPaused = false
                                        recenterNonce++
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = synced && userPaused && currentIndex >= 0,
                        enter = fadeIn(Motion.effectsFast()) +
                            scaleIn(initialScale = 0.8f, animationSpec = Motion.spatialDefault()),
                        exit = fadeOut(Motion.effectsFast()) +
                            scaleOut(targetScale = 0.8f, animationSpec = Motion.spatialDefault()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 150.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                userPaused = false
                                recenterNonce++
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = stringResource(R.string.player_lyrics_to_current_cd),
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = error ?: stringResource(R.string.player_lyrics_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.player_lyrics_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    text: String,
    isSynced: Boolean,
    distance: Int,
    isActive: Boolean,
    highlightColor: Color,
    fill: () -> Float,
    onClick: (() -> Unit)?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val animates = !isSynced || distance <= 2
    val targetAlpha = when {
        !isSynced -> 0.85f
        isActive -> 1f
        distance == 1 -> 0.6f
        distance == 2 -> 0.45f
        else -> LYRIC_IDLE_ALPHA
    }
    val targetScale = if (isActive) 1.04f else 1f

    val alpha = remember { Animatable(targetAlpha) }
    val scale = remember { Animatable(targetScale) }

    LaunchedEffect(animates, targetAlpha) {
        if (animates) {
            alpha.animateTo(targetAlpha, Motion.effectsDefault())
        } else {
            alpha.snapTo(targetAlpha)
        }
    }
    LaunchedEffect(animates, targetScale) {
        if (animates) {
            scale.animateTo(targetScale, Motion.spatialDefault())
        } else {
            scale.snapTo(targetScale)
        }
    }

    val lineStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Medium,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick?.invoke() },
            )
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = lineStyle,
            color = onSurface,
            textAlign = TextAlign.Center,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LYRIC_BAR_SLOT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .clip(PillShape)
                    .graphicsLayer { this.alpha = if (isActive) 1f else 0f }
                    .drawBehind {
                        drawRect(outlineVariant.copy(alpha = 0.4f))
                        drawRect(
                            color = highlightColor,
                            size = Size(size.width * fill(), size.height),
                        )
                    },
            )
        }
    }
}

/**
 * Чистый экран текстов поверх «утопленной» размытой обложки:
 * без стеклянных панелей — только парящая шапка трека и строки, растворяющиеся в фоне.
 */
@Composable
internal fun LyricsForeground(
    title: String,
    artist: String,
    artworkUrl: String?,
    lyricsData: LyricsData?,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    highlightColor: Color,
    notePainter: Painter,
    placeholderPainter: Painter,
    isPlaying: Boolean,
    playbackClock: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
    onListBoundsChanged: (Rect?) -> Unit,
) {
    val miniShape = MaterialTheme.shapes.medium
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Парящая шапка: миниатюра и подпись без подложки
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(miniShape),
            ) {
                ArtworkImage(
                    artworkUrl = artworkUrl,
                    shape = miniShape,
                    notePainter = notePainter,
                    placeholderPainter = placeholderPainter,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.44f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Строки скользят под мягкими градиентными масками, не упираясь в контролы.
        // Маски — это DstIn-затухание самого контента, а не серая плёнка surface:
        // на светлой обложке цветная подложка давала видимый прямоугольник.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LyricsListView(
                lyrics = lyricsData,
                isLoading = isLoading,
                error = error,
                listState = listState,
                isPlaying = isPlaying,
                clock = playbackClock,
                highlightColor = highlightColor,
                onSeekToLine = onSeekToLine,
                onRetry = onRetry,
                onListBoundsChanged = onListBoundsChanged,
            )
        }

        // Воздух до острова управления: тексты не налезают на язычок и волну
        Spacer(Modifier.height(16.dp))
    }
}
