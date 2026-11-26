package com.facet.music.core.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object CloudSpacing {
    val Screen: Dp = 24.dp
    val Section: Dp = 32.dp
    val Item: Dp = 12.dp
    val Card: Dp = 16.dp
    val CardWide: Dp = 20.dp
    val Header: Dp = 28.dp
}

@Composable
fun Modifier.cloudPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "cloudPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun CloudReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 14) * 40).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = Motion.MediumMillis,
                easing = Motion.EmphasizedDecelerate,
            ),
        ) + slideInVertically(
            animationSpec = Motion.reveal(),
            initialOffsetY = { it / 6 },
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = Motion.ShortMillis,
                easing = Motion.EmphasizedAccelerate,
            ),
        ),
        content = { content() },
    )
}
