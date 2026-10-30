package com.facet.music.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.facet.music.R
import com.facet.music.core.download.DownloadState

/**
 * Компактная кнопка скачивания для карточек треков.
 *
 * Показывает четыре состояния: стрелка, кольцо прогресса, галочка и крестик.
 * Используется в «Для вас», «Недавнем», «Воспоминаниях» и медиатеке,
 * чтобы скачать трек можно было без открытия плеера.
 */
@Composable
fun TrackDownloadButton(
    state: DownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val progress = (state as? DownloadState.InProgress)?.progress ?: 0f
    val tint = when (state) {
        is DownloadState.Downloaded -> MaterialTheme.colorScheme.primary
        is DownloadState.Error -> MaterialTheme.colorScheme.error
        else -> contentColor
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                if (state !is DownloadState.Downloaded) onClick()
            },
            modifier = Modifier.size(size),
        ) {
            when (state) {
                is DownloadState.Downloaded -> Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = stringResource(R.string.track_downloaded_cd),
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )

                is DownloadState.InProgress -> if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = tint.copy(alpha = 0.2f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is DownloadState.Error -> Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.track_download_failed_cd),
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )

                DownloadState.NotDownloaded -> Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.track_download_cd),
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
