package com.facet.music.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.facet.music.R

internal fun waveRemoteViews(context: Context, bitmap: Bitmap?): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_wave).apply {
        if (bitmap != null) setImageViewBitmap(R.id.widget_wave, bitmap)
    }

internal fun elapsedRemoteViews(
    context: Context,
    positionMs: Long,
    isPlaying: Boolean,
    palette: WidgetPalette,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_elapsed).apply {
        val base = SystemClock.elapsedRealtime() - positionMs.coerceAtLeast(0L)
        if (isPlaying) {
            setChronometer(R.id.widget_elapsed, base, null, true)
        } else {
            setChronometer(R.id.widget_elapsed, base, null, false)
            setTextViewText(
                R.id.widget_elapsed,
                DateUtils.formatElapsedTime(positionMs.coerceAtLeast(0L) / 1000L),
            )
        }
        setTextViewTextSize(
            R.id.widget_elapsed,
            TypedValue.COMPLEX_UNIT_SP,
            9f,
        )
        setTextColor(R.id.widget_elapsed, palette.muted.toArgb())
    }
