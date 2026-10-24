package com.my.music.data.repository

/**
 * Память о треках, которые уже показывались в рекомендациях.
 */
interface RecentlyShownRepository {

    /**
     * Возвращает ID треков, показанных не раньше `now - ttlMs`.
     */
    suspend fun getRecentlyShownIds(now: Long, ttlMs: Long): Set<String>

    /**
     * Запоминает новую пачку и удаляет записи старше TTL.
     */
    suspend fun markShown(trackIds: Collection<String>, now: Long, ttlMs: Long)
}
