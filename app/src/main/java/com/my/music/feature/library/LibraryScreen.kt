package com.my.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.core.download.DownloadState
import com.my.music.core.ui.components.TrackCard
import com.my.music.core.ui.components.TrackDownloadButton
import com.my.music.data.model.Track
import com.my.music.navigation.LocalBottomDockHeight

/**
 * Экран медиатеки: любимые треки с быстрыми действиями,
 * подсветкой играющего трека и пустым состоянием с переходом в поиск.
 */
@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val likeError by viewModel.likeError.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val isPlayerPlaying by viewModel.isPlaying.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadError) {
        val message = downloadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeDownloadError()
    }

    LaunchedEffect(likeError) {
        val message = likeError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeLikeError()
    }

    Scaffold(
        // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Статус-бар обеспечивает сам экран (корневого Scaffold больше нет);
        // низ — через contentPadding списка с запасом под плавающий док.
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LibraryHeader(
                trackCount = likedTracks.size,
                totalMs = likedTracks.sumOf { it.durationMs },
            )

            if (likedTracks.isEmpty()) {
                LibraryEmptyState(
                    onOpenSearch = onOpenSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalBottomDockHeight.current),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Быстрые кнопки: «Слушать всё» шире «Перемешать» (1.6 к 1.0)
                    item(key = "library_actions") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    onTrackClick(likedTracks.first(), likedTracks)
                                },
                                modifier = Modifier
                                    .weight(1.6f)
                                    .height(52.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Слушать всё")
                            }

                            FilledTonalButton(
                                onClick = {
                                    val shuffled = likedTracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Перемешать")
                            }
                        }
                    }

                    items(
                        items = likedTracks,
                        key = { it.id },
                    ) { track ->
                        TrackCard(
                            track = track,
                            isDownloaded = track.id in downloadedIds,
                            isPlaying = track.id == currentTrackId,
                            isPaused = track.id == currentTrackId && !isPlayerPlaying,
                            artworkSize = 60,
                            artworkCornerDp = 16,
                            onClick = { onTrackClick(track, likedTracks) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TrackDownloadButton(
                                        state = downloadStates[track.id]
                                            ?: DownloadState.NotDownloaded,
                                        onClick = { viewModel.downloadTrack(track) },
                                    )
                                    IconButton(onClick = { viewModel.removeLike(track) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = "Удалить из любимых",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Шапка медиатеки: eyebrow «КОЛЛЕКЦИЯ», крупный заголовок и подпись
 * с количеством треков и суммарной длительностью (плюрализация русская).
 */
@Composable
private fun LibraryHeader(trackCount: Int, totalMs: Long) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Text(
            text = "Медиатека",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            // Для пустой коллекции не показываем «0 мин» — только количество.
            text = if (totalMs > 0L) {
                "${pluralTracks(trackCount)} · ${formatTotalDuration(totalMs)}"
            } else {
                pluralTracks(trackCount)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Пустое состояние: squircle-плашка, заголовок, подсказка и выход в поиск. */
@Composable
private fun LibraryEmptyState(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "В медиатеке пока пусто",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Нажимайте сердечко в плеере или в списках треков, " +
                    "чтобы собирать свою коллекцию любимой музыки",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onOpenSearch,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Найти музыку")
            }
        }
    }
}

/** Русская плюрализация: 1 трек, 2 трека, 5 треков. */
private fun pluralTracks(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "трек"
        mod10 in 2..4 && (mod100 < 12 || mod100 > 14) -> "трека"
        else -> "треков"
    }
    return "$count $word"
}

/** Суммарная длительность коллекции: «1 ч 5 мин» или «42 мин». */
private fun formatTotalDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}
