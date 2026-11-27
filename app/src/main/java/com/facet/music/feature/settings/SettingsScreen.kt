package com.facet.music.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.facet.music.BuildConfig
import com.facet.music.R
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.location.LocationPermissions
import com.facet.music.data.repository.SettingsRepository
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.facet.music.core.ui.theme.contrastContentColor
import com.facet.music.core.ui.theme.AccentOption
import com.facet.music.core.ui.theme.AccentOptions
import com.facet.music.core.spotify.SpotifyAuthState
import com.facet.music.core.ui.components.TrackArtwork
import com.facet.music.core.ui.theme.cloudPress
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.ThemeMode
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStudio: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val spotifyAuth by viewModel.spotifyAuth.collectAsState()
    val spotifyAuthUrl by viewModel.spotifyAuthUrl.collectAsState()
    val spotifyAuthError by viewModel.spotifyAuthError.collectAsState()
    val cacheBytes by viewModel.cacheBytes.collectAsState()
    val cacheCount by viewModel.cacheCount.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasLocationPermission by remember {
        mutableStateOf(LocationPermissions.hasPermission(context))
    }
    var showSpotifyGuide by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingAnchor by remember { mutableStateOf<String?>(null) }
    val anchorPositions = remember { mutableStateMapOf<String, Int>() }
    val settingsScroll = rememberScrollState()

    LaunchedEffect(pendingAnchor) {
        val target = pendingAnchor ?: return@LaunchedEffect
        withFrameNanos { }
        val offset = anchorPositions[target] ?: return@LaunchedEffect
        settingsScroll.animateScrollTo(offset)
        pendingAnchor = null
    }

    LaunchedEffect(spotifyAuthUrl) {
        val url = spotifyAuthUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        viewModel.consumeSpotifyAuthUrl()
    }

    val spotifyErrorText = stringResource(R.string.spotify_sign_in_failed)
    LaunchedEffect(spotifyAuthError) {
        if (!spotifyAuthError) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = spotifyErrorText,
            duration = SnackbarDuration.Long,
        )
        viewModel.consumeSpotifyAuthError()
    }

    val languageChangedMessage = stringResource(R.string.settings_language_changed)
    val restartLabel = stringResource(R.string.settings_language_restart)
    val onLanguageSelect: (String) -> Unit = { language ->
        viewModel.setAppLanguage(language)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = languageChangedMessage,
                actionLabel = restartLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                context.findActivity()?.recreate()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = LocationPermissions.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                SettingsHeader(
                    onBack = onBack,
                    searchActive = searchActive,
                    onSearchToggle = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                )
            }

            if (searchActive) {
                SettingsSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
                Spacer(Modifier.height(4.dp))
            }

            if (searchActive && searchQuery.isNotBlank()) {
                SettingsSearchResults(
                    query = searchQuery,
                    onPick = { entry ->
                        if (entry.opensStudio) {
                            searchActive = false
                            searchQuery = ""
                            onOpenStudio()
                        } else {
                            pendingAnchor = entry.anchor
                            searchActive = false
                            searchQuery = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(settingsScroll),
            ) {
                CloudSettingsQuickGrid(
                    onAppearance = { pendingAnchor = ANCHOR_APPEARANCE },
                    onLanguage = { pendingAnchor = ANCHOR_LANGUAGE },
                    onIntegrations = { pendingAnchor = ANCHOR_INTEGRATIONS },
                    onPlayback = { pendingAnchor = ANCHOR_BACKGROUND },
                )

                SettingsSectionTitle(
                    R.string.settings_appearance,
                    anchor = ANCHOR_APPEARANCE,
                    positions = anchorPositions,
                )

                StudioEntryRow(
                    settings = settings,
                    onClick = onOpenStudio,
                )

                SettingsDivider()

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_cover),
                    hint = stringResource(R.string.settings_dynamic_cover_hint),
                    checked = settings.dynamicCoverColors,
                    onCheckedChange = viewModel::setDynamicCoverColors,
                )

                SettingsSectionTitle(
                    R.string.settings_language_section,
                    anchor = ANCHOR_LANGUAGE,
                    positions = anchorPositions,
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SegmentedButtonRow(
                    selected = settings.appLanguage,
                    onSelect = onLanguageSelect,
                )
            }

            SettingsSectionTitle(
                    R.string.settings_lyrics_translation_section,
                    anchor = ANCHOR_LYRICS_TRANSLATION,
                    positions = anchorPositions,
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_lyrics_translation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SegmentedButtonRow(
                    selected = settings.lyricsTranslationLanguage,
                    onSelect = viewModel::setLyricsTranslationLanguage,
                )
            }

            SettingsSectionTitle(
                    R.string.settings_integrations,
                    anchor = ANCHOR_INTEGRATIONS,
                    positions = anchorPositions,
                )
            SpotifyIntegrationCard(
                settings = settings,
                auth = spotifyAuth,
                onSave = viewModel::saveSpotifyCredentials,
                onSignIn = viewModel::signInSpotify,
                onSignOut = viewModel::signOutSpotify,
                onOpenGuide = { showSpotifyGuide = true },
            )

            SettingsSectionTitle(
                    R.string.settings_permissions,
                    anchor = ANCHOR_PERMISSIONS,
                    positions = anchorPositions,
                )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hasLocationPermission) {
                        Icons.Rounded.LocationOn
                    } else {
                        Icons.Rounded.LocationOff
                    },
                    contentDescription = null,
                    tint = if (hasLocationPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasLocationPermission) {
                            stringResource(R.string.settings_location_allowed)
                        } else {
                            stringResource(R.string.settings_location_denied)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (hasLocationPermission) {
                            stringResource(R.string.settings_location_allowed_hint)
                        } else {
                            stringResource(R.string.settings_location_denied_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        hasLocationPermission = LocationPermissions.hasPermission(context)
                        if (!hasLocationPermission) {
                            LocationPermissions.openAppSettings(context)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.settings_check_permissions))
                }

                if (!hasLocationPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_location_footer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSectionTitle(
                    R.string.settings_background_section,
                    anchor = ANCHOR_BACKGROUND,
                    positions = anchorPositions,
                )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_focus_resume_title),
                hint = stringResource(R.string.settings_focus_resume_hint),
                checked = settings.resumeAfterFocusLoss,
                onCheckedChange = viewModel::setResumeAfterFocusLoss,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = { LocationPermissions.openAppSettings(context) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.settings_battery_open))
                }
            }

            SettingsDivider()

            SettingsSectionTitle(
                    R.string.settings_downloads_section,
                    anchor = ANCHOR_DOWNLOADS,
                    positions = anchorPositions,
                )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_quality_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.settings_quality_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsRepository.DOWNLOAD_QUALITIES.forEach { kbps ->
                        FilterChip(
                            selected = settings.downloadQualityKbps == kbps,
                            onClick = { viewModel.setDownloadQuality(kbps) },
                            label = { Text("$kbps") },
                        )
                    }
                }
            }

            SettingsDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsAnchor(ANCHOR_CACHE, anchorPositions)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_cache_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (cacheCount == 0) {
                            stringResource(R.string.settings_cache_empty)
                        } else {
                            stringResource(
                                R.string.settings_cache_summary,
                                formatMegabytes(cacheBytes),
                                cacheCount,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (cacheCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = viewModel::clearAudioCache) {
                        Text(stringResource(R.string.settings_cache_clear))
                    }
                }
            }

            SettingsSectionTitle(
                    R.string.settings_privacy_section,
                    anchor = ANCHOR_PRIVACY,
                    positions = anchorPositions,
                )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_analytics_title),
                hint = stringResource(R.string.settings_analytics_hint),
                checked = settings.analyticsEnabled,
                onCheckedChange = viewModel::setAnalyticsEnabled,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsAnchor(ANCHOR_SUPPORT, anchorPositions)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)),
                            )
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_support_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.settings_support_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { showLicenses = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .settingsAnchor(ANCHOR_ABOUT, anchorPositions)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_licenses),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        }
            }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding(),
        )
    }

    if (showSpotifyGuide) {
        SpotifyGuideDialog(onDismiss = { showSpotifyGuide = false })
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun SettingsSectionTitle(
    titleRes: Int,
    anchor: String? = null,
    positions: MutableMap<String, Int>? = null,
) {
    val anchorModifier = if (anchor != null && positions != null) {
        Modifier.onGloballyPositioned { coordinates ->
            positions[anchor] = coordinates.positionInParent().y.roundToInt()
        }
    } else {
        Modifier
    }
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp)
            .then(anchorModifier),
    )
}

