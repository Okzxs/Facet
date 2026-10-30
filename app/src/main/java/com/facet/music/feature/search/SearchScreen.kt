package com.facet.music.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facet.music.R
import com.facet.music.core.service.ServiceHealth
import com.facet.music.core.service.ServiceType
import com.facet.music.core.ui.components.AddToPlaylistSheet
import com.facet.music.core.ui.components.TrackCard
import com.facet.music.navigation.LocalBottomDockHeight
import com.facet.music.data.model.Track
import kotlinx.coroutines.launch

private enum class SearchContentState { Idle, Loading, Error, Empty, Results }

private val searchSuggestions = listOf("Electronic", "Hip-Hop", "Rock", "Chill", "Pop", "R&B")

/**
 * Порядок кнопок-источников в поиске: сначала бренды с полноценным стримом,
 * Spotify последним — он работает только при введённых ключах.
 */
private val sourceButtonOrder = listOf(
    ServiceType.YOUTUBE,
    ServiceType.SOUNDCLOUD,
    ServiceType.DEEZER,
    ServiceType.APPLE,
    ServiceType.SPOTIFY,
)

// Брендовые цвета источников: фон кнопки — с альфой, текст — чистый цвет.
private val YoutubeRed = Color(0xFFFF0000)
private val SoundCloudOrange = Color(0xFFFF5500)
private val DeezerPurple = Color(0xFFA238FF)
private val ApplePink = Color(0xFFFA243C)
private val SpotifyGreen = Color(0xFF1DB954)

/** Векторная иконка бренда внутри квадратной кнопки источника. */
private fun ServiceType.brandIconRes(): Int = when (this) {
    ServiceType.YOUTUBE -> R.drawable.ic_brand_youtube
    ServiceType.SOUNDCLOUD -> R.drawable.ic_brand_soundcloud
    ServiceType.DEEZER -> R.drawable.ic_brand_deezer
    ServiceType.APPLE -> R.drawable.ic_brand_apple
    ServiceType.SPOTIFY -> R.drawable.ic_brand_spotify
}

/** Фирменный цвет бренда. */
private fun ServiceType.brandColor(): Color = when (this) {
    ServiceType.YOUTUBE -> YoutubeRed
    ServiceType.SOUNDCLOUD -> SoundCloudOrange
    ServiceType.DEEZER -> DeezerPurple
    ServiceType.APPLE -> ApplePink
    ServiceType.SPOTIFY -> SpotifyGreen
}

// Семантические цвета статусов: зелёный/красный не должны зависеть от Dynamic Color.
private val OnlineGreenLight = Color(0xFF1E8E3E)
private val OnlineGreenDark = Color(0xFF81C995)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onTrackClick: (track: Track, queue: List<Track>) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Boolean,
    onAddToQueue: (Track) -> Boolean,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val serviceStatuses by viewModel.serviceStatuses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Трек, для которого открыта шторка «Добавить в плейлист».
    var playlistTrack by remember { mutableStateOf<Track?>(null) }

    // Очередь живёт в плеере, поэтому единственный отклик на действие —
    // Snackbar с названием трека.
    val handlePlayNext: (Track) -> Unit = { track ->
        if (onPlayNext(track)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.queue_play_next_done, track.title),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }
    val handleAddToQueue: (Track) -> Unit = { track ->
        val added = onAddToQueue(track)
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    if (added) R.string.queue_added_done else R.string.queue_already_added,
                    track.title,
                ),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val contentState = when {
        state.isLoading -> SearchContentState.Loading
        state.error != null -> SearchContentState.Error
        state.results.isEmpty() && state.query.length >= 2 -> SearchContentState.Empty
        state.results.isNotEmpty() -> SearchContentState.Results
        else -> SearchContentState.Idle
    }

    Scaffold(
        // Фон ставим сами: корневого Scaffold больше нет, без него экран прозрачный.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.surface,
        // Статус-бар обеспечиваем сами (корневого Scaffold больше нет); низ —
        // через contentPadding списков с запасом под плавающий док.
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchHeader()

            ExpressiveSearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            SourceSwitcher(
                selected = state.selectedService,
                statuses = serviceStatuses,
                onSelect = viewModel::onServiceSelected,
                onRefreshStatuses = viewModel::refreshStatuses,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )

            AnimatedVisibility(
                visible = state.query.isBlank() && state.recentQueries.isNotEmpty(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(120)),
            ) {
                RecentQueriesRow(
                    queries = state.recentQueries,
                    onClick = viewModel::onRecentQueryClick,
                    onClear = viewModel::clearHistory,
                )
            }

            AnimatedVisibility(
                visible = state.query.isBlank(),
                enter = fadeIn(tween(220)) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                SuggestionChips(onSuggestion = viewModel::onQueryChange)
            }

            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically { it / 16 }) togetherWith
                        fadeOut(tween(130))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "searchContent",
            ) { target ->
                when (target) {
                    SearchContentState.Idle -> IdleState(
                        query = state.query,
                        serviceName = state.selectedService.displayName,
                    )
                    SearchContentState.Loading -> SearchSkeletonList()
                    SearchContentState.Error -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::retry,
                    )
                    SearchContentState.Empty -> EmptyResultsState(
                        query = state.query,
                        serviceName = state.selectedService.displayName,
                        selectedService = state.selectedService,
                        onSelectService = viewModel::onServiceSelected,
                    )
                    SearchContentState.Results -> ResultsList(
                        tracks = state.results,
                        downloadedIds = downloadedIds,
                        serviceName = state.selectedService.displayName,
                        onTrackClick = onTrackClick,
                        onAddToPlaylist = { track -> playlistTrack = track },
                        onArtistClick = onArtistClick,
                        onPlayNext = handlePlayNext,
                        onAddToQueue = handleAddToQueue,
                    )
                }
            }
        }
    }

    // Шторка вне Scaffold: она рисуется в отдельном окне, а Snackbar
    // «Добавлено в …» показывает сам поиск.
    playlistTrack?.let { track ->
        AddToPlaylistSheet(
            track = track,
            onDismiss = { playlistTrack = null },
            onAdded = { playlistName ->
                playlistTrack = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.playlist_added_to, playlistName),
                        duration = SnackbarDuration.Short,
                    )
                }
            },
        )
    }
}

