package com.facet.music.core.player

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.serialization.json.Json

/** MediaItem с сериализованным Track в extras: сервис восстанавливает очередь сам. */
object TrackMediaItem {

    private const val KEY_TRACK = "facet.track"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun defaultMimeType(track: Track): String = when (track.source) {
        Source.YOUTUBE -> MimeTypes.AUDIO_MP4
        else -> MimeTypes.AUDIO_MPEG
    }

    fun create(track: Track, mimeType: String = defaultMimeType(track)): MediaItem {
        val extras = Bundle().apply {
            putString(KEY_TRACK, json.encodeToString(Track.serializer(), track))
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.genre.orEmpty())
            .setDurationMs(track.durationMs)
            .setExtras(extras)
            .apply {
                track.artworkUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setArtworkUri(it.toUri()) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(PlaybackUri.forTrack(track.id))
            .setCustomCacheKey(track.id)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    fun trackOf(item: MediaItem?): Track? {
        val encoded = item?.mediaMetadata?.extras?.getString(KEY_TRACK) ?: return null
        return runCatching {
            json.decodeFromString(Track.serializer(), encoded)
        }.getOrNull()
    }

    fun withMimeType(item: MediaItem, mimeType: String): MediaItem =
        item.buildUpon().setMimeType(mimeType).build()

    fun artworkUriOf(item: MediaItem?): Uri? = item?.mediaMetadata?.artworkUri
}
