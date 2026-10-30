package com.facet.music.data.repository

import com.facet.music.data.model.LyricsResult

interface LyricsRepository {
    suspend fun getLyrics(title: String, artist: String, durationMs: Long): Result<LyricsResult>
}
