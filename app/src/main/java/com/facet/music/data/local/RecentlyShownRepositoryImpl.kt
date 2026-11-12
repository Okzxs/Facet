package com.facet.music.data.local

import com.facet.music.data.local.dao.RecentlyShownDao
import com.facet.music.data.local.entity.RecentlyShownEntity
import com.facet.music.data.repository.RecentlyShownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyShownRepositoryImpl @Inject constructor(
    private val recentlyShownDao: RecentlyShownDao,
) : RecentlyShownRepository {

    override suspend fun getRecentlyShownIds(
        now: Long,
        ttlMs: Long,
    ): Set<String> = withContext(Dispatchers.IO) {
        recentlyShownDao.getTrackIdsSince(now - ttlMs).toSet()
    }

    override suspend fun markShown(
        trackIds: Collection<String>,
        now: Long,
        ttlMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (trackIds.isNotEmpty()) {
            recentlyShownDao.upsertAll(
                trackIds.distinct().map { trackId ->
                    RecentlyShownEntity(trackId = trackId, shownAt = now)
                },
            )
        }
        recentlyShownDao.deleteOlderThan(now - ttlMs)
    }
}
