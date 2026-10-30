package com.my.music.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.R
import com.my.music.core.player.RepeatMode
import com.my.music.data.model.Track

/**
 * Шторка очереди воспроизведения (PlayerQueueSheet) по спецификации reference-notes.md:
 * - Высота: ~78% высоты экрана
 * - Скругление: верхние углы 28dp
 * - Handle: 32×4 dp
 * - Подсветка активного трека
 * - Свайп для удаления трека из очереди
 * - Кнопки Shuffle и Repeat в шапке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val sheetHeight = (configuration.screenHeightDp * 0.78f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Автоматическая прокрутка к текущему треку при открытии
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 20.dp),
        ) {
            // ─── Шапка: Заголовок + кнопки Shuffle / Repeat ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.queue_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.track_count, queue.size, queue.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Кнопка Shuffle
                    FilledTonalIconToggleButton(
                        checked = isShuffle,
                        onCheckedChange = { onToggleShuffle() },
                        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.queue_shuffle_cd),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Кнопка Repeat
                    FilledTonalIconButton(
                        onClick = onToggleRepeat,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (repeatMode != RepeatMode.OFF) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (repeatMode != RepeatMode.OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    ) {
                        val icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.queue_repeat_cd),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )

            // ─── Список треков очереди ─────────────────────────────────────────
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = queue,
                        // Стабильный ключ по треку: при удалении элемента его состояние
                        // не «переезжает» на соседний, как было с ключом по индексу
                        key = { _, track -> track.id },
                    ) { index, track ->
                        val isPlaying = index == currentIndex
                        QueueTrackItem(
                            track = track,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(index) },
                            onDismiss = { onRemoveTrack(index) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Элемент очереди с поддержкой свайпа для удаления.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueTrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            val color by animateColorAsState(
                targetValue = if (isDismissing) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "dismissBackground",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                if (isDismissing) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.queue_remove_cd),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
    ) {
        val containerColor = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.large,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Обложка трека
                val context = LocalContext.current
                val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
                val placeholderPainter = remember { ColorPainter(Color(0xFF2A2A34)) }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.artworkUrl)
                            .crossfade(200)
                            .build(),
                        contentDescription = track.title,
                        placeholder = placeholderPainter,
                        error = notePainter,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.queue_playing_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Название и артист
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Длительность
                if (track.durationMs > 0) {
                    val minutes = (track.durationMs / 1000) / 60
                    val seconds = (track.durationMs / 1000) % 60
                    Text(
                        text = "%d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
