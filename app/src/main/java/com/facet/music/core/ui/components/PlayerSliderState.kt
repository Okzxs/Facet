package com.facet.music.core.ui.components

/** Общая модель preview для дорожки и таймкода. Stop сохраняет значение до finish. */
data class PlayerSliderState(
    val preview: Float? = null,
    val dragging: Boolean = false,
) {
    fun start() = copy(dragging = true)
    fun change(value: Float) = copy(preview = value.coerceIn(0f, 1f))
    fun stop() = copy(dragging = false)
    fun cancel() = PlayerSliderState()
    fun display(playback: Float) = preview ?: playback
}

/** Координаты относительно самой дорожки, без дополнительных отступов thumb. */
internal fun trackX(fraction: Float, width: Float, rtl: Boolean): Float =
    width * if (rtl) 1f - fraction.coerceIn(0f, 1f) else fraction.coerceIn(0f, 1f)
