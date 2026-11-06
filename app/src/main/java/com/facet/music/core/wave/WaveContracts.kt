package com.facet.music.core.wave

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Один источник кандидатов волны. Реализации живут в data-слое и сами решают,
 * какие сетевые вызовы делать; ядро лишь запускает их параллельно с таймаутом.
 */
interface CandidateSource {
    suspend fun candidates(plan: WaveSessionPlan, randomSeed: Long): List<Candidate>
}

/**
 * Общий сетевой шлюз волны: не больше четырёх одновременных запросов,
 * даже если все источники стартовали разом.
 */
class WaveNetworkGate(maxConcurrent: Int = 4) {

    private val semaphore = Semaphore(maxConcurrent.coerceAtLeast(1))

    suspend fun <T> withPermit(block: suspend () -> T): T =
        semaphore.withPermit { block() }
}

/** Профиль вкуса с эффективными (затухающими) весами. */
interface TasteProfileProvider {
    suspend fun profile(): WaveTasteProfile
}

/** Всё, что ядру нужно знать о прошлом пользователя и его очереди. */
interface WaveHistoryStore {

    suspend fun loadHistory(nowMs: Long): WaveHistory

    /** Лайки и дослушанные треки за окно; вес считает ядро. */
    suspend fun seedTracks(sinceMs: Long): List<PlayedTrack>

    /** Ключи треков-семян последних сессий. */
    suspend fun previousSessionSeedKeys(limit: Int): Set<String>

    /** Ключи артистов-семян последних сессий. */
    suspend fun previousSessionSeedArtistKeys(limit: Int): Set<String>

    suspend fun recordShown(sessionId: String, candidates: List<Candidate>)

    /**
     * Обновить исход показа. Если строки показа для трека ещё нет (например,
     * трек играл из обычной очереди), событие просто игнорируется.
     */
    suspend fun recordOutcome(
        sessionId: String?,
        track: com.facet.music.data.model.Track,
        outcome: WaveOutcome,
        playedMs: Long,
    )

    suspend fun saveSession(record: WaveSessionRecord)

    suspend fun cleanupIfNeeded(nowMs: Long)

    suspend fun blockArtist(artistKey: String, nowMs: Long)
}
