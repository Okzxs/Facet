package com.my.music.data.repository

import com.my.music.data.model.Track

/**
 * Результат генерации «Моей волны»: треки и вклад каждого источника.
 */
data class WaveSelection(
    val tracks: List<Track>,
    val fromLikes: Int,
    val fromHistory: Int,
    val fromGenres: Int,
)

/**
 * Персональный поток на основе лайков, истории и жанровых находок.
 */
interface WaveRepository {

    /**
     * Собрать новую волну:
     * 1. до 10 случайных лайков;
     * 2. до 5 недавних треков из истории;
     * 3. до 5 находок из жанров, которые встречаются в лайках;
     * 4. перемешать и убрать дубликаты.
     */
    suspend fun generateWave(): Result<WaveSelection>
}
