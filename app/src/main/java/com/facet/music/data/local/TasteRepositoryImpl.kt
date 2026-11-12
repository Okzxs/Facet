package com.facet.music.data.local

import com.facet.music.core.wave.Clock
import com.facet.music.data.local.dao.TasteDao
import com.facet.music.data.local.dao.decayedWeight
import com.facet.music.data.model.Track
import com.facet.music.data.repository.TasteContext
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.TasteWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TasteRepositoryImpl @Inject constructor(
    private val tasteDao: TasteDao,
    private val clock: Clock,
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
        val delta = if (context == TasteContext.WAVE) WAVE_SKIP_DELTA else NORMAL_SKIP_DELTA
        changeWeight(track, delta)
    }

    override suspend fun onLike(track: Track) {
        changeWeight(track, LIKE_DELTA)
    }

    override suspend fun onUnlike(track: Track) {
        changeWeight(track, -LIKE_DELTA)
    }

    override suspend fun onDislike(track: Track) {
        changeWeight(track, DISLIKE_DELTA)
    }

    override suspend fun getTopGenres(limit: Int): List<TasteWeight> =
        withContext(Dispatchers.IO) {
            val now = clock.nowMs()
            tasteDao.getGenresRaw(RAW_TOP_LIMIT)
                .map { it.genre to decayedWeight(it.weight, it.updatedAt, now) }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { TasteWeight(it.first, it.second) }
        }

    override suspend fun getTopArtists(limit: Int): List<TasteWeight> =
        withContext(Dispatchers.IO) {
            val now = clock.nowMs()
            tasteDao.getArtistsRaw(RAW_TOP_LIMIT)
                .map { it.artist to decayedWeight(it.weight, it.updatedAt, now) }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { TasteWeight(it.first, it.second) }
        }

    private suspend fun changeWeight(track: Track, delta: Double): Unit =
        withContext(Dispatchers.IO) {
            val now = clock.nowMs()
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
        const val WAVE_SKIP_DELTA = -1.0
        const val NORMAL_SKIP_DELTA = -0.3
        const val LIKE_DELTA = 3.0
        const val DISLIKE_DELTA = -4.0
        const val RAW_TOP_LIMIT = 200
    }
}
