package com.facet.music.data.repository

import com.facet.music.data.model.Track

data class QueueSnapshot(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long = 0L,
)

interface QueueRepository {

    suspend fun load(): QueueSnapshot?

    suspend fun save(tracks: List<Track>, currentIndex: Int, positionMs: Long = 0L)

    suspend fun clear()
}
