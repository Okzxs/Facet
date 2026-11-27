package com.facet.music.core.player

data class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playBufferMs: Int,
    val rebufferMs: Int,
)

object PlaybackAudioConfig {

    const val FADE_OFF = 0
    const val FADE_SHORT = 1_500
    const val FADE_MEDIUM = 3_000
    const val FADE_LONG = 6_000

    val FADE_OPTIONS = listOf(FADE_OFF, FADE_SHORT, FADE_MEDIUM, FADE_LONG)

    fun sanitizeFade(ms: Int): Int =
        if (ms in FADE_OPTIONS) ms else FADE_OFF

    fun bufferProfile(name: String): BufferProfile = when (name.uppercase()) {
        "SMALL" -> BufferProfile(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            playBufferMs = 1_000,
            rebufferMs = 3_000,
        )
        "LARGE" -> BufferProfile(
            minBufferMs = 60_000,
            maxBufferMs = 120_000,
            playBufferMs = 1_500,
            rebufferMs = 8_000,
        )
        else -> BufferProfile(
            minBufferMs = 50_000,
            maxBufferMs = 50_000,
            playBufferMs = 1_000,
            rebufferMs = 5_000,
        )
    }

    fun fadeVolume(
        positionMs: Long,
        durationMs: Long,
        fadeMs: Int,
        hasNext: Boolean,
    ): Float {
        if (fadeMs <= 0 || durationMs <= 0L) return 1f
        val fade = fadeMs.toLong()
        val fadeIn = (positionMs.toFloat() / fade).coerceIn(0f, 1f)
        val fadeOut = if (hasNext) {
            ((durationMs - positionMs).toFloat() / fade).coerceIn(0f, 1f)
        } else {
            1f
        }
        return minOf(fadeIn, fadeOut)
    }
}
