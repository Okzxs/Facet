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

    private const val PHASE_PERIOD_MS = 1600.0
    private const val RESOLUTION_SCALE = 0.5f

    private val lock = Any()
    private var cacheKey: String? = null
    private var cached: Bitmap? = null

    fun phase(positionMs: Long): Float {
        val position = positionMs.coerceAtLeast(0L)
        val fraction = (position % PHASE_PERIOD_MS.toLong()).toDouble() / PHASE_PERIOD_MS
        return (fraction * 2.0 * PI).toFloat()
    }

    fun render(
        context: Context,
        width: Dp,
        height: Dp,
        progress: Float,
        phase: Float,
        accent: Color,
        track: Color,
    ): Bitmap? {
        val density = context.resources.displayMetrics.density * RESOLUTION_SCALE
        val widthPx = (width.value * density).roundToInt().coerceAtLeast(16)
        val heightPx = (height.value * density).roundToInt().coerceAtLeast(10)
        val clamped = progress.coerceIn(0f, 1f)
        val roundedPhase = (phase * 100f).roundToInt()
        val key = "$widthPx|$heightPx|${(clamped * 500f).roundToInt()}|$roundedPhase|" +
            "${accent.toArgb()}|${track.toArgb()}"
        synchronized(lock) {
            if (key == cacheKey) cached?.let { return it }
        }
        val bitmap = runCatching {
            draw(widthPx, heightPx, clamped, phase, accent.toArgb(), track.toArgb())
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
        phase: Float,
        accent: Int,
        track: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerY = height / 2f
        val inset = height * 0.16f
        val left = inset
        val right = width - inset
        val span = (right - left).coerceAtLeast(1f)
        val stroke = max(2f, height * 0.16f)
        val amplitude = height * 0.26f
        val wavelength = max(height * 3.6f, 10f)

        val path = Path()
        var x = left
        var started = false
        while (x <= right) {
            val u = ((x - left) / span).toDouble()
            val envelope = sin(PI * u).toFloat()
            val angle = (2.0 * PI * (x - left) / wavelength + phase).toFloat()
            val y = centerY + sin(angle).toFloat() * amplitude * envelope
            if (started) path.lineTo(x, y) else path.moveTo(x, y)
            started = true
            x += 1f
        }
        if (!started) return bitmap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = track
        canvas.drawPath(path, paint)

        if (progress <= 0.001f) return bitmap
        val playedX = left + span * progress
        paint.color = accent
        canvas.save()
        canvas.clipRect(0f, 0f, playedX, height.toFloat())
        canvas.drawPath(path, paint)
        canvas.restore()

        val u = progress.toDouble()
        val envelope = sin(PI * u).toFloat()
        val angle = (2.0 * PI * (playedX - left) / wavelength + phase).toFloat()
        val dotY = centerY + sin(angle).toFloat() * amplitude * envelope
        val dotRadius = max(stroke * 1.2f, height * 0.2f)
        canvas.drawCircle(playedX, dotY, dotRadius, paint.apply { style = Paint.Style.FILL })
        return bitmap
    }
}
