package com.facet.music.feature.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.facet.music.R
import com.facet.music.core.import.ImportError
import com.facet.music.core.import.ImportService

/**
 * Импорт из публичного профиля Spotify/SoundCloud и из текстового списка
 * треков. Всё, что ходит в сеть, делает ViewModel; экран только показывает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentError = state.error
    val errorText = if (currentError != null) importErrorText(currentError) else null
    LaunchedEffect(state.message, errorText) {
        val text = state.message ?: errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.tab == ImportTab.PROFILE,
                    onClick = { viewModel.setTab(ImportTab.PROFILE) },
                    label = { Text(stringResource(R.string.import_tab_profile)) },
                )
                FilterChip(
                    selected = state.tab == ImportTab.TEXT,
                    onClick = { viewModel.setTab(ImportTab.TEXT) },
                    label = { Text(stringResource(R.string.import_tab_text)) },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (state.tab) {
                ImportTab.PROFILE -> ProfileImportContent(state, viewModel)
                ImportTab.TEXT -> TextImportContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun ProfileImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                if (state.detectedService == ImportService.SOUNDCLOUD) {
                    R.string.import_profile_hint_soundcloud
                } else {
                    R.string.import_profile_hint_spotify
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.link,
            onValueChange = viewModel::setLink,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.import_profile_field)) },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = viewModel::loadProfile,
            enabled = !state.isLoading && state.link.isNotBlank(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.import_profile_load))
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        // Лайки SoundCloud: публичный client_id из NewPipe, профиль должен быть открыт.
        Text(
            text = stringResource(R.string.import_sc_likes_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.import_sc_likes_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::loadSoundCloudLikes,
            enabled = !state.isLoading && state.link.isNotBlank(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.import_sc_likes_button))
        }

        if (state.likesPreview.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.import_sc_likes_found,
                            state.likesPreview.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.likesPreview.take(5).forEach { track ->
                        Text(
                            text = "${track.artist} — ${track.title}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::importSoundCloudLikes,
                        enabled = !state.isImporting,
                    ) {
                        Text(
                            stringResource(
                                R.string.import_sc_likes_import,
                                state.likesPreview.size,
                            ),
                        )
                    }
                }
            }
        }

        if (state.playlists.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.import_profile_found,
                    state.playlists.size,
                    state.selectedCount,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(items = state.playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = playlist.selected,
                            onCheckedChange = { viewModel.togglePlaylist(playlist.id) },
                        )
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.import_profile_tracks,
                                    playlist.trackCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Button(
                onClick = viewModel::importSelected,
                enabled = !state.isImporting && state.selectedCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    stringResource(
                        R.string.import_profile_import_selected,
                        state.selectedCount,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TextImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.import_text_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(stringResource(R.string.import_text_field)) },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::parseText,
                enabled = !state.isLoading && state.text.isNotBlank(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.import_text_parse))
            }
            Button(
                onClick = viewModel::importText,
                enabled = !state.isImporting && state.textFound.isNotEmpty(),
            ) {
                Text(stringResource(R.string.import_text_import))
            }
        }

        if (state.textQueries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.import_text_summary,
                            state.textFound.size,
                            state.textQueries.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.textMissing > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.import_text_missing,
                                state.textMissing,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Текст ошибки импорта: коды общие для профиля и плейлистов. */
@Composable
private fun importErrorText(error: ImportError): String = when (error) {
    ImportError.EMPTY_LINK -> stringResource(R.string.import_error_empty)
    ImportError.UNSUPPORTED_LINK -> stringResource(R.string.import_error_unsupported)
    ImportError.SPOTIFY_NOT_CONFIGURED -> stringResource(R.string.import_error_spotify_keys)
    ImportError.SOUNDCLOUD_CLIENT_ID_REQUIRED -> stringResource(
        R.string.import_error_soundcloud_client,
    )
    ImportError.EMPTY_PLAYLIST -> stringResource(R.string.import_profile_empty)
    ImportError.SAVE_FAILED -> stringResource(R.string.import_error_save)
    else -> stringResource(R.string.import_error_network)
}
