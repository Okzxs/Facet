package com.facet.music.core.home

import com.facet.music.data.model.Track

object SmartCollections {

    fun forgotten(
        liked: List<Track>,
        recentIds: Set<String>,
        excludeIds: Set<String> = emptySet(),
        limit: Int = 10,
    ): List<Track> = liked
        .filterNot { it.id in recentIds || it.id in excludeIds }
        .distinctBy { it.artist.lowercase() }
        .take(limit)

    fun freshLikes(liked: List<Track>, limit: Int = 10): List<Track> = liked
        .distinctBy { it.artist.lowercase() }
        .take(limit)
}
