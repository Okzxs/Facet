package com.facet.music.feature.player

import androidx.compose.ui.geometry.Rect

class BoundsHolder {
    var value: Rect? = null
}

/** Порог закрытия свайпом вниз: 30% высоты экрана. */
internal const val DISMISS_THRESHOLD = 0.30f

/** Скорость вниз, при которой плеер закрывается независимо от дистанции. */
internal const val CLOSE_VELOCITY_PX_S = 1200f
