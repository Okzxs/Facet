package com.my.music.core.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion blur для переходов (только Android 12+).
 *
 * Размытие живёт в отдельном graphicsLayer и пересоздаётся только когда
 * радиус реально меняется. При нулевом радиусе renderEffect сбрасывается
 * в null, чтобы в состоянии покоя GPU не тратился на эффект.
 *
 * [CompositingStrategy.Offscreen] обязателен: на части устройств RenderEffect
 * без собственного offscreen-слоя просто игнорируется.
 */
fun Modifier.motionBlur(blurRadius: Float): Modifier = motionBlur { blurRadius }

/**
 * Вариант с лямбдой: радиус читается внутри layer-блока при отрисовке,
 * поэтому анимация не вызывает рекомпозицию — только инвалидацию слоя.
 */
fun Modifier.motionBlur(blurRadius: () -> Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return graphicsLayer {
        val radius = blurRadius().coerceAtLeast(0f)
        val active = radius > 0.1f
        compositingStrategy = if (active) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
        renderEffect = if (active) {
            RenderEffect.createBlurEffect(
                radius,
                radius,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        } else {
            null
        }
    }
}
