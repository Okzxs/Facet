package com.facet.music.core.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

data class WidgetSnapshot(
    val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String = "",
    val artToken: String = "",
    val isPlaying: Boolean = false,
    val isLiked: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val hasTrack: Boolean
        get() = title.isNotBlank()

    val hasArtwork: Boolean
        get() = artToken.isNotBlank() && artToken == trackId

    val progress: Float
        get() = WidgetFormat.fraction(positionMs, durationMs)
}

val WIDGET_TRACK_ID_KEY = stringPreferencesKey("track_id")
val WIDGET_TITLE_KEY = stringPreferencesKey("title")
val WIDGET_ARTIST_KEY = stringPreferencesKey("artist")
val WIDGET_ARTWORK_URL_KEY = stringPreferencesKey("artwork_url")
val WIDGET_ART_TOKEN_KEY = stringPreferencesKey("art_token")
val WIDGET_PLAYING_KEY = booleanPreferencesKey("playing")
val WIDGET_LIKED_KEY = booleanPreferencesKey("liked")
val WIDGET_POSITION_KEY = longPreferencesKey("position")
val WIDGET_DURATION_KEY = longPreferencesKey("duration")

internal fun Preferences.toWidgetSnapshot(): WidgetSnapshot = WidgetSnapshot(
    trackId = this[WIDGET_TRACK_ID_KEY].orEmpty(),
    title = this[WIDGET_TITLE_KEY].orEmpty(),
    artist = this[WIDGET_ARTIST_KEY].orEmpty(),
    artworkUrl = this[WIDGET_ARTWORK_URL_KEY].orEmpty(),
    artToken = this[WIDGET_ART_TOKEN_KEY].orEmpty(),
    isPlaying = this[WIDGET_PLAYING_KEY] ?: false,
    isLiked = this[WIDGET_LIKED_KEY] ?: false,
    positionMs = this[WIDGET_POSITION_KEY] ?: 0L,
    durationMs = this[WIDGET_DURATION_KEY] ?: 0L,
)

internal fun MutablePreferences.putSnapshot(snapshot: WidgetSnapshot) {
    this[WIDGET_TRACK_ID_KEY] = snapshot.trackId
    this[WIDGET_TITLE_KEY] = snapshot.title
    this[WIDGET_ARTIST_KEY] = snapshot.artist
    this[WIDGET_ARTWORK_URL_KEY] = snapshot.artworkUrl
    this[WIDGET_ART_TOKEN_KEY] = snapshot.artToken
    this[WIDGET_PLAYING_KEY] = snapshot.isPlaying
    this[WIDGET_LIKED_KEY] = snapshot.isLiked
    this[WIDGET_POSITION_KEY] = snapshot.positionMs
    this[WIDGET_DURATION_KEY] = snapshot.durationMs
}

object WidgetSnapshotStore {

    private const val PREFS_NAME = "facet_widget"
    private const val KEY_TRACK_ID = "track_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ARTWORK_URL = "artwork_url"
    private const val KEY_ART_TOKEN = "art_token"
    private const val KEY_PLAYING = "playing"
    private const val KEY_LIKED = "liked"
    private const val KEY_POSITION = "position"
    private const val KEY_DURATION = "duration"

    fun read(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetSnapshot(
            trackId = prefs.getString(KEY_TRACK_ID, "").orEmpty(),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            artworkUrl = prefs.getString(KEY_ARTWORK_URL, "").orEmpty(),
            artToken = prefs.getString(KEY_ART_TOKEN, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_PLAYING, false),
            isLiked = prefs.getBoolean(KEY_LIKED, false),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L),
        )
    }

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACK_ID, snapshot.trackId)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_ARTWORK_URL, snapshot.artworkUrl)
            .putString(KEY_ART_TOKEN, snapshot.artToken)
            .putBoolean(KEY_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_LIKED, snapshot.isLiked)
            .putLong(KEY_POSITION, snapshot.positionMs)
            .putLong(KEY_DURATION, snapshot.durationMs)
            .apply()
    }
}
