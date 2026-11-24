package com.facet.music.feature.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

class BoundsHolder {
    var value: Rect? = null
}

internal const val DISMISS_THRESHOLD = 0.14f

internal const val CLOSE_VELOCITY_PX_S = 800f

internal fun dismissThresholdPx(heightPx: Float, density: Density): Float =
    minOf(heightPx * DISMISS_THRESHOLD, with(density) { 96.dp.toPx() })
