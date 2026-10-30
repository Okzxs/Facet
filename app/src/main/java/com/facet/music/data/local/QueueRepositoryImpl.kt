package com.facet.music.data.local

import android.content.Context
import com.facet.music.data.local.dao.QueueDao
import com.facet.music.data.local.entity.QueueEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.QueueSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Очередь в Room. Источник хранится строкой: `valueOf` переживает обфускацию,
 * а битое значение не должно ронять восстановление — такие строки пропускаем.
 * Позиция текущего трека — небольшое число в SharedPreferences: держать его
 * отдельной таблицей ради одного int смысла нет.
 */
@Singleton
class QueueRepositoryImpl @Inject constructor(
    private val queueDao: QueueDao,
    @ApplicationContext context: Context,
) : QueueRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): QueueSnapshot? {
        val rows = queueDao.getAll()
        if (rows.isEmpty()) return null
        val tracks = rows.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return null
        val savedIndex = prefs.getInt(KEY_INDEX, 0)
        return QueueSnapshot(
            tracks = tracks,
            currentIndex = savedIndex.coerceIn(0, tracks.lastIndex),
        )
    }

    override suspend fun save(tracks: List<Track>, currentIndex: Int) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        queueDao.replaceAll(tracks.mapIndexed { index, track -> track.toEntity(index) })
        prefs.edit().putInt(KEY_INDEX, currentIndex.coerceIn(0, tracks.lastIndex)).apply()
    }

    override suspend fun clear() {
        queueDao.clear()
        prefs.edit().remove(KEY_INDEX).apply()
    }

    private fun Track.toEntity(position: Int) = QueueEntity(
        position = position,
        trackId = id,
        source = source.name,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        genre = genre,
        transcodingUrl = transcodingUrl,
        isrc = isrc,
        previewSeconds = previewSeconds,
    )

    private fun QueueEntity.toTrack(): Track? {
        val parsedSource = runCatching { Source.valueOf(source) }.getOrNull() ?: return null
        if (trackId.isBlank()) return null
        return Track(
            id = trackId,
            source = parsedSource,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            streamUrl = streamUrl,
            durationMs = durationMs,
            genre = genre,
            transcodingUrl = transcodingUrl,
            isrc = isrc,
            previewSeconds = previewSeconds,
        )
    }

    private companion object {
        /** Позиция текущего трека: одно число, отдельная таблица не нужна. */
        const val PREFS_NAME = "queue_state"
        const val KEY_INDEX = "current_index"
    }
}
