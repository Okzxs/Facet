package com.facet.music.core.widget

import android.content.Context

data class WidgetSnapshot(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val accentArgb: Int = DEFAULT_ACCENT,
) {
    val hasTrack: Boolean
        get() = title.isNotBlank()

    companion object {
        const val DEFAULT_ACCENT: Int = 0xFFC8A46A.toInt()
    }
}

object WidgetSnapshotStore {

    private const val PREFS_NAME = "facet_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"
    private const val KEY_ACCENT = "accent"

    fun read(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            accentArgb = prefs.getInt(KEY_ACCENT, WidgetSnapshot.DEFAULT_ACCENT),
        )
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putInt(KEY_ACCENT, snapshot.accentArgb)
            .apply()
    }
}
