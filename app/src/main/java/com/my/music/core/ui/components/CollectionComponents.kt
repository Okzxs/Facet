package com.my.music.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.my.music.R
import kotlin.math.abs

/**
 * Заголовок секции коллекции — общий ритм для медиатеки, артиста и плейлиста.
 *
 * Справа допускается одно действие: иконка с подписью (например «Импорт»).
 * Заголовок занимает всё свободное место и обрезается, поэтому длинные
 * названия не выдавливают кнопку за экран.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    if (!actionLabel.isNullOrBlank()) Spacer(Modifier.width(6.dp))
                }
                if (!actionLabel.isNullOrBlank()) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Универсальное пустое состояние: squircle-плашка с иконкой, заголовок,
 * пояснение и необязательное действие.
 *
 * Один компонент на все экраны — раньше каждая вкладка рисовала свою копию
 * с чуть другими отступами и размерами шрифта.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onAction != null && actionLabel != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onAction, shape = CircleShape) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Крупная карточка-«чипс» коллекции: градиентная плашка с иконкой, названием,
 * подписью и стрелкой. Используется для «Любимых треков» в медиатеке.
 *
 * Цвета берутся из контейнерных ролей темы, поэтому акцент пользователя и
 * динамические цвета соблюдаются автоматически; текст красится в `on*Container`,
 * а не в `onSurface`, иначе на светлой плашке он бы терялся.
 */
@Composable
fun CollectionChipCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = MaterialTheme.colorScheme.primaryContainer
    val end = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val shape = MaterialTheme.shapes.large

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(start, end)))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * Обложка плейлиста: первая обложка трека либо тональный градиент от названия.
 *
 * Градиент детерминированный (хэш имени), поэтому у плейлиста без обложки
 * всегда один и тот же цвет — и не нужно хранить его в базе. Второй цвет
 * сдвинут по палитре контейнеров, чтобы соседние плитки различались.
 */
@Composable
fun PlaylistCover(
    name: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
) {
    val seed = abs(name.hashCode())
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    val first = palette[seed % palette.size]
    val second = palette[(seed / 7 + 1) % palette.size]
    val shape = RoundedCornerShape(corner)
    val context = LocalContext.current
    val placeholder = rememberVectorPainter(Icons.Rounded.MusicNote)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(first, second))),
        contentAlignment = Alignment.Center,
    ) {
        if (!coverUrl.isNullOrBlank()) {
            val request = ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(200)
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = placeholder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * Плитка плейлиста для сетки медиатеки: обложка, название и счётчик треков.
 * Нажатие открывает экран плейлиста.
 */
@Composable
fun PlaylistTile(
    playlistName: String,
    coverUrl: String?,
    trackCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        PlaylistCover(
            name = playlistName,
            coverUrl = coverUrl,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlistName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralTrackCount(trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Подпись «N треков» с плюрализацией — плитки не могут вызвать stringResource из Column. */
@Composable
private fun pluralTrackCount(count: Int): String =
    pluralStringResource(R.plurals.track_count, count, count)

/** Сколько треков и плейлистов: общая подпись шапки коллекции. */
@Composable
fun CollectionSummary(trackCount: Int, playlistCount: Int): String {
    val tracks = pluralStringResource(R.plurals.track_count, trackCount, trackCount)
    val playlists = pluralStringResource(R.plurals.playlist_count, playlistCount, playlistCount)
    return "$tracks · $playlists"
}