@Composable
private fun SearchHeader() {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.search_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceSwitcher(
    selected: ServiceType,
    statuses: Map<ServiceType, ServiceHealth>,
    onSelect: (ServiceType) -> Unit,
    onRefreshStatuses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedHealth = statuses[selected] ?: ServiceHealth(isChecking = true)

    Column(modifier = modifier) {
        // 5 кнопок по 48dp, 4 промежутка по 10dp и кнопка статусов справа:
        // 280 + 46 + отступы = 326dp — помещается в 411dp без прокрутки.
        // На узких экранах (360dp) промежуток сжимается до 4dp, кнопки не сплющиваются.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val buttonSize = 48.dp
            val statusButtonSize = 46.dp
            val maxGap = 10.dp
            val minGap = 4.dp
            // Остаток ширины после кнопок и статуса делим на 4 промежутка.
            val gap = ((maxWidth - buttonSize * sourceButtonOrder.size - statusButtonSize) / 4)
                .coerceIn(minGap, maxGap)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    sourceButtonOrder.forEach { service ->
                        SourceButton(
                            service = service,
                            selected = service == selected,
                            health = statuses[service] ?: ServiceHealth(),
                            onClick = { onSelect(service) },
                        )
                    }
                }

                ServiceStatusButton(
                    statuses = statuses,
                    onRefresh = onRefreshStatuses,
                )
            }
        }

        UnavailableServiceWarning(
            service = selected,
            health = selectedHealth,
        )
    }
}

/**
 * Квадратная кнопка источника 48×48: фон — фирменный цвет с альфой 0.15,
 * внутри векторная иконка бренда в полный цвет. Выбранный источник получает
 * более плотный фон 0.25 и рамку 2dp цвета бренда; недоступный — приглушённую
 * иконку (alpha 0.4), но остаётся нажимаемым.
 */
@Composable
private fun SourceButton(
    service: ServiceType,
    selected: Boolean,
    health: ServiceHealth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = service.brandColor()
    val unavailable = !health.isAvailable && !health.isChecking
    val sourceName = service.displayName
    val contentDescription = stringResource(R.string.search_source_cd, sourceName)

    val containerColor by animateColorAsState(
        targetValue = brand.copy(alpha = if (selected) 0.25f else 0.15f),
        animationSpec = tween(180),
        label = "sourceButtonContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) brand else Color.Transparent,
        animationSpec = tween(180),
        label = "sourceButtonBorder",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = brand,
        border = BorderStroke(2.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(service.brandIconRes()),
                contentDescription = contentDescription,
                tint = brand,
                // Недоступный сервис виден по приглушённой иконке, а не по всей кнопке.
                modifier = Modifier
                    .size(26.dp)
                    .alpha(if (unavailable) 0.4f else 1f),
            )
        }
    }
}

