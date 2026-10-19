package com.my.music.feature.wave

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class WaveSpec(
    val heightFraction: Float,
    val amplitudeDp: Float,
    val frequency: Float,
    val phaseOffset: Float,
    val color: Color,
    val strokeDp: Float,
)

private val waveMoods = listOf("Бодрая", "Спокойная", "По любимым", "Открытия")

@Composable
fun WaveScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + slideInVertically(
            initialOffsetY = { it / 14 },
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessLow,
            ),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            WaveHeader()

            WaveHero(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            )

            MoodPreview()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Волна уже настраивается",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Лайки и история прослушиваний станут основой персонального потока. Релиз — в ближайшем обновлении.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Моя волна",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                ),
                modifier = Modifier.weight(1f),
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "СКОРО",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = "Бесконечный поток, который понимает твой вкус",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveHero(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val outlineColor = MaterialTheme.colorScheme.onPrimaryContainer

    val waves = remember(primary, tertiary, secondary) {
        listOf(
            WaveSpec(
                heightFraction = 0.60f,
                amplitudeDp = 16f,
                frequency = 1.15f,
                phaseOffset = 0f,
                color = primary.copy(alpha = 0.85f),
                strokeDp = 2.5f,
            ),
            WaveSpec(
                heightFraction = 0.73f,
                amplitudeDp = 22f,
                frequency = 0.85f,
                phaseOffset = 1.7f,
                color = tertiary.copy(alpha = 0.55f),
                strokeDp = 2f,
            ),
            WaveSpec(
                heightFraction = 0.84f,
                amplitudeDp = 12f,
                frequency = 1.55f,
                phaseOffset = 3.1f,
                color = secondary.copy(alpha = 0.45f),
                strokeDp = 1.6f,
            ),
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveMotion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wavePulse",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Орб ровно по центру hero-зоны, чтобы иконка волны (Alignment.Center) стояла в его геометрическом центре
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.17f * pulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 2.8f,
                ),
                radius = baseRadius * 2.8f,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryContainer,
                        primary.copy(alpha = 0.72f),
                    ),
                    center = Offset(center.x - baseRadius * 0.28f, center.y - baseRadius * 0.32f),
                    radius = baseRadius * 1.6f,
                ),
                radius = baseRadius,
                center = center,
            )

            drawCircle(
                color = outlineColor.copy(alpha = 0.30f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            drawCircle(
                color = primary.copy(alpha = 0.16f),
                radius = baseRadius * 1.36f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            waves.forEach { spec ->
                val path = Path()
                val waveY = size.height * spec.heightFraction
                val amplitude = spec.amplitudeDp.dp.toPx() * (0.75f + 0.25f * pulse)
                val step = 5f
                var x = 0f
                var first = true
                while (x <= size.width) {
                    val progress = x / size.width
                    val envelope = sin(progress * PI).toFloat()
                    val y = waveY + sin(progress * 2f * PI.toFloat() * spec.frequency + phase + spec.phaseOffset) *
                        amplitude * envelope
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += step
                }
                drawPath(
                    path = path,
                    color = spec.color,
                    style = Stroke(
                        width = spec.strokeDp.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            repeat(8) { index ->
                val angle = phase * 0.35f + index * (2f * PI.toFloat() / 8f)
                val radius = baseRadius * (1.55f + 0.18f * (index % 3))
                val particle = Offset(
                    x = center.x + cos(angle) * radius,
                    y = center.y + sin(angle) * radius * 0.55f,
                )
                drawCircle(
                    color = if (index % 2 == 0) primary.copy(alpha = 0.45f)
                    else tertiary.copy(alpha = 0.38f),
                    radius = 2.4.dp.toPx(),
                    center = particle,
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.Waves,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun MoodPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = "НАСТРОЕНИЕ ВОЛНЫ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))
        val dotColors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = waveMoods,
                key = { _, mood -> mood },
            ) { index, mood ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.alpha(0.72f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColors[index]),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mood,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
