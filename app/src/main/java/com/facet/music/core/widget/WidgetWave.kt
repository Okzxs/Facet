package com.facet.music.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetWave {

    private val lock = Any()
    private var cacheKey: String? = null
    private var cached: Bitmap? = null

    fun render(
        context: Context,
        width: Dp,
        height: Dp,
        progress: Float,
        accent: Color,
        track: Color,
    ): Bitmap? {
        val density = context.resources.displayMetrics.density
        val widthPx = (width.value * density).roundToInt().coerceAtLeast(8)
        val heightPx = (height.value * density).roundToInt().coerceAtLeast(8)
        val clamped = progress.coerceIn(0f, 1f)
        val key = "$widthPx|$heightPx|${(clamped * 500f).roundToInt()}|" +
            "${accent.toArgb()}|${track.toArgb()}"
        synchronized(lock) {
            if (key == cacheKey) cached?.let { return it }
        }
        val bitmap = runCatching {
            draw(widthPx, heightPx, clamped, accent.toArgb(), track.toArgb())
        }.getOrNull()
        synchronized(lock) {
            cacheKey = key
            cached = bitmap
        }
        return bitmap
    }

    private fun draw(
        width: Int,
        height: Int,
        progress: Float,
        accent: Int,
        track: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerY = height / 2f
        val inset = height * 0.14f
        val left = inset
        val right = width - inset
        val span = (right - left).coerceAtLeast(1f)
        val stroke = max(2f, height * 0.14f)
        val amplitude = height * 0.2f
        val wavelength = max(height * 1.7f, 12f)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        linePaint.color = track
        canvas.drawLine(left, centerY, right, centerY, linePaint)

        if (progress <= 0.001f) return bitmap

        val played = left + span * progress
        linePaint.color = accent
        val path = Path()
        var x = left
        var started = false
        while (x <= played) {
            val envelope = ((played - x) / wavelength).coerceIn(0f, 1f)
            val phase = (x - left) / wavelength * (2f * PI.toFloat())
            val y = centerY + sin(phase) * amplitude * envelope
            if (started) path.lineTo(x, y) else path.moveTo(x, y)
            started = true
            x += 1f
        }
        if (started) canvas.drawPath(path, linePaint)

        val dotRadius = max(stroke * 1.1f, height * 0.16f)
        val dotX = played.coerceIn(left + dotRadius, right - dotRadius)
        canvas.drawCircle(dotX, centerY, dotRadius, linePaint)
        return bitmap
    }
}