@Composable
private fun ServiceStatusButton(
    statuses: Map<ServiceType, ServiceHealth>,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anyChecking = statuses.values.any { it.isChecking }
    val anyUnavailable = statuses.values.any { !it.isAvailable && !it.isChecking }
    val onlineColor = serviceOnlineColor()

    val indicatorColor by animateColorAsState(
        targetValue = when {
            anyChecking -> MaterialTheme.colorScheme.primary
            anyUnavailable -> MaterialTheme.colorScheme.error
            else -> onlineColor
        },
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "statusBadge",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            anyUnavailable -> MaterialTheme.colorScheme.error
            anyChecking -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(180),
        label = "statusIcon",
    )

    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                if (anyUnavailable) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
            ),
            tonalElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Radar,
                    contentDescription = when {
                        anyChecking -> stringResource(R.string.search_status_cd_checking)
                        anyUnavailable -> stringResource(R.string.search_status_cd_unavailable)
                        else -> stringResource(R.string.search_status_cd_online)
                    },
                    tint = iconTint,
                    modifier = Modifier.size(23.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 9.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(indicatorColor),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(304.dp),
            offset = DpOffset(x = 0.dp, y = 6.dp),
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.search_status_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.search_status_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                ServiceType.entries.forEachIndexed { index, service ->
                    ServiceStatusRow(
                        service = service,
                        health = statuses[service] ?: ServiceHealth(isChecking = true),
                    )
                    if (index < ServiceType.entries.lastIndex) {
                        Spacer(Modifier.height(2.dp))
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )

                FilledTonalButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    if (anyChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_status_refresh))
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusRow(
    service: ServiceType,
    health: ServiceHealth,
) {
    val onlineColor = serviceOnlineColor()
    val isUnavailable = !health.isAvailable && !health.isChecking
    val statusColor = when {
        health.isChecking -> MaterialTheme.colorScheme.primary
        health.isAvailable -> onlineColor
        else -> MaterialTheme.colorScheme.error
    }
    val statusLabel = when {
        health.isChecking -> stringResource(R.string.search_status_checking)
        health.isAvailable -> stringResource(R.string.search_status_online)
        else -> stringResource(R.string.search_status_error)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = MaterialTheme.shapes.medium,
            color = if (isUnavailable) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = serviceIcon(service),
                    contentDescription = null,
                    tint = if (isUnavailable) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = service.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (health.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun UnavailableServiceWarning(
    service: ServiceType,
    health: ServiceHealth,
) {
    AnimatedVisibility(
        visible = !health.isAvailable && !health.isChecking,
        enter = fadeIn(tween(180)) +
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.search_service_unavailable, service.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private fun serviceIcon(service: ServiceType) = when (service) {
    ServiceType.SOUNDCLOUD -> Icons.Rounded.Cloud
    ServiceType.YOUTUBE -> Icons.Rounded.SmartDisplay
    ServiceType.DEEZER -> Icons.Rounded.Equalizer
    ServiceType.APPLE -> Icons.Rounded.MusicNote
    ServiceType.SPOTIFY -> Icons.Rounded.GraphicEq
}

@Composable
private fun serviceOnlineColor(): Color =
    if (isSystemInDarkTheme()) OnlineGreenDark else OnlineGreenLight

@Composable
private fun ExpressiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.extraLarge

    val containerColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(240),
        label = "searchContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(240),
        label = "searchBorder",
    )
    val iconContainerColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(240),
        label = "searchIconContainer",
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(240),
        label = "searchIconTint",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = shape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = iconContainerColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(160)) + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.search_clear_input),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.search_try),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            searchSuggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSuggestion(suggestion) },
                    label = {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                )
            }
        }
    }
}

/**
 * Недавние запросы: горизонтальный ряд чипсов над подсказками.
 * Появляются, только пока поле пустое — чтобы не мешать вводу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentQueriesRow(
    queries: List<String>,
    onClick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.search_clear),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            queries.forEach { query ->
                AssistChip(
                    onClick = { onClick(query) },
                    label = {
                        Text(
                            text = query,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shape = MaterialTheme.shapes.large,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun IdleState(
    query: String,
    serviceName: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(MaterialTheme.shapes.extraLarge)
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
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.search_idle_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (query.isEmpty()) {
                stringResource(R.string.search_idle_hint, serviceName)
            } else {
                stringResource(R.string.search_idle_hint_short)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyResultsState(
    query: String,
    serviceName: String,
    selectedService: ServiceType,
    onSelectService: (ServiceType) -> Unit,
) {
    val alternatives = ServiceType.entries.filter { it != selectedService }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_empty_message, query, serviceName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (query.length >= 2) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.search_other_source),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                alternatives.forEach { service ->
                    AssistChip(
                        onClick = { onSelectService(service) },
                        label = {
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = serviceIcon(service),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.search_error_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        FilledTonalButton(
            onClick = onRetry,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.search_retry))
        }
    }
}

@Composable
private fun ResultsList(
    tracks: List<Track>,
    downloadedIds: Set<String>,
    serviceName: String,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_results, serviceName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "${tracks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomDockHeight.current),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = tracks,
                key = { it.id },
            ) { track ->
                TrackCard(
                    track = track,
                    isDownloaded = track.id in downloadedIds,
                    onClick = { onTrackClick(track, tracks) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onArtistClick = { onArtistClick(track.artist) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                )
            }
        }
    }
}

/**
 * Скелетон списка карточек при поиске: повторяет геометрию TrackCard.
 */
@Composable
private fun SearchSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .height(15.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(11.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}
