package com.facet.music.data.source.lyrics

import com.facet.music.data.model.LyricAlignment
import com.facet.music.data.model.LyricLine
import com.facet.music.data.model.LyricSyllable
import com.facet.music.data.model.LyricWord

internal class TimedRun(val startMs: Long, val endMs: Long, val raw: String)

internal fun wordsFromRuns(runs: List<TimedRun>, spacingIsExplicit: Boolean = false): List<LyricWord> {
    val glue = spacingIsExplicit || runs.any { run -> run.raw.any(Char::isWhitespace) }
    val words = ArrayList<LyricWord>()
    val current = StringBuilder()
    val parts = ArrayList<LyricSyllable>()
    var start = 0L
    var end = 0L

    fun flush() {
        if (current.isNotEmpty()) {
            val text = current.toString()
            words += LyricWord(start, maxOf(start, end), text, LyricSyllable.normalized(parts, text.length))
        }
        current.setLength(0)
        parts.clear()
    }

    for (run in runs) {
        val content = run.raw.trim()
        if (content.isEmpty()) {
            flush()
            continue
        }
        val joins = glue && current.isNotEmpty() && !run.raw.first().isWhitespace() &&
            !current.last().isUnspacedScript() && !content.first().isUnspacedScript()
        if (!joins) flush()
        if (current.isEmpty()) {
            start = run.startMs
            end = run.startMs
        }
        val from = current.length
        current.append(content)
        val runEnd = maxOf(run.startMs, run.endMs)
        parts += LyricSyllable(run.startMs, runEnd, from, current.length)
        end = maxOf(end, runEnd)
        if (!glue || run.raw.last().isWhitespace()) flush()
    }
    flush()
    return words
}

private fun Char.isUnspacedScript(): Boolean =
    this in '\u3400'..'\u9FFF' ||
        this in '\u3040'..'\u30FF' ||
        this in '\u3000'..'\u303F' ||
        this in '\uFF00'..'\uFFEF' ||
        this in '\u0E00'..'\u0E7F'

internal fun List<LyricLine>.withInstrumentalGaps(): List<LyricLine> {
    if (isEmpty()) return this
    val out = ArrayList<LyricLine>(size + 4)
    if (first().timeMs >= 4_000L) out += LyricLine(0L, "")
    forEachIndexed { index, line ->
        out += line
        val next = getOrNull(index + 1) ?: return@forEachIndexed
        if (!line.hasKnownEnd) return@forEachIndexed
        val silence = next.timeMs - line.endMs
        if (silence >= 4_000L && line.endMs > line.timeMs) out += LyricLine(line.endMs, "")
    }
    return out
}

internal fun lineAlignments(singers: List<String?>, types: Map<String, String>): List<LyricAlignment> {
    var left = true
    var lastVoice: String? = null
    var rightward = 0
    var placed = 0
    val sides = singers.map { singer ->
        if (singer.isNullOrEmpty()) return@map LyricAlignment.Start
        val type = types[singer] ?: when (singer) {
            "v1000" -> "group"
            "v2000" -> "other"
            else -> "person"
        }
        placed += 1
        if (type == "group") return@map LyricAlignment.Start
        when {
            lastVoice == null -> left = type != "other"
            singer != lastVoice -> left = !left
        }
        lastVoice = singer
        if (!left) rightward += 1
        if (left) LyricAlignment.Start else LyricAlignment.End
    }
    if (placed == 0 || rightward.toFloat() / placed < 0.85f) return sides
    return sides.map {
        if (it == LyricAlignment.Start) LyricAlignment.End else LyricAlignment.Start
    }
}
