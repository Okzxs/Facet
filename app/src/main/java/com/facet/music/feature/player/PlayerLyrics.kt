package com.facet.music.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import coil3.compose.AsyncImage
import com.facet.music.R
import com.facet.music.data.model.LyricsResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

internal const val LYRIC_LEAD_MS = 200L
internal const val LYRIC_USER_PAUSE_MS = 4_000L

private val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 400
private const val LYRIC_TOP_PADDING_DP = 40
private const val LYRIC_BOTTOM_PADDING_FRACTION = 0.8f
private val LYRIC_ALPHA = floatArrayOf(1f, 0.74f, 0.6f, 0.52f, 0.46f, 0.4f)
private val LYRIC_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)
private const val LYRIC_BROWSING_ALPHA = 0.8f
private const val LYRIC_INACTIVE_SCALE = 0.98f
private const val LYRIC_PRESSED_SCALE = 0.96f

@Immutable
data class LyricsData(
    val lines: List<String>,
    val timesMs: LongArray,
    val isSynced: Boolean,
    val isInstrumental: Boolean,
    val richLines: List<com.facet.music.data.model.LyricLine> = emptyList(),
    val provider: String = "LRCLIB",
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
        richLines = sorted,
        provider = result.provider,
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
    offsetMs: Int = 0,
    editMode: Boolean = false,
    onSetLineTime: ((Int) -> Unit)? = null,
    lyricScale: Float = 1f,
    lyricBold: Boolean = true,
    lyricBlurEnabled: Boolean = true,
) {
    val synced = lyrics?.isSynced == true && !lyrics.isEmpty
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val lyricTimeMs = rememberLyricClock(
        positionProvider = clock,
        isPlaying = isPlaying && synced,
        resetKey = lyrics,
    )
    val lyricTimeState = rememberUpdatedState(lyricTimeMs)
    val activeIndex = remember(lyrics) {
        derivedStateOf {
            val data = lyrics
            if (data == null || !data.isSynced) {
                -1
            } else {
                data.activeIndexAt(lyricTimeState.value + offsetMs)
            }
        }
    }
    val currentIndex by activeIndex

    var userPaused by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    var hasCentered by remember { mutableStateOf(false) }
    var recenterNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(lyrics) {
        hasCentered = false
        userPaused = false
        if (lyrics != null && !lyrics.isEmpty) listState.scrollToItem(0)
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
        if (!synced || currentIndex < 0 || viewportHeightPx <= 0 || userPaused) return@LaunchedEffect
        val topOffset = with(density) { -(LYRIC_TOP_PADDING_DP.dp.roundToPx()) }
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
        if (!hasCentered) {
            listState.scrollToItem(currentIndex, topOffset)
            hasCentered = true
        } else if (visible == null || abs(visible.offset - topOffset) > 4) {
            autoScrolling = true
            try {
                listState.animateScrollToItem(currentIndex, topOffset)
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
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = highlightColor,
                    )
                    Spacer(Modifier.height(12.dp))
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
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            lyrics != null && !lyrics.isEmpty -> {
                val displayLines = lyrics.richLines.ifEmpty {
                    lyrics.lines.mapIndexed { index, text ->
                        com.facet.music.data.model.LyricLine(
                            timeMs = lyrics.timesMs.getOrElse(index) { 0L },
                            text = text,
                        )
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { onListBoundsChanged(null) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportHeightPx = it.height }
                        .onGloballyPositioned { onListBoundsChanged(it.boundsInRoot()) },
                    contentPadding = PaddingValues(
                        top = LYRIC_TOP_PADDING_DP.dp,
                        bottom = with(density) { viewportHeightPx.toDp() } * LYRIC_BOTTOM_PADDING_FRACTION,
                        start = 24.dp,
                        end = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(
                        items = displayLines,
                        key = { index, _ -> index },
                        contentType = { _, _ -> "lyric-line" },
                    ) { index, line ->
                        val distance = when {
                            !synced -> Int.MAX_VALUE
                            currentIndex < 0 -> 0
                            else -> abs(index - currentIndex)
                        }
                        LyricLineItem(
                            line = line,
                            isSynced = synced,
                            distance = distance,
                            isActive = synced && index == currentIndex,
                            browsing = userPaused,
                            lyricScale = lyricScale,
                            lyricBold = lyricBold,
                            lyricBlurEnabled = lyricBlurEnabled,
                            clock = { clock() + offsetMs },
                            onLongClick = if (editMode && !line.isGap) {
                                { onSetLineTime?.invoke(index) }
                            } else {
                                null
                            },
                            onClick = if (synced && line.timeMs >= 0) {
                                {
                                    onSeekToLine(line.timeMs)
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
                    enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.8f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
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

            else -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricLineItem(
    line: com.facet.music.data.model.LyricLine,
    isSynced: Boolean,
    distance: Int,
    isActive: Boolean,
    browsing: Boolean,
    clock: () -> Long,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    lyricScale: Float = 1f,
    lyricBold: Boolean = true,
    lyricBlurEnabled: Boolean = true,
) {
    if (line.isGap) {
        LyricGapLine(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = onClick != null || onLongClick != null,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                )
                .padding(vertical = 10.dp),
        )
        return
    }
    val step = distance.coerceIn(0, LYRIC_ALPHA.lastIndex)
    val alphaTarget = when {
        !isSynced -> 0.95f
        distance == 0 && !isActive -> 0.95f
        isActive -> 1f
        browsing -> LYRIC_BROWSING_ALPHA
        else -> LYRIC_ALPHA[step]
    }
    val scaleTarget = if (isActive) 1f else LYRIC_INACTIVE_SCALE
    val blurTarget = when {
        !lyricBlurEnabled -> 0.dp
        !isSynced || browsing || isActive || distance == 0 || distance == Int.MAX_VALUE -> 0.dp
        else -> LYRIC_BLUR[step.coerceIn(0, LYRIC_BLUR.lastIndex)]
    }
    val alpha by animateFloatAsState(
        targetValue = alphaTarget,
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricScale",
    )
    val blurRadius by animateDpAsState(
        targetValue = blurTarget,
        animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
        label = "lyricBlur",
    )
    val weight = if (lyricBold) FontWeight.ExtraBold else FontWeight.Medium
    val style = if (isSynced) {
        val size = 34f * lyricScale
        MaterialTheme.typography.headlineLarge.copy(
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
            fontWeight = weight,
            textAlign = TextAlign.Start,
        )
    } else {
        val size = 30f * lyricScale
        MaterialTheme.typography.headlineMedium.copy(
            fontSize = size.sp,
            lineHeight = (size * 1.26f).sp,
            fontWeight = weight,
            textAlign = TextAlign.Start,
        )
    }
    val modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
        .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
        .combinedClickable(
            enabled = onClick != null || onLongClick != null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
        .padding(vertical = 10.dp)
    if (isActive && line.isWordSynced) {
        WordSweptText(
            line = line,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            clock = clock,
            modifier = modifier,
        )
    } else {
        Text(
            text = line.text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = modifier,
        )
    }
}

@Composable
private fun LyricGapLine(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
            )
        }
    }
}

@Composable
private fun WordSweptText(
    line: com.facet.music.data.model.LyricLine,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    clock: () -> Long,
    modifier: Modifier = Modifier,
) {
    var layout by remember(line) { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    var sweep by remember(line) { mutableStateOf(0f) }
    val liftPx = with(LocalDensity.current) { (2.dp * sweep).toPx() }
    LaunchedEffect(line) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { frame ->
                if (frame - lastFrame >= SONG_SWEEP_FRAME_NS) {
                    lastFrame = frame
                    val length = line.text.length.coerceAtLeast(1)
                    sweep = (line.revealedChars(clock()) / length).coerceIn(0f, 1f)
                }
            }
        }
    }
    Column(modifier) {
        Box {
        Text(
            text = line.text,
            style = style,
            color = color.copy(alpha = 0.24f),
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = -liftPx
                    alpha = 0.55f + 0.45f * sweep
                }
                .blur((7.dp + 4.dp * sweep), BlurredEdgeTreatment.Unbounded),
        )
        Text(
            text = line.text,
            style = style,
            color = color.copy(alpha = 0.42f),
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -liftPx },
        )
        Text(
            text = line.text,
            style = style,
            color = color,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -liftPx }
                .drawWithContent {
                    val position = clock()
                    val chars = line.revealedChars(position)
                    val measured = layout
                    if (measured == null || chars <= 0f) return@drawWithContent
                    if (chars >= line.text.length) {
                        drawContent()
                        return@drawWithContent
                    }
                    val textLength = measured.layoutInput.text.length
                    for (visualLine in 0 until measured.lineCount) {
                        val start = measured.getLineStart(visualLine)
                        if (chars <= start) return@drawWithContent
                        val end = measured.getLineEnd(visualLine, visibleEnd = false)
                        val left = measured.getLineLeft(visualLine)
                        val right = measured.getLineRight(visualLine)
                        val top = measured.getLineTop(visualLine)
                        val bottom = measured.getLineBottom(visualLine)
                        val cut = if (chars >= end) {
                            right
                        } else {
                            measured.getHorizontalPosition(
                                chars.toInt().coerceIn(start, (textLength - 1).coerceAtLeast(0)),
                                usePrimaryDirection = true,
                            ).coerceIn(left, right)
                        }
                        if (cut <= left) continue
                        clipRect(left = left, top = top, right = cut, bottom = bottom) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        )
        }
        line.background?.takeIf { it.text.isNotBlank() }?.let { backing ->
            Text(
                text = backing.text,
                style = style.copy(
                    fontSize = style.fontSize * 0.66f,
                    lineHeight = style.lineHeight * 0.72f,
                ),
                color = color.copy(alpha = 0.62f),
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun LyricsForeground(
    lyricsData: LyricsData?,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    highlightColor: Color,
    isPlaying: Boolean,
    playbackClock: () -> Long,
    onSeekToLine: (Long) -> Unit,
    onRetry: () -> Unit,
    onListBoundsChanged: (Rect?) -> Unit,
    offsetMs: Int = 0,
    editMode: Boolean = false,
    onSetLineTime: ((Int) -> Unit)? = null,
    lyricScale: Float = 1f,
    lyricBold: Boolean = true,
    lyricBlurEnabled: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
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
            offsetMs = offsetMs,
            editMode = editMode,
            onSetLineTime = onSetLineTime,
            lyricScale = lyricScale,
            lyricBold = lyricBold,
            lyricBlurEnabled = lyricBlurEnabled,
        )
        if (editMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.player_lyrics_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private const val SONG_SWEEP_FRAME_NS = 33_000_000L