private fun Modifier.settingsAnchor(
    id: String,
    positions: MutableMap<String, Int>,
): Modifier = onGloballyPositioned { coordinates ->
    positions[id] = coordinates.positionInParent().y.roundToInt()
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    )
}

@Composable
private fun StudioEntryRow(
    settings: AppSettings,
    onClick: () -> Unit,
) {
    val palette = com.facet.music.core.ui.theme.FacetPalette.fromName(settings.paletteMode)
    val shape = com.facet.music.core.ui.theme.FacetShapeStyle.fromName(settings.shapeStyle)
    val summary = buildString {
        append(stringResource(paletteLabel(palette)))
        append(" · ")
        append(
            stringResource(
                if (settings.serifDisplay) {
                    R.string.studio_entry_serif
                } else {
                    R.string.studio_entry_sans
                },
            ),
        )
        append(" · ")
        append(stringResource(shapeStyleLabel(shape)))
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_studio_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licenses_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.settings_licenses_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { uriHandler.openUri(LICENSES_URL) }
                },
            ) {
                Text(stringResource(R.string.settings_licenses_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_licenses_close))
            }
        },
    )
}

private const val LICENSES_URL = "https://github.com/Okzxs/my-music-app/blob/main/LICENSE"

private const val SUPPORT_URL = "https://www.donationalerts.com/r/facetapp"

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0)

