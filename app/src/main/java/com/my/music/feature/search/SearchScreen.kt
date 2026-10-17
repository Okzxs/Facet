package com.my.music.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.core.ui.components.TrackCard
import com.my.music.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onTrackClick: (track: Track, queue: List<Track>) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {

    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ─── Строка поиска ───────────────────────────────────────────────
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Треки, артисты…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )

            // ─── Тело ────────────────────────────────────────────────────────
            when {
                state.isLoading -> {
                    // Скелетон-плейсхолдеры карточек вместо голого спиннера
                    SearchSkeletonList()
                }

                state.error != null -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⚠️ ${state.error}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.onQueryChange(state.query) }) {
                                Text("Повторить")
                            }
                        }
                    }
                }

                state.results.isEmpty() && state.query.length >= 2 -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Введи название трека или артиста",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Минимум 2 символа",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                else -> {
                    // ─── Список результатов ──────────────────────────────────
                    Text(
                        text = "Результаты · SoundCloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = state.results,
                            key = { it.id },
                        ) { track ->
                            TrackCard(
                                track = track,
                                onClick = { onTrackClick(track, state.results) },
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Скелетон списка карточек при поиске.
 * Предотвращает скачки интерфейса и заменяет голый спиннер.
 */
@Composable
private fun SearchSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Обложка-скелетон
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Полоска названия
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                    Spacer(Modifier.height(8.dp))
                    // Полоска артиста
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    ) {}
                }
            }
        }
    }
}
