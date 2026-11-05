package com.facet.music.data.repository

import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPage
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistProfile
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import kotlinx.coroutines.flow.Flow

/**
 * Экран артиста на YouTube/SoundCloud: шапка, популярное, релизы, все треки,
 * плейлисты и похожие исполнители. Кэш страницы живёт в Room (TTL 6 ч).
 */
interface ArtistRepository {

    /** Первая страница: шапка, популярные треки и начало «Всех треков». */
    suspend fun artist(source: Source, url: String): Result<ArtistPage>

    /** Свежая страница из Room; null — кэша нет или он старше TTL. */
    suspend fun cached(source: Source, url: String): ArtistPage?

    /** Сохранить обновлённую страницу (после догрузки релизов и плейлистов). */
    suspend fun store(page: ArtistPage)

    suspend fun popularTracks(source: Source, url: String, limit: Int = 10): Result<List<Track>>

    suspend fun releases(source: Source, url: String): Result<List<ArtistRelease>>

    /** Следующая страница «Всех треков»; [nextToken] — из [ArtistPage]. */
    suspend fun allTracks(
        source: Source,
        url: String,
        nextToken: String? = null,
        limit: Int = 30,
    ): Result<TrackPage>

    /** Треки альбома/плейлиста по ссылке из релиза. */
    suspend fun releaseTracks(source: Source, url: String): Result<List<Track>>

    suspend fun playlists(source: Source, url: String): Result<List<ArtistPlaylist>>

    /** Похожие исполнители: SoundCloud — related треков, YouTube — Last.fm. */
    suspend fun related(source: Source, url: String, name: String): Result<List<Artist>>

    /** Старый маршрут artist/{name}: лучший результат поиска. */
    suspend fun resolveByName(name: String): Result<Artist?>

    /** Ключи подписанных артистов (`SOURCE:url`). */
    fun observeFollowed(): Flow<Set<String>>

    suspend fun setFollowed(profile: ArtistProfile, followed: Boolean)
}
