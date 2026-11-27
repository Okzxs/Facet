package com.facet.music.feature.imports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.facet.music.R
import com.facet.music.core.ui.components.DockAwareSnackbarHost
import com.facet.music.core.ui.theme.CloudReveal
import com.facet.music.core.import.ImportError
import com.facet.music.core.import.ImportService

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
            ImportHeader(onBack = onBack)
        },
        snackbarHost = { DockAwareSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ImportTabs(
                selected = state.tab,
                onSelect = viewModel::setTab,
            )

            AnimatedContent(
                targetState = state.tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                },
                label = "importTab",
            ) { tab ->
                when (tab) {
                    ImportTab.PROFILE -> ProfileImportContent(state, viewModel)
                    ImportTab.LINK -> LinkImportContent(state, viewModel)
                    ImportTab.YOUTUBE -> YouTubeImportContent(state, viewModel)
                    ImportTab.TEXT -> TextImportContent(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ImportHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "FACET",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.import_screen_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ImportTabs(
    selected: ImportTab,
    onSelect: (ImportTab) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ImportTab.entries) { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.height(44.dp),
                shape = MaterialTheme.shapes.extraLarge,
                label = {
                    Text(
                        text = stringResource(
                            when (tab) {
                                ImportTab.PROFILE -> R.string.import_tab_profile
                                ImportTab.LINK -> R.string.import_tab_link
                                ImportTab.YOUTUBE -> R.string.import_tab_youtube
                                ImportTab.TEXT -> R.string.import_tab_text
                            },
                        ),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun LikesSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun LikesTrackRow(
    track: com.facet.music.data.model.Track,
    badge: String,
    checked: Boolean,
    showCheckbox: Boolean = true,
    onToggle: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
        if (showCheckbox) Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ProfileImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "profile_link") {
            ImportCard(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.import_profile_card_title),
                subtitle = stringResource(R.string.import_profile_hint),
                revealIndex = 0,
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

        if (state.detectedService != ImportService.SPOTIFY) {
            item(key = "sc_likes") {
                ImportCard(
                    icon = Icons.Rounded.Favorite,
                    title = stringResource(R.string.import_sc_likes_title),
                    subtitle = stringResource(R.string.import_sc_likes_hint),
                    revealIndex = 1,
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

                    AnimatedVisibility(
                        visible = state.likesPreview.isNotEmpty(),
                        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
                    ) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (state.likesTotal > 0) {
                                    stringResource(R.string.import_likes_available, state.likesTotal, state.likesPreview.size)
                                } else {
                                    stringResource(R.string.import_likes_count_selected, state.likesPreview.size, state.selectedLikeIds.size)
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LikesSourceFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = state.likesSourceFilter == filter,
                                        onClick = { viewModel.setLikesSourceFilter(filter) },
                                        label = {
                                            Text(
                                                when (filter) {
                                                    LikesSourceFilter.ALL -> stringResource(R.string.import_likes_all)
                                                    LikesSourceFilter.SOUNDCLOUD -> "SoundCloud"
                                                    LikesSourceFilter.YOUTUBE -> "YouTube"
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                            if (state.likesSourceFilter != LikesSourceFilter.YOUTUBE) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = state.selectedLikeIds.size == state.likesPreview.size,
                                        onCheckedChange = viewModel::selectAllLikes,
                                    )
                                    Text(
                                        text = stringResource(R.string.import_likes_select_all),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = viewModel::importSoundCloudLikes,
                                    enabled = !state.isImporting && state.selectedLikeIds.isNotEmpty(),
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
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
                                        pluralStringResource(
                                            R.plurals.import_sc_likes_import,
                                            state.selectedLikeIds.size,
                                            state.selectedLikeIds.size,
                                        ),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                LikesSection(
                                    title = "SoundCloud",
                                    subtitle = stringResource(
                                        R.string.import_likes_selected_of,
                                        state.selectedLikeIds.size,
                                        state.likesPreview.size,
                                    ),
                                ) {
                                    state.likesPreview.take(state.visibleLikes).forEach { track ->
                                        LikesTrackRow(track, "SC", track.id in state.selectedLikeIds) {
                                            viewModel.toggleLike(track.id)
                                        }
                                    }
                                    if (state.visibleLikes < state.likesPreview.size) {
                                        TextButton(onClick = viewModel::showMoreLikes) {
                                            Text(stringResource(R.string.import_likes_show_more))
                                        }
                                    }
                                }
                            }
                            if (state.likesSourceFilter != LikesSourceFilter.SOUNDCLOUD) {
                                val found = state.fallbackResults.values.count { it.status == FallbackStatus.FOUND }
                                val checked = state.fallbackResults.size
                                Spacer(Modifier.height(8.dp))
                                LikesSection(
                                    title = stringResource(R.string.import_likes_youtube_fallback),
                                    subtitle = if (checked > 0) {
                                        stringResource(R.string.import_likes_found_of, found, checked, state.selectedLikeIds.size)
                                    } else {
                                        stringResource(R.string.import_likes_not_checked)
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.import_likes_fallback_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    state.fallbackResults.values.filter { it.fallbackSource != null }.forEach { item ->
                                        LikesTrackRow(item.fallbackSource!!, "SC→YT", false, showCheckbox = false) {}
                                    }
                                    if (checked > 0 && found == 0) {
                                        Text(
                                            text = stringResource(R.string.import_likes_no_matches),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(
                                        onClick = viewModel::checkYouTubeFallback,
                                        enabled = state.selectedLikeIds.isNotEmpty() && !state.fallbackRunning,
                                    ) {
                                        if (state.fallbackRunning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(
                                            stringResource(
                                                if (state.fallbackRunning) {
                                                    R.string.import_likes_check_running
                                                } else {
                                                    R.string.import_likes_check_selected
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
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
                    revealIndex = 2,
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

        if (state.playlists.isEmpty() && !state.isLoading) {
            item(key = "profile_empty_cloud") {
                CloudReveal(index = 3) { ImportCloudHint() }
            }
        }
    }
}

@Composable
private fun ImportCloudHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )
            Surface(
                modifier = Modifier.size(84.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.import_empty_cloud_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.import_empty_cloud_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun YouTubeImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    val takeoutLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importYouTubeTakeout)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "yt_profile") {
            ImportCard(
                icon = Icons.Rounded.Person,
                title = stringResource(R.string.import_yt_profile_title),
                subtitle = stringResource(R.string.import_yt_profile_hint),
                revealIndex = 0,
            ) {
                OutlinedTextField(
                    value = state.link,
                    onValueChange = viewModel::setLink,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.import_yt_profile_field)) },
                    placeholder = { Text("https://music.youtube.com/channel/…") },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::loadProfile,
                    enabled = !state.isLoading && state.link.isNotBlank(),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.import_yt_profile_load))
                }
                if (state.playlists.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.import_profile_found,
                            state.playlists.size,
                            state.selectedCount,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    state.playlists.forEach { playlist ->
                        ProfilePlaylistRow(
                            playlist = playlist,
                            onToggle = { viewModel.togglePlaylist(playlist.id) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
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

        item(key = "yt_takeout") {
            ImportCard(
                icon = Icons.Rounded.Favorite,
                title = stringResource(R.string.import_yt_takeout_title),
                subtitle = stringResource(R.string.import_yt_takeout_hint),
                revealIndex = 1,
            ) {
                Button(
                    onClick = {
                        takeoutLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/csv",
                            ),
                        )
                    },
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
                    Text(stringResource(R.string.import_yt_takeout_button))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.import_yt_takeout_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.playlists.isEmpty() && !state.isLoading) {
            item(key = "yt_empty_cloud") {
                CloudReveal(index = 2) { ImportCloudHint() }
            }
        }
    }
}

@Composable
private fun LinkImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "playlist_link") {
            ImportCard(
                icon = Icons.Rounded.AddLink,
                title = stringResource(R.string.import_link_card_title),
                subtitle = stringResource(R.string.import_link_hint),
                revealIndex = 0,
            ) {
                OutlinedTextField(
                    value = state.playlistLink,
                    onValueChange = viewModel::setPlaylistLink,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.import_link_field)) },
                    placeholder = { Text("https://open.spotify.com/playlist/…") },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::loadPlaylistLink,
                    enabled = !state.playlistLoading && state.playlistLink.isNotBlank(),
                    shape = CircleShape,
                ) {
                    if (state.playlistLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.import_link_load))
                }
            }
        }

        val preview = state.playlistPreview
        if (preview != null) {
            item(key = "playlist_preview") {
                ImportCard(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = preview.name,
                    subtitle = pluralStringResource(
                        R.plurals.import_link_track_count,
                        preview.tracks.size,
                        preview.tracks.size,
                    ),
                    revealIndex = 1,
                ) {
                    preview.tracks.take(10).forEach { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                track.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    track.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (preview.isIncomplete) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.import_link_incomplete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::importPlaylistLink,
                        enabled = !state.playlistImporting && preview.tracks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        if (state.playlistImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.import_link_import))
                    }
                }
            }
        }

        if (preview == null && !state.playlistLoading) {
            item(key = "link_empty_cloud") {
                CloudReveal(index = 2) { ImportCloudHint() }
            }
        }
    }
}

@Composable
private fun TextImportContent(state: ImportUiState, viewModel: ImportViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "text_input") {
            ImportCard(
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                title = stringResource(R.string.import_text_card_title),
                subtitle = stringResource(R.string.import_text_hint),
                revealIndex = 0,
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
                    revealIndex = 1,
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

        if (state.textQueries.isEmpty() && !state.isLoading) {
            item(key = "text_empty_cloud") {
                CloudReveal(index = 2) { ImportCloudHint() }
            }
        }
    }
}

@Composable
private fun ImportCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    revealIndex: Int = -1,
    content: @Composable () -> Unit,
) {
    val card: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                content()
            }
        }
    }
    if (revealIndex >= 0) {
        CloudReveal(index = revealIndex) { card() }
    } else {
        card()
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = playlist.selected, onCheckedChange = { onToggle() })
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
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
