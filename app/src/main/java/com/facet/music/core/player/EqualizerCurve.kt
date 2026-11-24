package com.facet.music.core.player

enum class EqualizerPreset {
    FLAT,
    BASS,
    VOCAL,
    ROCK,
    ELECTRONIC,
    ;

    companion object {
        fun fromName(raw: String?): EqualizerPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FLAT
    }
}

object EqualizerCurve {

    fun gainDb(preset: EqualizerPreset, centerHz: Int): Float {
        val (bass, lowMid, highMid, treble) = curve(preset)
        return when {
            centerHz < 250 -> bass
            centerHz < 1_000 -> lowMid
            centerHz < 4_000 -> highMid
            else -> treble
        }
    }

    fun curve(preset: EqualizerPreset): List<Float> = when (preset) {
        EqualizerPreset.FLAT -> listOf(0f, 0f, 0f, 0f)
        EqualizerPreset.BASS -> listOf(6f, 2f, 0f, 1f)
        EqualizerPreset.VOCAL -> listOf(-2f, 0f, 3f, 1f)
        EqualizerPreset.ROCK -> listOf(4f, -1f, 2f, 3f)
        EqualizerPreset.ELECTRONIC -> listOf(5f, 1f, 2f, 4f)
    }

    fun levelMillibels(gainDb: Float, minMb: Int, maxMb: Int): Short =
        (gainDb * 100f).toInt().coerceIn(minMb, maxMb).toShort()
}
