package com.facet.music.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facet.music.R
import com.facet.music.data.model.CanonicalTrack
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSourcesSheet(
    canonical: CanonicalTrack?,
    currentTrackId: String?,
    isLoading: Boolean,
    onPlay: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.sources_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (canonical != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${canonical.artist} · ${canonical.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }

                canonical == null || canonical.variants.isEmpty() -> Text(
                    text = stringResource(R.string.sources_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(canonical.variants, key = { it.track.id }) { variant ->
                        SourceVariantRow(
                            track = variant.track,
                            isCurrent = variant.track.id == currentTrackId,
                            onPlay = { onPlay(variant.track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceVariantRow(
    track: Track,
    isCurrent: Boolean,
    onPlay: () -> Unit,
) {
    val container = if (isCurrent) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                url = track.artworkUrl,
                contentDescription = null,
                size = 44,
                cornerDp = 10,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.source.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = variantSubtitle(track),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isCurrent) {
                FilledTonalButton(
                    onClick = onPlay,
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.sources_sheet_play),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.sources_sheet_current),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun variantSubtitle(track: Track): String {
    val parts = mutableListOf<String>()
    if (track.durationMs > 0) parts += formatDuration(track.durationMs)
    when {
        track.isSubscriptionOnly -> parts += stringResource(R.string.sources_quality_subscription)
        track.previewSeconds != null -> parts += stringResource(
            R.string.sources_quality_preview,
            track.previewSeconds,
        )
        track.streamUrl != null || track.transcodingUrl != null ->
            parts += stringResource(R.string.sources_quality_full)
    }
    return parts.joinToString(" · ")
}

fun Source.displayName(): String = when (this) {
    Source.SOUNDCLOUD -> "SoundCloud"
    Source.YOUTUBE -> "YouTube"
    Source.SPOTIFY -> "Spotify"
    Source.DEEZER -> "Deezer"
    Source.YANDEX -> "Yandex Music"
    Source.VK -> "VK Music"
    Source.APPLE -> "Apple Music"
    Source.LOCAL -> "Local"
}
