package com.facet.music.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.facet.music.R
import com.facet.music.core.ui.theme.AccentOptions
import com.facet.music.core.ui.theme.FacetPalette
import com.facet.music.core.ui.theme.FacetShapeStyle
import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.ThemeMode
import kotlinx.coroutines.launch

private data class StudioPreset(
    val titleRes: Int,
    val palette: FacetPalette,
    val serif: Boolean,
    val shape: String,
    val amoled: Boolean,
    val themeMode: ThemeMode?,
    val swatches: List<Color>,
)

private val StudioPresets = listOf(
    StudioPreset(
        titleRes = R.string.palette_signature,
        palette = FacetPalette.SIGNATURE,
        serif = true,
        shape = "DEFAULT",
        amoled = false,
        themeMode = ThemeMode.SYSTEM,
        swatches = listOf(Color(0xFF0E0F12), Color(0xFFC8A46A), Color(0xFFF4F1EA)),
    ),
    StudioPreset(
        titleRes = R.string.palette_night_jazz,
        palette = FacetPalette.NIGHT_JAZZ,
        serif = true,
        shape = "SOFT",
        amoled = false,
        themeMode = ThemeMode.DARK,
        swatches = listOf(Color(0xFF141A2B), Color(0xFF6C7BC4), Color(0xFFE8B15C)),
    ),
    StudioPreset(
        titleRes = R.string.palette_paper,
        palette = FacetPalette.PAPER,
        serif = true,
        shape = "SOFT",
        amoled = false,
        themeMode = ThemeMode.LIGHT,
        swatches = listOf(Color(0xFFF7F4EE), Color(0xFF8A6A34), Color(0xFF1A1815)),
    ),
    StudioPreset(
        titleRes = R.string.palette_mono,
        palette = FacetPalette.MONO,
        serif = false,
        shape = "DEFAULT",
        amoled = false,
        themeMode = ThemeMode.SYSTEM,
        swatches = listOf(Color(0xFF1C1C1C), Color(0xFF8A8A8A), Color(0xFFF2F2F2)),
    ),
    StudioPreset(
        titleRes = R.string.palette_dynamic,
        palette = FacetPalette.DYNAMIC,
        serif = false,
        shape = "ROUND",
        amoled = false,
        themeMode = ThemeMode.SYSTEM,
        swatches = listOf(Color(0xFF3A4E6E), Color(0xFF7FA6C9), Color(0xFFEADCC8)),
    ),
)

