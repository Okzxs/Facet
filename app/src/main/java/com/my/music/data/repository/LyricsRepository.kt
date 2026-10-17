package com.my.music.data.repository

import com.my.music.data.model.LyricsResult

interface LyricsRepository {
    suspend fun getLyrics(title: String, artist: String, durationMs: Long): Result<LyricsResult>
}
