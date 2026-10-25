package com.my.music.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.core.download.DownloadState
import com.my.music.core.ui.components.TrackCard
import com.my.music.core.ui.components.TrackDownloadButton
import com.my.music.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val likedTracks by viewModel.likedTracks.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadError) {
        val message = downloadError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        viewModel.consumeDownloadError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Медиатека",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "${likedTracks.size} треков в коллекции",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (likedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Нажимайте сердечко в плеере или в списках треков, чтобы собирать свою коллекцию любимой музыки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Быстрые кнопки: «Слушать всё» и «Перемешать»
                item(key = "library_actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                if (likedTracks.isNotEmpty()) {
                                    onTrackClick(likedTracks.first(), likedTracks)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Слушать всё")
                        }

                        FilledTonalButton(
                            onClick = {
                                if (likedTracks.isNotEmpty()) {
                                    val shuffled = likedTracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
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
