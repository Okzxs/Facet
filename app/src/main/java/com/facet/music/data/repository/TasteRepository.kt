package com.facet.music.data.repository

import com.facet.music.data.model.Track

/**
 * Вес термина (жанра или артиста), накопленный по обратной связи.
 */
data class TasteWeight(
    val value: String,
    val weight: Double,
)

/**
 * Контекст прослушивания для вкусового профиля.
 */
enum class TasteContext {
    NORMAL,
    WAVE,
}

/**
 * Локальный профиль вкусов «Моей волны».
 *
 * События:
 * - трек дослушан (>80%) — +1 к жанру/артисту;
 * - скип (<30 секунд) — -1;
 * - лайк — +3, дизлайк — -3.
 */
interface TasteRepository {

    suspend fun onTrackFinished(
        track: Track,
        playedFraction: Float,
        context: TasteContext = TasteContext.NORMAL,
    )

    suspend fun onSkip(
        track: Track,
        playedMs: Long,
        context: TasteContext = TasteContext.NORMAL,
    )

    suspend fun onLike(track: Track)

    suspend fun onUnlike(track: Track)

    suspend fun onDislike(track: Track)

    suspend fun getTopGenres(limit: Int = 5): List<TasteWeight>

    suspend fun getTopArtists(limit: Int = 5): List<TasteWeight>
}
