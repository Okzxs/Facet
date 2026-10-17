package com.my.music.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.data.model.Track

/**
 * Переиспользуемая карточка трека — горизонтальный layout.
 * Используется в поиске, главной, библиотеке.
 */
@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(track.artist)
                    if (!track.genre.isNullOrBlank()) append(" · ${track.genre}")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            TrackArtwork(
                url = track.artworkUrl,
                contentDescription = "${track.title} обложка",
            )
        },
        trailingContent = trailingContent,
    )
}

@Composable
fun TrackArtwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 52,
) {
    val shape = RoundedCornerShape(8.dp)
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val notePainter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val placeholderPainter = remember(surfaceColor) { ColorPainter(surfaceColor) }

    Surface(
        modifier = modifier.size(size.dp),
        shape = shape,
        color = surfaceColor,
    ) {
        if (!url.isNullOrBlank()) {
            val imageRequest = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(200)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = notePainter,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = notePainter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((size / 2).dp),
                )
            }
        }
    }
}

/** Форматирование длительности: мс → "3:22" */
fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
