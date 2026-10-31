package com.facet.music.feature.imports

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
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
 * треков. Экран собран из MD3-карточек: профиль, лайки SoundCloud и плейлисты
 * идут отдельными блоками, а не одной сплошной формой.
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

    // Итог импорта лайков: 0 — все треки уже были в избранном.
    val likesDone = state.likesImported
    val likesDoneText = likesDone?.let { count ->
        if (count > 0) {
            stringResource(R.string.import_sc_likes_done, count)
        } else {
            stringResource(R.string.import_sc_likes_duplicates)
        }
    }
    LaunchedEffect(likesDoneText) {
        val text = likesDoneText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeLikesImported()
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
                .padding(padding),
        ) {
            TabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = state.tab == ImportTab.PROFILE,
                    onClick = { viewModel.setTab(ImportTab.PROFILE) },
                    text = { Text(stringResource(R.string.import_tab_profile)) },
                )
                Tab(
                    selected = state.tab == ImportTab.TEXT,
                    onClick = { viewModel.setTab(ImportTab.TEXT) },
                    text = { Text(stringResource(R.string.import_tab_text)) },
                )
            }

            when (state.tab) {
                ImportTab.PROFILE -> ProfileImportContent(state, viewModel)
                ImportTab.TEXT -> TextImportContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun ProfileImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "profile_link") {
            ImportCard(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.import_profile_card_title),
                subtitle = stringResource(R.string.import_profile_hint),
            ) {
                OutlinedTextField(
                    value = state.link,
                    onValueChange = viewModel::setLink,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.import_profile_field)) },
                    placeholder = { Text(stringResource(R.string.import_profile_placeholder)) },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::loadProfile,
                    enabled = !state.isLoading && state.link.isNotBlank(),
                    shape = CircleShape,
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.import_profile_load))
                }
            }
        }

        // Лайки SoundCloud: только для SoundCloud-ссылки или имени пользователя.
        // Для Spotify этот блок не нужен и только путал.
        if (state.detectedService != ImportService.SPOTIFY) {
            item(key = "sc_likes") {
                ImportCard(
                    icon = Icons.Rounded.Favorite,
                    title = stringResource(R.string.import_sc_likes_title),
                    subtitle = stringResource(R.string.import_sc_likes_hint),
                ) {
                    Button(
                        onClick = viewModel::loadSoundCloudLikes,
                        enabled = !state.isLoading && state.link.isNotBlank(),
                        shape = CircleShape,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.import_sc_likes_button))
                    }

                    val likesError = likesErrorText(state)
                    if (likesError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = likesError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    if (state.likesPreview.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                R.string.import_sc_likes_found,
                                state.likesPreview.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        state.likesPreview.take(PREVIEW_TRACK_COUNT).forEach { track ->
                            Text(
                                text = "${track.artist} — ${track.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::importSoundCloudLikes,
                            enabled = !state.isImporting,
                            shape = CircleShape,
                        ) {
                            if (state.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
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
        }

        if (state.playlists.isNotEmpty()) {
            item(key = "profile_playlists") {
                ImportCard(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(
                        R.string.import_profile_found,
                        state.playlists.size,
                        state.selectedCount,
                    ),
                ) {
                    state.playlists.forEach { playlist ->
                        ProfilePlaylistRow(
                            playlist = playlist,
                            onToggle = { viewModel.togglePlaylist(playlist.id) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::importSelected,
                        enabled = !state.isImporting && state.selectedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
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
    }
}

@Composable
private fun TextImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "text_input") {
            ImportCard(
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                title = stringResource(R.string.import_text_card_title),
                subtitle = stringResource(R.string.import_text_hint),
            ) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    label = { Text(stringResource(R.string.import_text_field)) },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::parseText,
                        enabled = !state.isLoading && state.text.isNotBlank(),
                        shape = CircleShape,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.import_text_parse))
                    }
                    Button(
                        onClick = viewModel::importText,
                        enabled = !state.isImporting && state.textFound.isNotEmpty(),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.import_text_import))
                    }
                }
            }
        }

        if (state.textQueries.isNotEmpty()) {
            item(key = "text_result") {
                ImportCard(
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    title = stringResource(
                        R.string.import_text_summary,
                        state.textFound.size,
                        state.textQueries.size,
                    ),
                ) {
                    if (state.textMissing > 0) {
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

/** Карточка раздела: иконка, заголовок, пояснение и содержимое. */
@Composable
private fun ImportCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProfilePlaylistRow(
    playlist: ProfilePlaylistItem,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = playlist.selected, onCheckedChange = { onToggle() })
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.track_count,
                    playlist.trackCount,
                    playlist.trackCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Текст ошибки лайков: пусто, приватно, HTTP-код API или сеть. */
@Composable
private fun likesErrorText(state: ImportUiState): String? = when (state.likesError) {
    LikesError.EMPTY -> stringResource(R.string.import_sc_likes_empty)
    LikesError.PRIVATE -> stringResource(R.string.import_sc_likes_private)
    LikesError.API -> stringResource(
        R.string.import_sc_likes_api_error,
        state.likesApiCode ?: 0,
    )
    LikesError.NETWORK -> stringResource(R.string.import_sc_likes_network)
    null -> null
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

/** Сколько строк лайков показываем в превью. */
private const val PREVIEW_TRACK_COUNT = 5
