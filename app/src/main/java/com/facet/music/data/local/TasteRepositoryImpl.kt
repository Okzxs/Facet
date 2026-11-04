package com.facet.music.data.local

import com.facet.music.data.local.dao.TasteDao
import com.facet.music.data.model.Track
import com.facet.music.data.repository.TasteContext
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.TasteWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Накопитель вкусов в Room. Все изменения пишутся в IO-диспетчере.
 */
@Singleton
class TasteRepositoryImpl @Inject constructor(
    private val tasteDao: TasteDao,
) : TasteRepository {

    override suspend fun onTrackFinished(
        track: Track,
        playedFraction: Float,
        context: TasteContext,
    ) {
        if (playedFraction <= FINISHED_THRESHOLD) return
        changeWeight(track, TRACK_FINISHED_DELTA)
    }

    override suspend fun onSkip(
        track: Track,
        playedMs: Long,
        context: TasteContext,
    ) {
        if (playedMs >= SKIP_THRESHOLD_MS) return
        changeWeight(track, SKIP_DELTA)
    }

    override suspend fun onLike(track: Track) {
        changeWeight(track, LIKE_DELTA)
    }

    override suspend fun onUnlike(track: Track) {
        changeWeight(track, -LIKE_DELTA)
    }

    override suspend fun onDislike(track: Track) {
        changeWeight(track, -LIKE_DELTA)
    }

    override suspend fun getTopGenres(limit: Int): List<TasteWeight> = withContext(Dispatchers.IO) {
        tasteDao.getTopGenres(limit).map { TasteWeight(it.genre, it.weight) }
    }

    override suspend fun getTopArtists(limit: Int): List<TasteWeight> = withContext(Dispatchers.IO) {
        tasteDao.getTopArtists(limit).map { TasteWeight(it.artist, it.weight) }
    }

    private suspend fun changeWeight(track: Track, delta: Double): Unit =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            normalize(track.genre)?.let { tasteDao.addGenreWeight(it, delta, now) }
            normalize(track.artist)?.let { tasteDao.addArtistWeight(it, delta, now) }
        }

    private fun normalize(value: String?): String? = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }

    private companion object {
        const val FINISHED_THRESHOLD = 0.8f
        const val SKIP_THRESHOLD_MS = 30_000L
        const val TRACK_FINISHED_DELTA = 1.0
        const val SKIP_DELTA = -1.0
        const val LIKE_DELTA = 3.0
    }
}
