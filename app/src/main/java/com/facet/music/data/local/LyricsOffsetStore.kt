package com.facet.music.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsOffsetStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(trackId: String): Int {
        if (trackId.isBlank()) return 0
        return prefs.getInt(trackId, 0).coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
    }

    fun set(trackId: String, offsetMs: Int) {
        if (trackId.isBlank()) return
        val value = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        if (value == 0) {
            prefs.edit().remove(trackId).apply()
        } else {
            prefs.edit().putInt(trackId, value).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "lyrics_offsets"
        const val MIN_OFFSET_MS = -2_000
        const val MAX_OFFSET_MS = 2_000
    }
}
