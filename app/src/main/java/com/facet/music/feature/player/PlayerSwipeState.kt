package com.facet.music.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
class PlayerSwipeState(private val scope: CoroutineScope) {

    var offsetPx by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set
    var isDismissed by mutableStateOf(false)
        private set
    var screenHeightPx by mutableFloatStateOf(0f)
        private set
    var screenWidthPx by mutableFloatStateOf(0f)
        private set

    private var settleJob: Job? = null

    val progress: Float
        get() {
            val height = screenHeightPx
            return if (height > 0f) {
                (offsetPx / height).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    fun onSizeChanged(width: Int, height: Int) {
        screenWidthPx = width.toFloat()
        screenHeightPx = height.toFloat()
    }

    fun beginDrag() {
        settleJob?.cancel()
        isDragging = true
    }

    fun dragTo(px: Float) {
        val height = screenHeightPx
        offsetPx = if (height > 0f) px.coerceIn(0f, height) else px.coerceAtLeast(0f)
    }

    fun settle(velocity: Float = 0f) {
        isDragging = false
        settleJob?.cancel()
        val start = offsetPx
        if (start <= 0f) {
            offsetPx = 0f
            return
        }
        settleJob = scope.launch {
            animate(
                initialValue = start,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessLow,
                ),
            ) { value, _ ->
                offsetPx = value
            }
        }
    }

    fun dismiss() {
        if (isDismissed) return
        settleJob?.cancel()
        isDragging = false
        isDismissed = true
    }

    fun reset() {
        settleJob?.cancel()
        isDragging = false
        isDismissed = false
        offsetPx = 0f
    }
}
