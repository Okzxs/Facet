package com.facet.music.data.repository

import com.facet.music.core.download.DownloadCapability
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage

/**
 * Источник контента YouTube через NewPipe Extractor.
 *
 * Для прослушивания и скачивания используем только progressive-аудио:
 * прямые M4A (AAC) или WEBM_OPUS. HLS/DASH-сегменты не подходят,
 * потому что из них нельзя собрать один файл.
 */
interface YouTubeRepository {

    /** Способность источника отдать цельный файл для скачивания. */
    val downloadCapability: DownloadCapability

    /**
     * Поиск видео на YouTube.
     * @param query строка поиска
     * @param limit максимальное количество результатов
     */
    suspend fun search(query: String, limit: Int = 20): Result<List<Track>>

    /** Страница поиска: music_songs, при малом результате добирается videos. */
    suspend fun searchPage(
        query: String,
        limit: Int = 20,
        nextToken: String? = null,
    ): Result<TrackPage>

    /** Каналы: music_artists, если пусто — channels. */
    suspend fun searchArtists(query: String, limit: Int = 10): Result<List<Artist>>

    /** Только музыкальная выдача YouTube Music — для треков артиста. */
    suspend fun searchMusicSongs(query: String, limit: Int = 10): Result<List<Track>>

    /** Альбомы YouTube Music — запасной путь, если у канала нет вкладки релизов. */
    suspend fun searchMusicAlbums(query: String, limit: Int = 20): Result<List<ArtistRelease>>

    /** Плейлисты YouTube Music — запасной путь для секции «Плейлисты». */
    suspend fun searchMusicPlaylists(query: String, limit: Int = 20): Result<List<ArtistPlaylist>>

    /**
     * Получить треки, похожие на видео [trackUrl].
     *
     * NewPipe отдаёт алгоритмические related-видео самого YouTube. Это
     * основной источник персональных рекомендаций: он находит исполнителей,
     * которых нет в лайках и истории пользователя.
     *
     * @param trackUrl URL страницы видео
     * @param limit максимальное количество related-треков
     */
    suspend fun getRelated(trackUrl: String, limit: Int = 30): Result<List<Track>>

    /**
     * Получить прямой progressive-аудиопоток по URL видео.
     * Возвращает [AudioStreamResult] с `isHls = false` либо ошибку,
     * если progressive-аудио недоступно.
     */
    suspend fun resolveStreamUrl(videoUrl: String): Result<AudioStreamResult>
}
