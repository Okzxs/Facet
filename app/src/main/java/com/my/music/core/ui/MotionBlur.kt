package com.my.music.core.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Motion blur для переходов (только Android 12+).
 *
 * Размытие живёт в отдельном graphicsLayer и пересоздаётся только когда
 * радиус реально меняется. При нулевом радиусе renderEffect сбрасывается
 * в null, чтобы в состоянии покоя GPU не тратился на эффект.
 */
fun Modifier.motionBlur(blurRadius: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val radius = blurRadius.coerceAtLeast(0f)
    return graphicsLayer {
        renderEffect = if (radius > 0.1f) {
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
