package com.facet.music.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facet.music.R
import com.facet.music.core.player.RepeatMode

/** Пружинный press-scale (0.90 при нажатии) — общий микро-жест Expressive-кнопок. */
@Composable
fun rememberPressScale(interactionSource: InteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

/**
 * Плавающий «остров» управления плеером:
 * - каплевидная асимметричная форма и цветная тень от палитры;
 * - внутри: слот прогресса, транспорт (шаффл, пред/плей/след, повтор)
 *   и необязательный ряд действий с подписями под Play.
 */
@Composable
fun PlayerControlIsland(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    borderColor: Color,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    actionsContent: (@Composable RowScope.() -> Unit)? = null,
    progressContent: @Composable () -> Unit,
) {
    val islandShape = MaterialTheme.shapes.extraLarge

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = islandShape,
            color = containerColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                progressContent()

                // Запас между таймлайном и рядом кнопок: палец, целящийся в Play/Pause,
                // не должен задевать зону перемотки
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IslandToggle(
                        icon = Icons.Filled.Shuffle,
                        active = shuffleEnabled,
                        contentDescription = stringResource(R.string.queue_shuffle_cd),
                        accentColor = accentColor,
                        contentColor = contentColor,
                        onClick = onToggleShuffle,
                    )

                    TransportButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.common_previous_cd),
                        contentColor = contentColor,
                        onClick = onPrev,
                    )

                    PlayMorphButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        onClick = onPlayPause,
                    )

                    TransportButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.common_next_cd),
                        contentColor = contentColor,
                        onClick = onNext,
                    )

                    IslandToggle(
                        icon = if (repeatMode == RepeatMode.ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        active = repeatMode != RepeatMode.OFF,
                        contentDescription = stringResource(R.string.queue_repeat_cd),
                        accentColor = accentColor,
                        contentColor = contentColor,
                        onClick = onToggleRepeat,
                    )
                }

                if (actionsContent != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                        content = actionsContent,
                    )
                }
            }
        }
    }
}

/** Кнопка транспорта 48dp без фона: пред/след не спорят с Play. */
@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    FilledIconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Малый переключатель транспорта (шаффл/повтор): 40dp, в активном состоянии —
 * тональная подложка акцента. Нажатие пружинит, как и остальные кнопки острова.
 */
@Composable
private fun IslandToggle(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = if (active) accentColor.copy(alpha = 0.18f) else Color.Transparent,
        contentColor = if (active) accentColor else contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * Действие плеера: тональная иконка и подпись снизу.
 *
 * Ряд из пяти таких кнопок живёт под Play и заменяет прежние сквирклы над
 * островом: лайк, скачивание, текст, очередь и вариации теперь в одном месте.
 * Активное состояние красится контейнером и акцентом обложки, загрузка
 * показывается кольцом прогресса вместо иконки.
 */
@Composable
fun PlayerActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    activeContainerColor: Color,
    idleContainerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    contentDescription: String = label,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) activeContainerColor else idleContainerColor,
                contentColor = if (active) accentColor else MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = accentColor,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Главная кнопка Play/Pause: морф радиуса (круг в паузе / сквиркл в игре) + press-scale. */
@Composable
private fun PlayMorphButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    accentColor: Color,
    onAccentColor: Color,
    onClick: () -> Unit,
) {
    val radius by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 32.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "playMorphRadius",
    )
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    FilledIconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(radius),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = accentColor,
            contentColor = onAccentColor,
        ),
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = onAccentColor,
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.common_pause_cd)
                } else {
                    stringResource(R.string.common_play_cd)
                },
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
