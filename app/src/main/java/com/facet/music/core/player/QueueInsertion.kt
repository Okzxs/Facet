package com.facet.music.core.player

import com.facet.music.data.model.Track

data class QueueInsertion(
    val queue: List<Track>,
    val currentIndex: Int,
)

fun insertNextTrack(queue: List<Track>, currentIndex: Int, track: Track): QueueInsertion {
    if (track.id.isBlank()) return QueueInsertion(queue, currentIndex)
    if (currentIndex !in queue.indices) return QueueInsertion(queue, currentIndex)

    val currentTrack = queue[currentIndex]
    if (currentTrack.id == track.id) return QueueInsertion(queue, currentIndex)

    var removedBeforeCurrent = 0
    val cleaned = ArrayList<Track>(queue.size)
    queue.forEachIndexed { index, item ->
        if (index != currentIndex && item.id == track.id) {
            if (index < currentIndex) removedBeforeCurrent++
        } else {
            cleaned.add(item)
        }
    }

    val anchorIndex = currentIndex - removedBeforeCurrent
    cleaned.add(anchorIndex + 1, track)
    return QueueInsertion(cleaned, anchorIndex)
}