@Composable
fun ThemeStudioScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val savedStyles by viewModel.savedStyles.collectAsState()
    val feedback by viewModel.studioFeedback.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSaveStyleDialog by remember { mutableStateOf(false) }
    var pendingDeleteStyle by remember { mutableStateOf<String?>(null) }

    val feedbackText = feedback?.let { context.getString(studioFeedbackText(it)) }
    LaunchedEffect(feedback) {
        val text = feedbackText ?: return@LaunchedEffect
        viewModel.consumeStudioFeedback()
        snackbarHostState.showSnackbar(text, duration = androidx.compose.material3.SnackbarDuration.Short)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioHeader(onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                item(key = "preview") {
                    StudioPreview(settings = settings)
                }

                item(key = "presets") {
                    StudioBlock(
                        title = stringResource(R.string.studio_presets),
                        hint = stringResource(R.string.studio_presets_hint),
                    ) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(StudioPresets, key = { it.titleRes }) { preset ->
                                PresetCard(
                                    preset = preset,
                                    selected = settings.paletteMode == preset.palette.name &&
                                        settings.serifDisplay == preset.serif &&
                                        settings.shapeStyle == preset.shape,
                                    onClick = {
                                        viewModel.applyPreset(
                                            palette = preset.palette.name,
                                            serif = preset.serif,
                                            shape = preset.shape,
                                            amoled = preset.amoled,
                                            themeMode = preset.themeMode,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "theme") {
                    StudioBlock(title = stringResource(R.string.studio_theme)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                ) {
                                    Text(
                                        text = when (mode) {
                                            ThemeMode.LIGHT ->
                                                stringResource(R.string.settings_theme_light)
                                            ThemeMode.DARK ->
                                                stringResource(R.string.settings_theme_dark)
                                            ThemeMode.SYSTEM ->
                                                stringResource(R.string.settings_theme_system)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StudioSwitchRow(
                            title = stringResource(R.string.settings_amoled),
                            hint = stringResource(R.string.settings_amoled_hint),
                            checked = settings.amoledBlack,
                            onCheckedChange = viewModel::setAmoledBlack,
                        )
                    }
                }

                item(key = "palette") {
                    StudioBlock(
                        title = stringResource(R.string.studio_palette),
                        hint = stringResource(R.string.settings_palette_hint),
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FacetPalette.entries.forEach { palette ->
                                FilterChip(
                                    selected = settings.paletteMode == palette.name &&
                                        !settings.accentEnabled,
                                    onClick = {
                                        viewModel.setPaletteMode(palette.name)
                                        if (settings.accentEnabled) {
                                            viewModel.setAccentEnabled(false)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(paletteLabel(palette)),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.studio_accent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccentOptions.forEach { option ->
                                AccentDot(
                                    option = option,
                                    selected = settings.accentEnabled &&
                                        settings.customAccentColor == option.color.toArgb(),
                                    onClick = { viewModel.setAccentColor(option.color) },
                                )
                            }
                            CustomColorDot(
                                color = settings.customAccentColor?.let { Color(it) },
                                selected = settings.accentEnabled &&
                                    settings.customAccentColor != null &&
                                    AccentOptions.none {
                                        it.color.toArgb() == settings.customAccentColor
                                    },
                                onClick = { showCustomColorDialog = true },
                            )
                        }
                    }
                }

                item(key = "type") {
                    StudioBlock(title = stringResource(R.string.studio_type)) {
                        StudioSwitchRow(
                            title = stringResource(R.string.settings_serif_display),
                            hint = stringResource(R.string.settings_serif_display_hint),
                            checked = settings.serifDisplay,
                            onCheckedChange = viewModel::setSerifDisplay,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_text_scale),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_text_scale_value,
                                    (settings.textScale * 100).toInt(),
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = settings.textScale,
                            onValueChange = viewModel::setTextScale,
                            valueRange = 0.85f..1.3f,
                            steps = 8,
                        )
                    }
                }

                item(key = "shape") {
                    StudioBlock(
                        title = stringResource(R.string.settings_shape_style),
                        hint = stringResource(R.string.studio_shape_hint),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FacetShapeStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = settings.shapeStyle == style.name,
                                    onClick = { viewModel.setShapeStyle(style.name) },
                                    label = {
                                        Text(
                                            text = stringResource(shapeStyleLabel(style)),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "player") {
                    StudioBlock(
                        title = stringResource(R.string.studio_player),
                        hint = stringResource(R.string.studio_player_hint),
                    ) {
                        StudioChipRow(
                            title = stringResource(R.string.studio_player_layout),
                            options = listOf(
                                AppSettings.LAYOUT_ARTWORK to R.string.studio_layout_artwork,
                                AppSettings.LAYOUT_SIDE to R.string.studio_layout_side,
                                AppSettings.LAYOUT_IMMERSIVE to R.string.studio_layout_immersive,
                            ),
                            selected = settings.playerLayout,
                            onSelect = viewModel::setPlayerLayout,
                        )
                        Spacer(Modifier.height(14.dp))
                        StudioChipRow(
                            title = stringResource(R.string.studio_cover_shape),
                            options = listOf(
                                AppSettings.COVER_AUTO to R.string.studio_cover_theme,
                                AppSettings.COVER_CIRCLE to R.string.studio_cover_circle,
                                AppSettings.COVER_SQUARE to R.string.studio_cover_square,
                            ),
                            selected = settings.coverShape,
                            onSelect = viewModel::setCoverShape,
                        )
                        Spacer(Modifier.height(14.dp))
                        StudioChipRow(
                            title = stringResource(R.string.studio_cover_size),
                            options = listOf(
                                AppSettings.COVER_MEDIUM to R.string.studio_cover_medium,
                                AppSettings.COVER_LARGE to R.string.studio_cover_large,
                            ),
                            selected = settings.coverSize,
                            onSelect = viewModel::setCoverSize,
                        )
                        Spacer(Modifier.height(14.dp))
                        StudioChipRow(
                            title = stringResource(R.string.studio_backdrop),
                            options = listOf(
                                AppSettings.BACKDROP_COVER to R.string.studio_backdrop_cover,
                                AppSettings.BACKDROP_SOLID to R.string.studio_backdrop_solid,
                            ),
                            selected = settings.playerBackdrop,
                            onSelect = viewModel::setPlayerBackdrop,
                        )
                        Spacer(Modifier.height(14.dp))
                        StudioChipRow(
                            title = stringResource(R.string.studio_dismiss),
                            options = listOf(
                                AppSettings.SENSITIVITY_LOW to R.string.studio_dismiss_low,
                                AppSettings.SENSITIVITY_MEDIUM to R.string.studio_dismiss_medium,
                                AppSettings.SENSITIVITY_HIGH to R.string.studio_dismiss_high,
                            ),
                            selected = settings.dismissSensitivity,
                            onSelect = viewModel::setDismissSensitivity,
                        )
                    }
                }

                item(key = "navigation") {
                    StudioBlock(
                        title = stringResource(R.string.studio_navigation),
                        hint = stringResource(R.string.studio_navigation_hint),
                    ) {
                        Text(
                            text = stringResource(R.string.studio_dock_style),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppSettings.DOCK_FLOATING to R.string.studio_dock_floating,
                                AppSettings.DOCK_ATTACHED to R.string.studio_dock_attached,
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = settings.dockStyle == value,
                                    onClick = { viewModel.setDockStyle(value) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.studio_dock_labels),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppSettings.LABELS_ALWAYS to R.string.studio_labels_always,
                                AppSettings.LABELS_SELECTED to R.string.studio_labels_selected,
                                AppSettings.LABELS_NEVER to R.string.studio_labels_never,
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = settings.dockLabels == value,
                                    onClick = { viewModel.setDockLabels(value) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.studio_tabs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        settings.tabOrder.forEachIndexed { index, route ->
                            TabsConfigRow(
                                label = stringResource(tabLabel(route)),
                                visible = route !in settings.hiddenTabs,
                                canMoveUp = index > 0,
                                canMoveDown = index < settings.tabOrder.lastIndex,
                                onMoveUp = { viewModel.moveTab(route, -1) },
                                onMoveDown = { viewModel.moveTab(route, 1) },
                                onVisibleChange = { visible ->
                                    viewModel.setTabVisible(route, visible)
                                },
                            )
                        }
                    }
                }

                item(key = "home") {
                    StudioBlock(
                        title = stringResource(R.string.studio_home),
                        hint = stringResource(R.string.studio_home_hint),
                    ) {
                        Text(
                            text = stringResource(R.string.studio_home_hero),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppSettings.HERO_EDITORIAL to R.string.studio_hero_editorial,
                                AppSettings.HERO_MINIMAL to R.string.studio_hero_minimal,
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = settings.homeHeroStyle == value,
                                    onClick = { viewModel.setHomeHeroStyle(value) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StudioSwitchRow(
                            title = stringResource(R.string.studio_home_wave_promo),
                            hint = stringResource(R.string.studio_home_wave_promo_hint),
                            checked = settings.homeShowWavePromo,
                            onCheckedChange = viewModel::setHomeShowWavePromo,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.studio_home_sections),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        settings.homeSectionOrder.forEachIndexed { index, section ->
                            TabsConfigRow(
                                label = stringResource(homeSectionLabel(section)),
                                visible = section !in settings.hiddenHomeSections,
                                canMoveUp = index > 0,
                                canMoveDown = index < settings.homeSectionOrder.lastIndex,
                                onMoveUp = { viewModel.moveHomeSection(section, -1) },
                                onMoveDown = { viewModel.moveHomeSection(section, 1) },
                                onVisibleChange = { visible ->
                                    viewModel.setHomeSectionVisible(section, visible)
                                },
                            )
                        }
                    }
                }

                item(key = "styles") {
                    StudioBlock(
                        title = stringResource(R.string.studio_styles),
                        hint = stringResource(R.string.studio_styles_hint),
                    ) {
                        if (savedStyles.isEmpty()) {
                            Text(
                                text = stringResource(R.string.studio_no_styles),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            savedStyles.forEach { name ->
                                SavedStyleRow(
                                    name = name,
                                    onApply = { viewModel.applyStyle(name) },
                                    onCopy = {
                                        val json = viewModel.exportStyleJson(name)
                                        if (json != null) {
                                            copyToClipboard(context, json)
                                        }
                                    },
                                    onDelete = { pendingDeleteStyle = name },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showSaveStyleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.studio_save_style))
                        }
                    }
                }

                item(key = "profile") {
                    StudioBlock(
                        title = stringResource(R.string.studio_profile),
                        hint = stringResource(R.string.studio_profile_hint),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    copyToClipboard(context, viewModel.exportProfileJson())
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.studio_profile_exported),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.studio_export_profile))
                            }
                            FilledTonalButton(
                                onClick = { showImportDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.studio_import_profile))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::resetAppearance,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.studio_reset))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initial = settings.customAccentColor?.let { Color(it) },
            onDismiss = { showCustomColorDialog = false },
            onApply = { color ->
                viewModel.setCustomAccentColor(color)
                showCustomColorDialog = false
            },
            onReset = {
                viewModel.setCustomAccentColor(null)
                showCustomColorDialog = false
            },
        )
    }

    if (showImportDialog) {
        ImportProfileDialog(
            onDismiss = { showImportDialog = false },
            onApply = { json ->
                viewModel.importProfileJson(json)
                showImportDialog = false
            },
        )
    }

    if (showSaveStyleDialog) {
        SaveStyleDialog(
            onDismiss = { showSaveStyleDialog = false },
            onSave = { name ->
                viewModel.saveStyle(name)
                showSaveStyleDialog = false
            },
        )
    }

    pendingDeleteStyle?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDeleteStyle = null },
            title = { Text(name) },
            text = { Text(stringResource(R.string.studio_delete_style_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStyle(name)
                        pendingDeleteStyle = null
                    },
                ) {
                    Text(stringResource(R.string.studio_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteStyle = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun StudioChipRow(
    title: String,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun StudioHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_studio_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_studio_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudioPreview(settings: AppSettings) {
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.home_greeting_evening),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.studio_preview_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.studio_preview_track),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.studio_preview_artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(
                    onClick = {},
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewDockItem(Icons.Outlined.Home, R.string.nav_home, selected = true, settings)
                PreviewDockItem(Icons.Rounded.Search, R.string.nav_search, selected = false, settings)
                PreviewDockItem(
                    Icons.Rounded.LibraryMusic,
                    R.string.nav_library,
                    selected = false,
                    settings,
                )
                PreviewDockItem(Icons.Rounded.Waves, R.string.nav_wave, selected = false, settings)
            }
        }
    }
}

@Composable
private fun PreviewDockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    selected: Boolean,
    settings: AppSettings,
) {
    val showLabel = when (settings.dockLabels) {
        AppSettings.LABELS_NEVER -> false
        AppSettings.LABELS_SELECTED -> selected
        else -> true
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 26.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StudioBlock(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun StudioSwitchRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PresetCard(
    preset: StudioPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary,
            )
        } else {
            null
        },
        modifier = Modifier.width(128.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                preset.swatches.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(preset.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsConfigRow(
    label: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.studio_move_up),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Rounded.ArrowDownward,
                contentDescription = stringResource(R.string.studio_move_down),
                modifier = Modifier.size(18.dp),
            )
        }
        Switch(checked = visible, onCheckedChange = onVisibleChange)
    }
}

@Composable
private fun SavedStyleRow(
    name: String,
    onApply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onApply),
        )
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.studio_export_style),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.studio_delete),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ImportProfileDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_import_profile)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.studio_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("{ \"schemaVersion\": 1, ... }") },
                    minLines = 5,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(input) },
                enabled = input.isNotBlank(),
            ) {
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
private fun SaveStyleDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_save_style)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.studio_style_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.studio_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("Facet appearance", text))
}

private fun tabLabel(route: String): Int = when (route) {
    "home" -> R.string.nav_home
    "search" -> R.string.nav_search
    "library" -> R.string.nav_library
    "wave" -> R.string.nav_wave
    else -> R.string.nav_home
}

private fun homeSectionLabel(section: String): Int = when (section) {
    AppSettings.SECTION_HERO -> R.string.studio_section_hero
    AppSettings.SECTION_WAVE -> R.string.studio_section_wave
    AppSettings.SECTION_NEW_RELEASES -> R.string.studio_section_new_releases
    AppSettings.SECTION_CHARTS -> R.string.studio_section_charts
    AppSettings.SECTION_GENRES -> R.string.studio_section_genres
    AppSettings.SECTION_RECOMMENDATIONS -> R.string.studio_section_recommendations
    AppSettings.SECTION_RECENT -> R.string.studio_section_recent
    AppSettings.SECTION_MEMORIES -> R.string.studio_section_memories
    else -> R.string.studio_section_hero
}

private fun studioFeedbackText(feedback: StudioFeedback): Int = when (feedback) {
    StudioFeedback.PROFILE_EXPORTED -> R.string.studio_profile_exported
    StudioFeedback.PROFILE_IMPORTED -> R.string.studio_profile_imported
    StudioFeedback.PROFILE_INVALID -> R.string.studio_profile_invalid
    StudioFeedback.PROFILE_RESET -> R.string.studio_profile_reset
    StudioFeedback.STYLE_SAVED -> R.string.studio_style_saved
    StudioFeedback.STYLE_APPLIED -> R.string.studio_style_applied
    StudioFeedback.STYLE_DELETED -> R.string.studio_style_deleted
    StudioFeedback.INVALID_NAME -> R.string.studio_invalid_name
    StudioFeedback.LAST_TAB_REQUIRED -> R.string.studio_last_tab_required
}
