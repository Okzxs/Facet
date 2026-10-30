package com.facet.music.data.source.lyrics

import com.facet.music.data.model.LyricLine
import com.facet.music.data.model.LyricsResult
import java.util.regex.Pattern

/**
 * Парсер LRC-формата (совместим со спекой и референсом exteraless).
 * Поддерживает [mm:ss.xx], [mm:ss.xxx], [offset:+-xxx] и караоке-теги.
 */
object LrcParser {

    private val TIME_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val WORD_TIME_PATTERN = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")
    private val OFFSET_PATTERN = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE)
    private val META_PATTERN = Pattern.compile("^\\[[a-zA-Z#]+:.*]")

    fun parse(lrcText: String?, plainText: String? = null): LyricsResult? {
        if (!lrcText.isNullOrBlank()) {
            val parsedSynced = parseSynced(lrcText)
            if (parsedSynced != null && parsedSynced.lines.isNotEmpty()) {
                return parsedSynced
            }
        }

        if (!plainText.isNullOrBlank()) {
            return parsePlain(plainText)
        }

        return null
    }

    private fun parseSynced(text: String): LyricsResult? {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val rows = normalized.split("\n")
        var offset = 0L
        val timed = mutableListOf<LyricLine>()

        for (row in rows) {
            val line = row.trim()
            val offMatcher = OFFSET_PATTERN.matcher(line)
            if (offMatcher.find()) {
                offMatcher.group(1)?.toLongOrNull()?.let { offset = it }
                continue
            }

            val m = TIME_PATTERN.matcher(line)
            val times = mutableListOf<Long>()
            var end = 0
            while (m.find() && m.start() == end) {
                val min = m.group(1) ?: "0"
                val sec = m.group(2) ?: "0"
                val frac = m.group(3)
                times.add(toMillis(min, sec, frac))
                end = m.end()
            }

            if (times.isEmpty()) continue

            val content = WORD_TIME_PATTERN.matcher(line.substring(end)).replaceAll("").trim()
            for (t in times) {
                val finalTime = (t - offset).coerceAtLeast(0L)
                timed.add(LyricLine(timeMs = finalTime, text = content))
            }
        }

        if (timed.isNotEmpty()) {
            timed.sortBy { it.timeMs }
            val cleaned = mutableListOf<LyricLine>()
            for (i in timed.indices) {
                val current = timed[i]
                if (current.text.isEmpty() && (cleaned.isEmpty() || cleaned.last().text.isEmpty() || i == timed.lastIndex)) {
                    continue
                }
                cleaned.add(current)
            }
            if (cleaned.isNotEmpty()) {
                return LyricsResult(lines = cleaned, isSynced = true)
            }
        }

        return null
    }

    private fun parsePlain(text: String): LyricsResult? {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val rows = normalized.split("\n")
        val plain = mutableListOf<LyricLine>()

        for (row in rows) {
            val line = row.trim()
            if (META_PATTERN.matcher(line).matches()) continue
            if (line.isEmpty() && (plain.isEmpty() || plain.last().text.isEmpty())) continue
            plain.add(LyricLine(timeMs = -1L, text = line))
        }

        while (plain.isNotEmpty() && plain.last().text.isEmpty()) {
            plain.removeAt(plain.lastIndex)
        }

        return if (plain.isNotEmpty()) LyricsResult(lines = plain, isSynced = false) else null
    }

    private fun toMillis(min: String, sec: String, frac: String?): Long {
        var ms = min.toLong() * 60_000L + sec.toLong() * 1_000L
        if (frac != null) {
            val f = frac.toIntOrNull() ?: 0
            ms += when (frac.length) {
                1 -> f * 100L
                2 -> f * 10L
                else -> f.toLong()
            }
        }
        return ms
    }
}
