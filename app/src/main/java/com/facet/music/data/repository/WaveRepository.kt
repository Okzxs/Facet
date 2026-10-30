package com.facet.music.data.repository

import com.facet.music.data.model.Track
import kotlinx.coroutines.flow.StateFlow

/**
 * Результат генерации «Моей волны»: треки и вклад каждого источника.
 */
data class WaveSelection(
    val tracks: List<Track>,
    val fromLikes: Int,
    val fromHistory: Int,
    val fromGenres: Int,
    val fromSimilar: Int = 0,
)

/**
 * Состояние бесконечной сессии волны.
 *
 * [shownTrackIds] защищает от повторов, а накопленные счётчики
 * позволяют UI показывать состав всей волны, а не только последней пачки.
 */
data class WaveSession(
    val sessionId: String,
    val seedTrackIds: List<String> = emptyList(),
    val seedArtists: List<String> = emptyList(),
    val seedGenres: List<String> = emptyList(),
    val shownTrackIds: Set<String> = emptySet(),
    val batchNumber: Int = 0,
    val isRussianPriority: Boolean = true,
    val isTerminated: Boolean = false,
    /** Радио от трека: очередь строится только от похожих артистов, без вкусов. */
    val radioMode: Boolean = false,
    val fromLikes: Int = 0,
    val fromHistory: Int = 0,
    val fromGenres: Int = 0,
    val fromSimilar: Int = 0,
)

/**
 * Очередная порция волны.
 */
data class WaveBatch(
    val tracks: List<Track>,
    val session: WaveSession,
)

/**
 * Персональный бесконечный поток с сессией и обратной связью.
 */
interface WaveRepository {

    /** Текущая активная сессия; null — волна не запущена. */
    val activeSession: StateFlow<WaveSession?>

    /**
     * Создать новую сессию.
     * [seeds] — уже показанные треки, которые нужно исключить из выдачи.
     */
    suspend fun startSession(seeds: List<String> = emptyList()): Result<WaveSession>

    /**
     * Запустить радио от конкретного трека: похожие артисты Last.fm и жанр
     * трека, без вклада лайков и истории. Дальше работает обычный refill.
     */
    suspend fun startRadio(seed: Track): Result<WaveSession>

    /**
     * Собрать следующую пачку треков с учётом накопленных вкусов
     * и без повторов внутри сессии.
     */
    suspend fun nextBatch(size: Int = DEFAULT_BATCH_SIZE): Result<WaveBatch>

    /** Завершить сессию волны. */
    suspend fun reset()

    /**
     * Совместимый метод: новая сессия и первая пачка одним вызовом.
     */
    suspend fun generateWave(): Result<WaveSelection>

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}
