package com.facet.music.core.search

import com.facet.music.data.model.Track
import java.util.Locale

/**
 * Слияние выдачи нескольких источников для сводного поиска.
 *
 * Треки считаются одним и тем же, если совпали нормализованные артист и
 * название — регистр, пунктуация и скобочные пометки версии не важны.
 * В результат попадает «лучший» вариант: с полным потоком, а не 30-секундным
 * превью; [Track.sourceCount] хранит число источников, где трек нашёлся.
 */
object TrackMerger {

    /** Скобки, в которых обычно живут версии: «(Remix)», «[Live]», «(feat. X)». */
    private val BRACKETS = Regex("[(\\[][^)\\]]*[)\\]]")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /**
     * [groups] — выдача каждого источника в порядке приоритета.
     * Порядок источников сохраняется, совпадения поднимаются наверх.
     */
    fun merge(groups: List<List<Track>>): List<Track> {
        val buckets = LinkedHashMap<String, MutableList<Track>>()
        groups.forEach { tracks ->
            tracks.forEach { track ->
                if (track.id.isBlank() || track.title.isBlank()) return@forEach
                buckets.getOrPut(key(track)) { mutableListOf() }.add(track)
            }
        }
        return buckets.values
            .map { candidates ->
                val best = candidates.firstOrNull {
                    it.streamUrl != null && it.previewSeconds == null && !it.isSubscriptionOnly
                }
                    ?: candidates.firstOrNull {
                        it.previewSeconds == null && !it.isSubscriptionOnly
                    }
                    ?: candidates.firstOrNull { it.previewSeconds == null }
                    ?: candidates.first()
                best.copy(sourceCount = candidates.map { it.source }.distinct().size)
            }
            .sortedByDescending { it.sourceCount }
    }

    /** Ключ дедупа: артист + название без регистра и скобочных пометок. */
    fun key(track: Track): String =
        normalize(track.artist) + "|" + normalize(BRACKETS.replace(track.title, " "))

    /**
     * Слияние без перестановки уже показанных строк: сводный поиск получает
     * источники по одному, и новые треки должны добавляться в конец, а не
     * поднимать всю выдачу и сбрасывать позицию скролла.
     */
    fun mergeKeepingOrder(existing: List<Track>, groups: List<List<Track>>): List<Track> {
        val merged = merge(groups)
        if (existing.isEmpty()) return merged

        val byKey = merged.associateBy { key(it) }
        val kept = existing.mapNotNull { byKey[key(it)] }
        val keptKeys = kept.mapTo(HashSet()) { key(it) }
        return kept + merged.filter { key(it) !in keptKeys }
    }

    private fun normalize(value: String): String =
        NON_WORD.replace(value.lowercase(Locale.ROOT), " ").trim()
}