internal fun shapeStyleLabel(style: com.facet.music.core.ui.theme.FacetShapeStyle): Int =
    when (style) {
        com.facet.music.core.ui.theme.FacetShapeStyle.SOFT -> R.string.shape_soft
        com.facet.music.core.ui.theme.FacetShapeStyle.DEFAULT -> R.string.shape_default
        com.facet.music.core.ui.theme.FacetShapeStyle.ROUND -> R.string.shape_round
    }

internal fun paletteLabel(palette: com.facet.music.core.ui.theme.FacetPalette): Int =
    when (palette) {
        com.facet.music.core.ui.theme.FacetPalette.DYNAMIC -> R.string.palette_dynamic
        com.facet.music.core.ui.theme.FacetPalette.SIGNATURE -> R.string.palette_signature
        com.facet.music.core.ui.theme.FacetPalette.NIGHT_JAZZ -> R.string.palette_night_jazz
        com.facet.music.core.ui.theme.FacetPalette.PAPER -> R.string.palette_paper
        com.facet.music.core.ui.theme.FacetPalette.MONO -> R.string.palette_mono
    }

@Composable
private fun CloudSettingsQuickGrid(
    onAppearance: () -> Unit,
    onLanguage: () -> Unit,
    onIntegrations: () -> Unit,
    onPlayback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CloudSettingsTile(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_theme_hint),
                onClick = onAppearance,
                modifier = Modifier.weight(1f),
            )
            CloudSettingsTile(
                icon = Icons.Rounded.Translate,
                title = stringResource(R.string.settings_language_section),
                subtitle = stringResource(R.string.settings_language_hint),
                onClick = onLanguage,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CloudSettingsTile(
                icon = Icons.Rounded.Hub,
                title = stringResource(R.string.settings_integrations),
                subtitle = "Spotify, YouTube, SoundCloud",
                onClick = onIntegrations,
                modifier = Modifier.weight(1f),
            )
            CloudSettingsTile(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.settings_background_section),
                subtitle = stringResource(R.string.settings_focus_resume_title),
                onClick = onPlayback,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CloudSettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .height(124.dp)
            .cloudPress(interactionSource),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    onBack: () -> Unit,
    searchActive: Boolean,
    onSearchToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "FACET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_overline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(8.dp))

        FilledTonalIconButton(
            onClick = onSearchToggle,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        ) {
            Icon(
                imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = stringResource(
                    if (searchActive) R.string.settings_search_close else R.string.settings_search_cd,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .heightIn(min = 56.dp),
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.settings_search_clear),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun SettingsSearchResults(
    query: String,
    onPick: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val results = remember(query, context) {
        settingsSearchEntries().filter { entry ->
            entry.matches(
                query = query,
                title = context.getString(entry.titleRes),
                hint = context.getString(entry.hintRes),
            )
        }
    }
    if (results.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        results.forEach { entry ->
            Surface(
                onClick = { onPick(entry) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(entry.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(entry.sectionRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (entry.opensStudio) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CustomColorDot(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "customColorDotSize",
    )
    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFE5484D),
                Color(0xFFF5A524),
                Color(0xFF43A047),
                Color(0xFF00A2A9),
                Color(0xFF3F6BD8),
                Color(0xFF8B5CF6),
                Color(0xFFE5484D),
            ),
        )
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .then(
                    if (selected && color != null) {
                        Modifier.background(color)
                    } else {
                        Modifier.background(rainbowBrush)
                    },
                )
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.Palette,
                contentDescription = if (selected) {
                    stringResource(R.string.settings_custom_color_cd_selected)
                } else {
                    stringResource(R.string.settings_custom_color_cd)
                },
                tint = color?.let(::contrastContentColor) ?: Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun CustomColorDialog(
    initial: Color?,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
    onReset: () -> Unit,
) {
    val startColor = initial ?: Color(0.65f, 0.36f, 0.55f)
    var red by remember { mutableFloatStateOf(startColor.red) }
    var green by remember { mutableFloatStateOf(startColor.green) }
    var blue by remember { mutableFloatStateOf(startColor.blue) }
    val preview = Color(red, green, blue)
    var hexInput by remember { mutableStateOf(formatHexColor(startColor)) }
    var hexError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_color)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(preview)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.large,
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { value ->
                            val normalized = value.take(MAX_HEX_INPUT_LENGTH)
                            hexInput = normalized
                            val parsed = parseHexColor(normalized)
                            if (parsed != null) {
                                red = parsed.red
                                green = parsed.green
                                blue = parsed.blue
                                hexError = false
                            } else {
                                hexError = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.settings_hex_label)) },
                        singleLine = true,
                        isError = hexError,
                        supportingText = {
                            Text(
                                if (hexError) {
                                    stringResource(R.string.settings_hex_error)
                                } else {
                                    stringResource(R.string.settings_hex_hint)
                                },
                            )
                        },
                        shape = MaterialTheme.shapes.large,
                    )
                }
                Spacer(Modifier.height(18.dp))
                RgbSlider(
                    label = stringResource(R.string.settings_red),
                    value = red,
                    accent = Color(0xFFE5484D),
                    onValueChange = { v ->
                        red = v
                        hexInput = formatHexColor(Color(v, green, blue))
                        hexError = false
                    },
                )
                RgbSlider(
                    label = stringResource(R.string.settings_green),
                    value = green,
                    accent = Color(0xFF43A047),
                    onValueChange = { v ->
                        green = v
                        hexInput = formatHexColor(Color(red, v, blue))
                        hexError = false
                    },
                )
                RgbSlider(
                    label = stringResource(R.string.settings_blue),
                    value = blue,
                    accent = Color(0xFF3F6BD8),
                    onValueChange = { v ->
                        blue = v
                        hexInput = formatHexColor(Color(red, green, v))
                        hexError = false
                    },
                )
                if (initial != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.settings_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(preview) }) {
                Text(stringResource(R.string.settings_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun RgbSlider(
    label: String,
    value: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = (value * 255f).roundToInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
            ),
        )
    }
}

private fun formatHexColor(color: Color): String {
    val r = (color.red * 255f).roundToInt()
    val g = (color.green * 255f).roundToInt()
    val b = (color.blue * 255f).roundToInt()
    return "#%02X%02X%02X".format(r, g, b)
}

private const val HEX_DIGITS = "0123456789abcdefABCDEF"

private const val MAX_HEX_INPUT_LENGTH = 7

private fun parseHexColor(input: String): Color? {
    val hex = input.removePrefix("#").trim()
    if (hex.length != 6 || !hex.all { HEX_DIGITS.contains(it) }) return null
    val rgb = hex.toLongOrNull(radix = 16) ?: return null
    return Color((rgb or 0xFF000000L).toInt())
}

@Composable
internal fun RecentColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 22.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "recentDotSize",
    )

    val hex = color.toHexString()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = hex },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = 1.5.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contrastContentColor(color),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
internal fun AccentDot(
    option: AccentOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val name = stringResource(option.nameRes)
    val dotSize by animateDpAsState(
        targetValue = if (selected) 40.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "accentDotSize",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(option.color)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contrastContentColor(option.color),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SegmentedButtonRow(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        AppLocale.LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
        AppLocale.LANGUAGE_RU to stringResource(R.string.settings_language_ru),
        AppLocale.LANGUAGE_EN to stringResource(R.string.settings_language_en),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SpotifyIntegrationCard(
    settings: AppSettings,
    auth: SpotifyAuthState,
    onSave: (String, String) -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    var clientId by remember(settings.spotifyClientId) {
        mutableStateOf(settings.spotifyClientId)
    }
    var clientSecret by remember(settings.spotifyClientSecret) {
        mutableStateOf(settings.spotifyClientSecret)
    }
    val connected = auth.connected
    val displayName = auth.displayName?.takeIf { it.isNotBlank() } ?: "Spotify"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Spotify",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenGuide) {
                    Text(
                        text = stringResource(R.string.spotify_guide_open),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.settings_spotify_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text(stringResource(R.string.settings_spotify_client_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text(stringResource(R.string.settings_spotify_client_secret)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (connected || settings.isSpotifyConfigured) {
                        stringResource(R.string.settings_spotify_connected)
                    } else {
                        stringResource(R.string.settings_spotify_not_connected)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connected || settings.isSpotifyConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { onSave(clientId, clientSecret) }) {
                    Text(stringResource(R.string.settings_save))
                }
            }

            Spacer(Modifier.height(12.dp))
            if (connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackArtwork(
                        url = auth.imageUrl,
                        contentDescription = null,
                        size = 40,
                        cornerDp = 20,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.spotify_signed_in_as, displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onSignOut) {
                        Text(stringResource(R.string.spotify_sign_out))
                    }
                }
            } else {
                Button(
                    onClick = { onSignIn(clientId, clientSecret) },
                    enabled = clientId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Text(stringResource(R.string.spotify_sign_in))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.spotify_redirect_uri),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_spotify_quota_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun SpotifyGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spotify_guide_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GuideStep(
                    number = 1,
                    titleRes = R.string.spotify_guide_step1_title,
                    bodyRes = R.string.spotify_guide_step1_body,
                )
                GuideStep(
                    number = 2,
                    titleRes = R.string.spotify_guide_step2_title,
                    bodyRes = R.string.spotify_guide_step2_body,
                )
                GuideStep(
                    number = 3,
                    titleRes = R.string.spotify_guide_step3_title,
                    bodyRes = R.string.spotify_guide_step3_body,
                )
                GuideStep(
                    number = 4,
                    titleRes = R.string.spotify_guide_step4_title,
                    bodyRes = R.string.spotify_guide_step4_body,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.spotify_redirect_uri),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.spotify_guide_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_DASHBOARD_URL)),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.spotify_guide_open_dashboard))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun GuideStep(number: Int, titleRes: Int, bodyRes: Int) {
    Row(modifier = Modifier.padding(bottom = 12.dp)) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SPOTIFY_DASHBOARD_URL = "https://developer.spotify.com/dashboard"

private fun Color.toHexString(): String =
    "#%06X".format(0xFFFFFF and toArgb())
