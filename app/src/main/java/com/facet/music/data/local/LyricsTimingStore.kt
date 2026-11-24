package com.facet.music.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsTimingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(trackId: String): List<Long> {
        if (trackId.isBlank()) return emptyList()
        val raw = prefs.getString(trackId, null) ?: return emptyList()
        return raw.split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    fun set(trackId: String, timings: List<Long>) {
        if (trackId.isBlank()) return
        if (timings.isEmpty()) {
            prefs.edit().remove(trackId).apply()
            return
        }
        prefs.edit().putString(trackId, timings.joinToString(",")).apply()
    }

    fun clear(trackId: String) {
        if (trackId.isNotBlank()) prefs.edit().remove(trackId).apply()
    }

    private companion object {
        const val PREFS_NAME = "lyrics_timings"
    }
}
