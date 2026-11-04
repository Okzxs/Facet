package com.facet.music.data.local

import android.content.Context
import com.facet.music.data.local.dao.QueueDao
import com.facet.music.data.local.entity.QueueEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.QueueSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    override suspend fun load(): QueueSnapshot? = withContext(Dispatchers.IO) {
        val rows = queueDao.getAll()
        if (rows.isEmpty()) return@withContext null
        val tracks = rows.mapNotNull { it.toTrack() }
        if (tracks.isEmpty()) return@withContext null
        val savedIndex = prefs.getInt(KEY_INDEX, 0)
        val savedPosition = prefs.getLong(KEY_POSITION, 0L)
        QueueSnapshot(
            tracks = tracks,
            currentIndex = savedIndex.coerceIn(0, tracks.lastIndex),
            positionMs = savedPosition.coerceAtLeast(0L),
        )
    }

    override suspend fun save(tracks: List<Track>, currentIndex: Int, positionMs: Long) =
        withContext(Dispatchers.IO) {
            if (tracks.isEmpty()) {
                clear()
                return@withContext
            }
            queueDao.replaceAll(tracks.mapIndexed { index, track -> track.toEntity(index) })
            prefs.edit()
                .putInt(KEY_INDEX, currentIndex.coerceIn(0, tracks.lastIndex))
                .putLong(KEY_POSITION, positionMs.coerceAtLeast(0L))
                .apply()
        }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        queueDao.clear()
        prefs.edit().remove(KEY_INDEX).remove(KEY_POSITION).apply()
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
        const val KEY_POSITION = "position_ms"
    }
}
