package com.facet.music.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facet.music.data.local.entity.PlaylistEntity
import com.facet.music.data.local.entity.PlaylistTrackEntity
import com.facet.music.data.local.entity.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

/**
 * DAO плейлистов и их треков.
 *
 * Списки отдаются потоками: после добавления/удаления трека экран перерисуется
 * сам, без ручного обновления. Число треков считается джойном, а не отдельным
 * запросом на каждую строку списка.
 */
@Dao
interface PlaylistDao {

    /** Плейлисты в порядке создания — без счётчиков (для служебных сценариев). */
    @Query("SELECT * FROM `playlists` ORDER BY `createdAt` ASC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    /** Плейлисты с числом треков и обложкой — основной запрос списков в UI. */
    @Query(
        """
        SELECT p.`id` AS id, p.`name` AS name, p.`createdAt` AS createdAt,
               COUNT(t.`trackId`) AS trackCount,
               (SELECT c.`artworkUrl` FROM `playlist_tracks` c
                WHERE c.`playlistId` = p.`id` AND c.`artworkUrl` IS NOT NULL
                ORDER BY c.`addedAt` ASC LIMIT 1) AS coverUrl
        FROM `playlists` p
        LEFT JOIN `playlist_tracks` t ON t.`playlistId` = p.`id`
        GROUP BY p.`id`
        ORDER BY p.`createdAt` ASC
        """,
    )
    fun observeAllWithCounts(): Flow<List<PlaylistWithCount>>

    /** Один плейлист со счётчиком — заголовок экрана плейлиста. */
    @Query(
        """
        SELECT p.`id` AS id, p.`name` AS name, p.`createdAt` AS createdAt,
               COUNT(t.`trackId`) AS trackCount,
               (SELECT c.`artworkUrl` FROM `playlist_tracks` c
                WHERE c.`playlistId` = p.`id` AND c.`artworkUrl` IS NOT NULL
                ORDER BY c.`addedAt` ASC LIMIT 1) AS coverUrl
        FROM `playlists` p
        LEFT JOIN `playlist_tracks` t ON t.`playlistId` = p.`id`
        WHERE p.`id` = :playlistId
        GROUP BY p.`id`
        """,
    )
    fun observePlaylistWithCount(playlistId: Long): Flow<PlaylistWithCount?>

    /** Треки плейлиста в порядке добавления. */
    @Query("SELECT * FROM `playlist_tracks` WHERE `playlistId` = :playlistId ORDER BY `addedAt` ASC")
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    /**
     * IGNORE, а не REPLACE: повторное добавление того же трека не должно
     * перезаписывать `addedAt` и двигать строку в конец списка.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    /** Удаление плейлиста; строки треков уходят каскадом по внешнему ключу. */
    @Query("DELETE FROM `playlists` WHERE `id` = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM `playlist_tracks` WHERE `playlistId` = :playlistId AND `trackId` = :trackId")
    suspend fun deleteTrack(playlistId: Long, trackId: String)

    @Query("SELECT COUNT(*) FROM `playlist_tracks` WHERE `playlistId` = :playlistId")
    suspend fun count(playlistId: Long): Int

    /** Всего строк во всех плейлистах — проверка каскада в тестах. */
    @Query("SELECT COUNT(*) FROM `playlist_tracks`")
    suspend fun countAllTracks(): Int

    @Query("UPDATE `playlists` SET `name` = :name WHERE `id` = :playlistId")
    suspend fun rename(playlistId: Long, name: String)
}
