package com.facet.music.feature.player

import androidx.compose.ui.geometry.Rect

class BoundsHolder {
    var value: Rect? = null
}

internal const val DISMISS_THRESHOLD = 0.30f

internal const val CLOSE_VELOCITY_PX_S = 1200f
