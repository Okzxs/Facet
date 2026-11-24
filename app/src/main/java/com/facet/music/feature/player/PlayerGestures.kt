package com.facet.music.feature.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.facet.music.data.repository.AppSettings

class BoundsHolder {
    var value: Rect? = null
}

internal data class DismissProfile(
    val fraction: Float,
    val capDp: Int,
    val velocityPxS: Float,
)

internal const val DISMISS_THRESHOLD = 0.14f

internal const val CLOSE_VELOCITY_PX_S = 800f

internal fun dismissProfile(sensitivity: String): DismissProfile = when (sensitivity) {
    AppSettings.SENSITIVITY_HIGH -> DismissProfile(0.10f, 72, 600f)
    AppSettings.SENSITIVITY_LOW -> DismissProfile(0.20f, 140, 1100f)
    else -> DismissProfile(DISMISS_THRESHOLD, 96, CLOSE_VELOCITY_PX_S)
}

internal fun dismissThresholdPx(
    heightPx: Float,
    density: Density,
    sensitivity: String = AppSettings.SENSITIVITY_MEDIUM,
): Float {
    val profile = dismissProfile(sensitivity)
    return minOf(heightPx * profile.fraction, with(density) { profile.capDp.dp.toPx() })
}

internal fun closeVelocityPxS(sensitivity: String): Float =
    dismissProfile(sensitivity).velocityPxS
