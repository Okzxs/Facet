package com.facet.music.core.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "cloudPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = Motion.press(),
        label = "cloudPressAlpha",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

@Composable
fun CloudReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val offsetPx = with(density) { 28.dp.roundToPx() }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 14) * 55).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 320,
                easing = Motion.EmphasizedDecelerate,
            ),
        ) + slideInVertically(
            animationSpec = Motion.reveal(),
            initialOffsetY = { offsetPx },
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

@Composable
fun Modifier.cloudSheetEntrance(): Modifier {
    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, Motion.bounce()) }
            launch { alpha.animateTo(1f, tween(200)) }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}
