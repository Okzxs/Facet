package com.my.music.data.repository

import com.my.music.data.model.Playlist
import com.my.music.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Плейлисты пользователя.
 *
 * Наблюдаемые методы не бросают исключений: поток просто перестаёт эмитить,
 * а ошибку UI показывает через свои состояния. Изменяющие методы бросают —
 * вызывающая сторона ловит их через `runCatching` + `rethrowCancellation`
 * (стиль проекта: репозитории работают с доменными моделями, ошибки
 * превращает в сообщения ViewModel).
 */
interface PlaylistRepository {

    /** Все плейлисты с числом треков, в порядке создания. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** Один плейлист со счётчиком; null — если его уже удалили. */
    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    /** Треки плейлиста в порядке добавления. */
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    /** Создаёт плейлист и возвращает его id. Имя обрезается по краям. */
    suspend fun createPlaylist(name: String): Long

    /** Добавляет трек; повторное добавление не создаёт дубль. */
    suspend fun addTrack(playlistId: Long, track: Track)

    suspend fun removeTrack(playlistId: Long, trackId: String)

    suspend fun deletePlaylist(playlistId: Long)

    suspend fun rename(playlistId: Long, name: String)
}
