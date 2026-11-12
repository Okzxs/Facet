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

/** Визуальные состояния кнопки: прогресс не перезапускает анимацию иконки. */
private enum class DownloadButtonKind {
    Download,
    Progress,
    Done,
    Error,
}

private fun DownloadState.toButtonKind(): DownloadButtonKind = when (this) {
    is DownloadState.Downloaded -> DownloadButtonKind.Done
    is DownloadState.InProgress -> DownloadButtonKind.Progress
    is DownloadState.Error -> DownloadButtonKind.Error
    DownloadState.NotDownloaded -> DownloadButtonKind.Download
}

/**
 * Кнопка скачивания 46dp: стрелка → прогресс → галочка,
 * при отмене/ошибке — крестик, который через секунду морфится обратно.
 */
@Composable
internal fun DownloadSquircle(
    state: DownloadState,
    primaryColor: Color,
    primaryContainerColor: Color,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onClick: () -> Unit,
) {
    val kind = state.toButtonKind()
    val progress = (state as? DownloadState.InProgress)?.progress ?: 0f
    val contentColor = when (kind) {
        DownloadButtonKind.Done -> primaryColor
        DownloadButtonKind.Error -> MaterialTheme.colorScheme.error
        DownloadButtonKind.Download, DownloadButtonKind.Progress ->
            MaterialTheme.colorScheme.onSurface
    }
    val containerColor = when (kind) {
        DownloadButtonKind.Done -> primaryContainerColor
        DownloadButtonKind.Error ->
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        DownloadButtonKind.Download, DownloadButtonKind.Progress -> idleContainerColor
    }

    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
            },
            label = "downloadIcon",
        ) { target ->
            when (target) {
                DownloadButtonKind.Download -> Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.player_download_cd),
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Progress -> if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = primaryColor,
                    )
                }

                DownloadButtonKind.Done -> Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = stringResource(R.string.player_download_done_cd),
                    modifier = Modifier.size(22.dp),
                )

                DownloadButtonKind.Error -> Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.player_download_cancelled_cd),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Лайк-сквиркл 46dp с тональным контейнером в цветах плеера. */
@Composable
internal fun LikeSquircle(
    isLiked: Boolean,
    primaryColor: Color,
    primaryContainerColor: Color,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onToggleLike: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onToggleLike,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isLiked) primaryContainerColor else idleContainerColor,
            contentColor = if (isLiked) primaryColor else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.player_like_cd),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Дизлайк-сквиркл 46dp: красный, пока трек исключён из рекомендаций. */
@Composable
internal fun DislikeSquircle(
    isDisliked: Boolean,
    idleContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    onToggleDislike: () -> Unit,
) {
    val error = MaterialTheme.colorScheme.error
    FilledTonalIconButton(
        onClick = onToggleDislike,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isDisliked) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                idleContainerColor
            },
            contentColor = if (isDisliked) error else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.ThumbDown,
            contentDescription = if (isDisliked) {
                stringResource(R.string.player_undislike_cd)
            } else {
                stringResource(R.string.player_dislike_cd)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}
