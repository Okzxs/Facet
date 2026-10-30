package com.my.music.data.local

import com.my.music.data.local.dao.PlaylistDao
import com.my.music.data.local.entity.PlaylistEntity
import com.my.music.data.local.entity.PlaylistTrackEntity
import com.my.music.data.local.entity.PlaylistWithCount
import com.my.music.data.model.Playlist
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Плейлисты поверх Room.
 *
 * Entity ↔ доменная модель маппится здесь: UI и ViewModel не знают про Room.
 * Трек из плейлиста собирается из денормализованных колонок, поэтому список
 * открывается без сети; `streamUrl` пуст — поток резолвит плеер по источнику
 * и `transcodingUrl`, как и для обычного поиска.
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao,
) : PlaylistRepository {

    override fun observePlaylists(): Flow<List<Playlist>> =
        dao.observeAllWithCounts().map { rows -> rows.map { it.toPlaylist() } }

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        dao.observePlaylistWithCount(playlistId).map { it?.toPlaylist() }

    override fun observeTracks(playlistId: Long): Flow<List<Track>> =
        dao.observeTracks(playlistId).map { rows -> rows.map { it.toTrack() } }

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim()))

    override suspend fun addTrack(playlistId: Long, track: Track) {
        dao.insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                source = track.source.name,
                transcodingUrl = track.transcodingUrl,
            ),
        )
    }

    override suspend fun removeTrack(playlistId: Long, trackId: String) {
        dao.deleteTrack(playlistId, trackId)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        // Строки playlist_tracks удаляет каскад внешнего ключа.
        dao.deletePlaylist(playlistId)
    }

    override suspend fun rename(playlistId: Long, name: String) {
        dao.rename(playlistId, name.trim())
    }
}

private fun PlaylistWithCount.toPlaylist(): Playlist = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    trackCount = trackCount,
    coverUrl = coverUrl,
)

/**
 * Собирает доменный трек из строки плейлиста.
 * Неизвестное имя источника (старая запись) не должно ронять экран — считаем
 * такой трек локальным и просто не даём ему зарезолвиться.
 */
private fun PlaylistTrackEntity.toTrack(): Track = Track(
    id = trackId,
    source = runCatching { Source.valueOf(source) }.getOrDefault(Source.LOCAL),
    title = title,
    artist = artist,
    artworkUrl = artworkUrl,
    streamUrl = null,
    durationMs = durationMs,
    genre = null,
    transcodingUrl = transcodingUrl,
)
