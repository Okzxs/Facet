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

    const val CUSTOM_PRESET: String = "CUSTOM"

    val BAND_CENTERS_HZ: List<Int> = listOf(60, 150, 400, 1_000, 2_400, 6_000, 12_000)

    val DEFAULT_CUSTOM_BANDS: List<Float> = List(BAND_CENTERS_HZ.size) { 0f }

    fun bandsForPreset(preset: EqualizerPreset): List<Float> =
        BAND_CENTERS_HZ.map { gainDb(preset, it) }

    fun customGainDb(bands: List<Float>, centerHz: Int): Float {
        if (bands.isEmpty()) return 0f
        val target = kotlin.math.ln(centerHz.toFloat())
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        BAND_CENTERS_HZ.forEachIndexed { index, hz ->
            val distance = kotlin.math.abs(kotlin.math.ln(hz.toFloat()) - target)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bands.getOrNull(bestIndex) ?: 0f
    }

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
