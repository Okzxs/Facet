package com.facet.music.core.lyrics

import com.facet.music.data.model.LyricLine

object LyricsAutoTimer {

    fun distribute(lines: List<LyricLine>, durationMs: Long): List<LyricLine> {
        if (lines.isEmpty() || durationMs <= 0L) return lines
        val usable = lines.filter { !it.isGap }
        if (usable.isEmpty()) return lines
        val start = durationMs * START_FRACTION
        val end = durationMs * END_FRACTION
        val span = (end - start).coerceAtLeast(1.0)
        val weights = usable.map { weight(it.text) }
        val total = weights.sum().coerceAtLeast(1.0)
        var cursor = start
        val times = HashMap<Int, Long>(usable.size)
        var usableIndex = 0
        lines.forEachIndexed { index, line ->
            if (line.isGap) return@forEachIndexed
            val share = weights[usableIndex] / total
            times[index] = cursor.toLong()
            cursor += share * span
            usableIndex++
        }
        return lines.mapIndexed { index, line ->
            val time = times[index] ?: line.timeMs
            line.copy(timeMs = time.coerceAtLeast(0L), sungUntilMs = null, words = emptyList())
        }
    }

    private fun weight(text: String): Double =
        text.count { !it.isWhitespace() }.coerceAtLeast(1).toDouble()

    private const val START_FRACTION = 0.05
    private const val END_FRACTION = 0.97
}
