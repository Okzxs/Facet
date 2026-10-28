package com.my.music.feature.player

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

/**
 * Состояние свайпа полноэкранного плеера, поднятое в AppNavGraph.
 *
 * Нужно, чтобы блюр и смещение обложки можно было читать внутри
 * `graphicsLayer { }` в навигационном графе и в плеере без рекомпозиции:
 * анимация обновляет только слой на RenderThread, а не пересобирает
 * тяжёлые панели с текстом и кнопками.
 *
 * Смещение — обычный [mutableFloatStateOf], а не [androidx.compose.animation.core.Animatable]:
 * `snapTo` у Animatable — suspend-функция, а вызывать её из `awaitEachGesture`
 * нельзя (restricted suspend scope). Пружина после отпускания запускается
 * отдельной корутиной и пишет в то же состояние; повторный захват пальцем
 * отменяет её через [settleJob].
 */
@Stable
class PlayerSwipeState(private val scope: CoroutineScope) {

    /** Смещение плеера вниз в пикселях. */
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

    /** Пружина возврата на место; отменяется новым касанием. */
    private var settleJob: Job? = null

    /** Доля высоты экрана, на которую уехал плеер. Читать только в layer-блоках. */
    val progress: Float
        get() {
            val height = screenHeightPx
            return if (height > 0f) {
                (offsetPx / height).coerceIn(0f, 1f)
            } else {
                0f
            }
        }

    /** Радиус motion blur: до [MAX_BLUR_RADIUS] на полном свайпе. */
    val blurRadius: Float
        get() = progress * MAX_BLUR_RADIUS

    fun onSizeChanged(width: Int, height: Int) {
        screenWidthPx = width.toFloat()
        screenHeightPx = height.toFloat()
    }

    fun beginDrag() {
        settleJob?.cancel()
        isDragging = true
    }

    /** Вызывается прямо из жеста: без suspend, иначе не пройдёт restricted scope. */
    fun dragTo(px: Float) {
        val height = screenHeightPx
        offsetPx = if (height > 0f) px.coerceIn(0f, height) else px.coerceAtLeast(0f)
    }

    /** Отпускание ниже порога: пружиной возвращаем плеер на место. */
    fun settle() {
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
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessLow,
                ),
            ) { value, _ ->
                offsetPx = value
            }
        }
    }

    /** Порог пройден: экран закрывается, ручной свайп дальше не нужен. */
    fun dismiss() {
        if (isDismissed) return
        settleJob?.cancel()
        isDragging = false
        isDismissed = true
    }

    /** Новое открытие плеера: прежний свайп не должен переезжать в новый сеанс. */
    fun reset() {
        settleJob?.cancel()
        isDragging = false
        isDismissed = false
        offsetPx = 0f
    }

    companion object {
        const val MAX_BLUR_RADIUS = 15f
    }
}
