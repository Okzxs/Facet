package com.my.music.data.source.wave

import com.my.music.core.util.rethrowCancellation
import com.my.music.data.model.Track
import com.my.music.data.repository.LikedTracksRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudChartsRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.WaveRepository
import com.my.music.data.repository.WaveSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сборка персональной волны из локальных данных и каталога SoundCloud.
 */
@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val chartsRepository: SoundCloudChartsRepository,
) : WaveRepository {

    override suspend fun generateWave(): Result<WaveSelection> = withContext(Dispatchers.IO) {
        runCatching {
            val liked = likedTracksRepository.getLikedTracks().first()
            val recent = playHistoryRepository.getPlayHistory().first().take(HISTORY_LIMIT)

            val likedPicks = liked.shuffled().take(LIKED_LIMIT)

            // Все лайки и недавние треки исключаем из жанровых находок
            val knownIds = (liked + recent).mapTo(mutableSetOf()) { it.id }
            val genrePicks = discoverByGenres(liked, knownIds)

            val entries = buildList {
                likedPicks.forEach { add(it to WaveOrigin.LIKES) }
                recent.forEach { add(it to WaveOrigin.HISTORY) }
                genrePicks.forEach { add(it to WaveOrigin.GENRES) }
            }

            val unique = entries.distinctBy { it.first.id }.shuffled()

            WaveSelection(
                tracks = unique.map { it.first },
                fromLikes = unique.count { it.second == WaveOrigin.LIKES },
                fromHistory = unique.count { it.second == WaveOrigin.HISTORY },
                fromGenres = unique.count { it.second == WaveOrigin.GENRES },
            )
        }.rethrowCancellation()
    }

    /**
     * Находки по жанрам лайков. Если у лайков нет жанров —
     * добираем случайные треки из топа SoundCloud.
     */
    private suspend fun discoverByGenres(
        liked: List<Track>,
        knownIds: MutableSet<String>,
    ): List<Track> {
        val genres = liked
            .mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .shuffled()
            .take(MAX_GENRE_QUERIES)

        val picks = mutableListOf<Track>()

        if (genres.isEmpty()) {
            return chartFallback(knownIds)
        }

        for (genre in genres) {
            if (picks.size >= GENRE_LIMIT) break
            val results = soundCloudRepository
                .search(query = genre, limit = GENRE_LIMIT * 2)
                .getOrNull()
                .orEmpty()
            results.shuffled().forEach { candidate ->
                if (picks.size < GENRE_LIMIT && knownIds.add(candidate.id)) {
                    picks += candidate
                }
            }
        }

        // Поиск не дал результатов (офлайн/пустая выдача) — добираем из топа
        return if (picks.isEmpty()) chartFallback(knownIds) else picks
    }

    private suspend fun chartFallback(knownIds: MutableSet<String>): List<Track> {
        val picks = mutableListOf<Track>()
        chartsRepository.getTop50().getOrNull().orEmpty().shuffled().forEach { candidate ->
            if (picks.size < GENRE_LIMIT && knownIds.add(candidate.id)) {
                picks += candidate
            }
        }
        return picks
    }

    private enum class WaveOrigin { LIKES, HISTORY, GENRES }

    private companion object {
        const val LIKED_LIMIT = 10
        const val HISTORY_LIMIT = 5
        const val GENRE_LIMIT = 5
        const val MAX_GENRE_QUERIES = 5
    }
}
