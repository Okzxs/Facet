package com.my.music.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * - каплевидная асимметричная форма (44dp сверху, 22dp снизу) и цветная тень от палитры;
 * - к верхней кромке «прирос» язычок режима текста, выступающий над островом;
 * - внутри: слот прогресса и ряд — скорость, пред/плей/след, очередь.
 */
@Composable
fun PlayerControlIsland(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playbackSpeed: Float,
    isLyricsActive: Boolean,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    lyricsActiveContainer: Color,
    lyricsActiveContent: Color,
    borderColor: Color,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeedClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                modifier = Modifier.padding(16.dp),
            ) {
                progressContent()

                // Запас между волной и рядом кнопок: палец, целящийся в Play/Pause,
                // не должен задевать зону перемотки
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val speedInteraction = remember { MutableInteractionSource() }
                    val speedScale = rememberPressScale(speedInteraction)
                    Surface(
                        onClick = onSpeedClick,
                        interactionSource = speedInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = speedScale
                                scaleY = speedScale
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = contentColor,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (playbackSpeed == 1.0f) "1×" else "${playbackSpeed}×",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }

                    val prevInteraction = remember { MutableInteractionSource() }
                    val prevScale = rememberPressScale(prevInteraction)
                    FilledIconButton(
                        onClick = onPrev,
                        interactionSource = prevInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = prevScale
                                scaleY = prevScale
                            },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = contentColor,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Предыдущий",
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    PlayMorphButton(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        onClick = onPlayPause,
                    )

                    val nextInteraction = remember { MutableInteractionSource() }
                    val nextScale = rememberPressScale(nextInteraction)
                    FilledIconButton(
                        onClick = onNext,
                        interactionSource = nextInteraction,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = nextScale
                                scaleY = nextScale
                            },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = contentColor,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Следующий",
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    val queueInteraction = remember { MutableInteractionSource() }
                    val queueScale = rememberPressScale(queueInteraction)
                    Surface(
                        onClick = onQueueClick,
                        interactionSource = queueInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer {
                                scaleX = queueScale
                                scaleY = queueScale
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = contentColor,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Очередь",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

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
                contentDescription = if (isPlaying) "Пауза" else "Играть",
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Язычок-переключатель режима текста: свешивается с верхней кромки острова. */
@Composable
private fun LyricsTab(
    isActive: Boolean,
    activeContainer: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveContent: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (isActive) activeContainer else inactiveContainer,
        animationSpec = tween(durationMillis = 220),
        label = "lyricsTabContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isActive) activeContent else inactiveContent,
        animationSpec = tween(durationMillis = 220),
        label = "lyricsTabContent",
    )
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 6.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lyrics,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Текст",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
