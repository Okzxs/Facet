package com.facet.music.data.repository

interface RecentlyShownRepository {

    suspend fun getRecentlyShownIds(now: Long, ttlMs: Long): Set<String>

    suspend fun markShown(trackIds: Collection<String>, now: Long, ttlMs: Long)
}
