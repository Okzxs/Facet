package com.facet.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import android.text.format.DateFormat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.facet.music.R
import com.facet.music.core.download.DownloadMessages
import com.facet.music.core.player.EqualizerCurve
import com.facet.music.data.repository.AppSettings
import com.facet.music.core.player.EqualizerPreset
import com.facet.music.core.download.DownloadState
import com.facet.music.core.location.LocationPermissions
import com.facet.music.core.ui.components.ExpressivePlayerSlider
import com.facet.music.core.ui.components.PlayerControlIsland
import com.facet.music.core.ui.components.PlayerQueueSheet
import com.facet.music.core.ui.components.PlayerSliderState
import com.facet.music.core.ui.components.AddToPlaylistSheet
import com.facet.music.core.ui.components.SleepTimerSheet
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.PillShape
import com.facet.music.core.ui.theme.bestContentColor
import com.facet.music.core.ui.theme.contrastContentColor
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.facet.music.core.ui.theme.PlayerDynamicColors
import com.facet.music.core.ui.theme.rememberPlayerDynamicColors
import com.facet.music.core.player.PlaybackTuning
import com.facet.music.core.player.PlaybackVariation
import com.facet.music.data.model.LyricsResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.isMetadataOnly
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

@Composable
internal fun TuningDialog(
    selectedHz: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tuning_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tuning_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PlaybackTuning.FREQUENCIES.forEach { hz ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hz) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = hz == selectedHz,
                            onClick = { onSelect(hz) },
                        )
                        Text(
                            text = if (hz == PlaybackTuning.REFERENCE_HZ.toInt()) {
                                stringResource(R.string.tuning_standard, hz)
                            } else {
                                "$hz Hz"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsProviderSheet(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.player_lyrics_source_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            val options = listOf(
                null to R.string.player_lyrics_source_auto,
                "BetterLyrics" to R.string.player_lyrics_source_better,
                "LyricsPlus" to R.string.player_lyrics_source_plus,
                "LRCLIB" to R.string.player_lyrics_source_lrclib,
            )
            options.forEach { (mode, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            onSelect(mode)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = current == mode,
                        onClick = {
                            onSelect(mode)
                            onDismiss()
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    enabled: Boolean,
    preset: String,
    customBands: List<Float>,
    skipSilence: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelect: (String) -> Unit,
    onCustomBandsChange: (List<Float>) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var customExpanded by remember { mutableStateOf(preset == EqualizerCurve.CUSTOM_PRESET) }
    var bands by remember(customBands) { mutableStateOf(customBands) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.player_equalizer),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.equalizer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.equalizer_enabled),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(Modifier.height(8.dp))
            SoundToggleRow(
                title = stringResource(R.string.equalizer_skip_silence),
                hint = stringResource(R.string.equalizer_skip_silence_hint),
                checked = skipSilence,
                onCheckedChange = onSkipSilenceChange,
            )
            Spacer(Modifier.height(8.dp))
            EqualizerPreset.entries.forEach { option ->
                val selected = option.name == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = enabled) { onPresetSelect(option.name) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { if (enabled) onPresetSelect(option.name) },
                        enabled = enabled,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(equalizerPresetLabel(option)),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = EqualizerCurve.bandsForPreset(option).joinToString(" · ") { gain ->
                                val rounded = gain.toInt()
                                if (rounded > 0) "+$rounded" else "$rounded"
                            } + " dB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = enabled) {
                        if (preset != EqualizerCurve.CUSTOM_PRESET) {
                            onCustomBandsChange(
                                EqualizerCurve.bandsForPreset(EqualizerPreset.fromName(preset)),
                            )
                        }
                        customExpanded = !customExpanded
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = preset == EqualizerCurve.CUSTOM_PRESET,
                    onClick = {
                        if (!enabled) return@RadioButton
                        if (preset != EqualizerCurve.CUSTOM_PRESET) {
                            onCustomBandsChange(
                                EqualizerCurve.bandsForPreset(EqualizerPreset.fromName(preset)),
                            )
                        }
                        customExpanded = true
                    },
                    enabled = enabled,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.equalizer_custom),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = stringResource(R.string.equalizer_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = customExpanded) {
                Column {
                    EqualizerCurve.BAND_CENTERS_HZ.forEachIndexed { index, hz ->
                        val gain = bands.getOrNull(index) ?: 0f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_band_label, hz),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(62.dp),
                            )
                            Slider(
                                value = gain,
                                onValueChange = { value ->
                                    bands = bands.toMutableList().also { it[index] = value }
                                },
                                onValueChangeFinished = { onCustomBandsChange(bands) },
                                valueRange = AppSettings.EQ_MIN_DB..AppSettings.EQ_MAX_DB,
                                steps = 11,
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDb(gain),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDb(gain: Float): String {
    val rounded = gain.toInt()
    return if (rounded > 0) "+$rounded" else "$rounded"
}

@Composable
private fun SoundToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

internal fun equalizerPresetLabel(preset: EqualizerPreset): Int = when (preset) {
    EqualizerPreset.FLAT -> R.string.equalizer_preset_flat
    EqualizerPreset.BASS -> R.string.equalizer_preset_bass
    EqualizerPreset.VOCAL -> R.string.equalizer_preset_vocal
    EqualizerPreset.ROCK -> R.string.equalizer_preset_rock
    EqualizerPreset.ELECTRONIC -> R.string.equalizer_preset_electronic
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VariationsSheet(
    selected: PlaybackVariation,
    onSelect: (PlaybackVariation) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.variations_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            PlaybackVariation.entries.forEach { variation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(variation) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = variation == selected,
                        onClick = { onSelect(variation) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(variationLabelRes(variation)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "×${variation.speed} · pitch ${variation.pitch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal fun variationLabelRes(variation: PlaybackVariation): Int = when (variation) {
    PlaybackVariation.NORMAL -> R.string.variation_normal
    PlaybackVariation.SPEED_UP -> R.string.variation_speed_up
    PlaybackVariation.SLOWED -> R.string.variation_slowed
    PlaybackVariation.NIGHTCORE -> R.string.variation_nightcore
}
