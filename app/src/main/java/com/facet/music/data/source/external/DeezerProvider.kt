package com.facet.music.data.source.external

import android.util.Log
import com.facet.music.core.network.RateLimiter
import com.facet.music.core.network.RateLimiterKeys
import com.facet.music.core.network.SourceRateLimiter
import com.facet.music.core.source.MusicSourceProvider
import com.facet.music.core.source.SourceCapabilities
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SearchCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Артист Deezer: имя, аватар и публичная статистика для шапки экрана.
 *
 * @param fans число слушателей (`nb_fan`), 0 — Deezer не отдал поле;
 * @param albums число альбомов (`nb_album`).
 */
data class DeezerArtist(
    val id: Long,
    val name: String,
    val pictureUrl: String?,
    val fans: Int,
    val albums: Int,
)

/** Альбом Deezer в карусели экрана артиста. */
data class DeezerAlbum(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    /** Год релиза (`release_date`), null — Deezer не отдал дату. */
    val year: String?,
)

/** Похожий артист: id нужен для запросов, имя — для перехода по маршруту. */
data class DeezerArtistRef(
    val id: Long,
    val name: String,
    val pictureUrl: String?,
)

/**
 * Deezer — бесплатный публичный поиск без авторизации.
 *
 * Отдаёт полноценные метаданные и **30-секундный** MP3-превью (`preview`).
 * Полный трек требует ARL-cookie и приватного API — это сознательно не делаем:
 * играем превью, а при его отсутствии [com.facet.music.core.source.StreamResolver]
 * ищет трек на YouTube.
 *
 * Успешные ответы поиска кэшируются как сырой JSON (Room, TTL 24 ч),
 * а сеть вызывается не чаще раза в секунду ([RateLimiter]).
 */
@Singleton
class DeezerProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val searchCache: SearchCacheRepository,
    @SourceRateLimiter(RateLimiterKeys.DEEZER) private val rateLimiter: RateLimiter,
) : MusicSourceProvider {

    override val source: Source = Source.DEEZER

    override val capabilities = SourceCapabilities(
        search = true,
        preview = true,
        fullStream = false,
        requiresAuth = false,
        supportsDownload = false,
    )

    override suspend fun search(
        query: String,
        limit: Int,
        useCache: Boolean,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                loadTracks(query = query, limit = limit, useCache = useCache)
            }.onFailure { error -> Log.w(TAG, "Поиск Deezer не удался: ${error.message}") }
        }

    /**
     * Популярные треки артиста для экрана артиста: сначала поиск артиста
     * (`/search/artist`), затем его топ (`/artist/{id}/top`).
     *
     * Оба запроса проходят через лимитер источника. Пустой список — артист
     * не найден (или у него нет топа), это не ошибка: экран уйдёт в фолбэк.
     */
    suspend fun fetchArtistTopTracks(
        artistName: String,
        limit: Int = ARTIST_TOP_LIMIT,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val artistId = findArtistId(artistName) ?: return@runCatching emptyList()
                topTracks(artistId, limit)
            }.onFailure { error ->
                Log.w(TAG, "Загрузка артиста Deezer не удалась: ${error.message}")
            }
        }

    /** Топ треков по уже известному id — без повторного поиска артиста. */
    suspend fun fetchArtistTopTracks(
        artistId: Long,
        limit: Int = ARTIST_TOP_LIMIT,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching { topTracks(artistId, limit) }
                .onFailure { error ->
                    Log.w(TAG, "Топ треков Deezer не удался: ${error.message}")
                }
        }

    /**
     * Треки артиста через обычный поиск с фильтром по имени исполнителя.
     *
     * Нужен как дополнение к топу: у части артистов Deezer отдаёт в `/top`
     * всего один трек, и без этого экран выглядел бы полупустым. Расширенный
     * синтаксис `artist:"…"` публичный API не поддерживает — фильтруем сами.
     */
    suspend fun searchArtistTracks(
        artistName: String,
        limit: Int = ARTIST_FILL_LIMIT,
    ): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/search?q=${encode(artistName)}" +
                    "&limit=${limit.coerceIn(1, MAX_LIMIT)}"
                val root = rateLimiter.acquire { requestJson(url) }
                root["data"]?.jsonArray
                    ?.mapNotNull { element -> parseTrack(element.jsonObject) }
                    ?.filter { track ->
                        track.artist.contains(artistName, ignoreCase = true)
                    }
                    .orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "Поиск треков артиста Deezer не удался: ${error.message}")
            }
        }

    /** Шапка артиста: аватар и публичная статистика. null — артист не найден. */
    suspend fun fetchArtist(artistName: String): Result<DeezerArtist?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/search/artist?q=${encode(artistName)}" +
                    "&limit=$ARTIST_SEARCH_LIMIT"
                val root = rateLimiter.acquire { requestJson(url) }
                root["data"]?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.let(::parseArtist)
            }.onFailure { error ->
                Log.w(TAG, "Артист Deezer не найден: ${error.message}")
            }
        }

    /** Альбомы артиста для карусели: свежие сверху, как в стримингах. */
    suspend fun fetchArtistAlbums(
        artistId: Long,
        limit: Int = ARTIST_ALBUMS_LIMIT,
    ): Result<List<DeezerAlbum>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/artist/$artistId/albums" +
                    "?limit=${limit.coerceIn(1, MAX_ARTIST_ALBUMS)}"
                val root = rateLimiter.acquire { requestJson(url) }
                root["data"]?.jsonArray
                    ?.mapNotNull { element -> parseAlbum(element.jsonObject) }
                    .orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "Альбомы Deezer не загрузились: ${error.message}")
            }
        }

    /** Похожие артисты Deezer — без ключей Last.fm и с готовыми аватарами. */
    suspend fun fetchRelatedArtists(
        artistId: Long,
        limit: Int = ARTIST_RELATED_LIMIT,
    ): Result<List<DeezerArtistRef>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL/artist/$artistId/related" +
                    "?limit=${limit.coerceIn(1, MAX_ARTIST_RELATED)}"
                val root = rateLimiter.acquire { requestJson(url) }
                root["data"]?.jsonArray
                    ?.mapNotNull { element -> parseArtistRef(element.jsonObject) }
                    .orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "Похожие артисты Deezer не загрузились: ${error.message}")
            }
        }

    /**
     * Треки альбома одним запросом: `/album/{id}` уже содержит `tracks.data`.
     * Нужно для «Слушать альбом» в карусели экрана артиста.
     */
    suspend fun fetchAlbumTracks(albumId: Long): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = rateLimiter.acquire { requestJson("$BASE_URL/album/$albumId") }
                root["tracks"]?.jsonObject?.get("data")?.jsonArray
                    ?.mapNotNull { element -> parseTrack(element.jsonObject) }
                    .orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "Альбом Deezer не загрузился: ${error.message}")
            }
        }

    /** Поиск артиста по имени: id нужен для топа, альбомов и похожих. */
    private suspend fun findArtistId(artistName: String): Long? {
        val searchUrl = "$BASE_URL/search/artist?q=${encode(artistName)}" +
            "&limit=$ARTIST_SEARCH_LIMIT"
        val searchRoot = rateLimiter.acquire { requestJson(searchUrl) }
        return searchRoot["data"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")?.jsonPrimitive?.longOrNull
    }

    private suspend fun topTracks(artistId: Long, limit: Int): List<Track> {
        val topUrl = "$BASE_URL/artist/$artistId/top" +
            "?limit=${limit.coerceIn(1, MAX_ARTIST_TOP)}"
        val topRoot = rateLimiter.acquire { requestJson(topUrl) }
        return topRoot["data"]?.jsonArray
            ?.mapNotNull { element -> parseTrack(element.jsonObject) }
            .orEmpty()
    }

    private fun parseArtist(item: JsonObject): DeezerArtist? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (name.isBlank()) return null
        return DeezerArtist(
            id = id,
            name = name,
            pictureUrl = item["picture_xl"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: item["picture_big"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            fans = item["nb_fan"]?.jsonPrimitive?.intOrNull ?: 0,
            albums = item["nb_album"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun parseAlbum(item: JsonObject): DeezerAlbum? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val cover = item["cover_big"]?.jsonPrimitive?.contentOrNull
            ?: item["cover_medium"]?.jsonPrimitive?.contentOrNull
        return DeezerAlbum(
            id = id,
            title = title,
            coverUrl = cover?.takeIf { it.isNotBlank() },
            // В ответе /artist/{id}/albums нет nb_tracks, зато есть дата релиза:
            // показываем год, а не «0 треков».
            year = item["release_date"]?.jsonPrimitive?.contentOrNull
                ?.take(4)
                ?.takeIf { it.length == 4 },
        )
    }

    private fun parseArtistRef(item: JsonObject): DeezerArtistRef? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (name.isBlank()) return null
        val picture = item["picture_medium"]?.jsonPrimitive?.contentOrNull
            ?: item["picture_big"]?.jsonPrimitive?.contentOrNull
        return DeezerArtistRef(
            id = id,
            name = name,
            pictureUrl = picture?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Превью Deezer живёт ограниченное время. Если ссылка протухла, ищем трек
     * заново по ISRC (точнее всего) или по «артист — название».
     */
    override suspend fun resolve(track: Track): Result<AudioStreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = track.isrc?.takeIf { it.isNotBlank() }
                    ?: listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" ")
                val url = "$BASE_URL/search?q=${encode(query)}&limit=$RESOLVE_LIMIT"
                // Ссылка превью одноразовая: кэш тут только помешал бы, поэтому
                // ограничиваем лишь частоту запроса.
                val root = rateLimiter.acquire { requestJson(url) }
                val refresh = root["data"]?.jsonArray
                    ?.mapNotNull { parseTrack(it.jsonObject) }
                    ?.firstOrNull { candidate ->
                        candidate.isrc != null && candidate.isrc == track.isrc
                    }
                    ?: root["data"]?.jsonArray
                        ?.mapNotNull { parseTrack(it.jsonObject) }
                        ?.firstOrNull()
                val streamUrl = refresh?.streamUrl
                    ?: error("Deezer не отдал превью для «${track.title}»")
                AudioStreamResult(url = streamUrl, isHls = false, mimeType = MIME_MP3)
            }.onFailure { error -> Log.w(TAG, "Resolve Deezer не удался: ${error.message}") }
        }

    /** Кэш → сеть. В кэш попадает только успешно разобранный ответ. */
    private suspend fun loadTracks(query: String, limit: Int, useCache: Boolean): List<Track> {
        if (useCache) {
            searchCache.get(query, source)?.let { cached ->
                // Битый кэш не должен ломать поиск: молча уходим в сеть.
                runCatching { parseTracks(cached) }.getOrNull()?.let { return it }
            }
        }

        val url = "$BASE_URL/search?q=${encode(query)}&limit=${limit.coerceIn(1, MAX_LIMIT)}"
        val body = rateLimiter.acquire { requestBody(url) }
        // Разбор идёт до записи в кэш: ответ, который не парсится, — не успех.
        val tracks = parseTracks(body)
        if (useCache) searchCache.put(query, source, body)
        return tracks
    }

    /** Разбор тела ответа — общий для свежего и закэшированного JSON. */
    private fun parseTracks(body: String): List<Track> =
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.mapNotNull { element -> parseTrack(element.jsonObject) }
            .orEmpty()

    private fun parseTrack(item: JsonObject): Track? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()
        val album = item["album"]?.jsonObject
        val artwork = album?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: album?.get("cover_medium")?.jsonPrimitive?.contentOrNull
        val durationSec = item["duration"]?.jsonPrimitive?.intOrNull ?: 0
        val preview = item["preview"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        return Track(
            id = "$ID_PREFIX$id",
            source = Source.DEEZER,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            streamUrl = preview,
            durationMs = durationSec * 1000L,
            genre = null,
            transcodingUrl = "https://www.deezer.com/track/$id",
            isrc = item["isrc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            previewSeconds = if (preview != null) PREVIEW_SECONDS else null,
        )
    }

    private fun requestJson(url: String): JsonObject =
        json.parseToJsonElement(requestBody(url)).jsonObject

    private fun requestBody(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Deezer HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "DeezerProvider"
        const val BASE_URL = "https://api.deezer.com"
        const val ID_PREFIX = "deezer:"
        const val MAX_LIMIT = 50
        const val RESOLVE_LIMIT = 10
        const val ARTIST_SEARCH_LIMIT = 1
        const val ARTIST_TOP_LIMIT = 20
        const val MAX_ARTIST_TOP = 50
        const val ARTIST_ALBUMS_LIMIT = 12
        const val MAX_ARTIST_ALBUMS = 50
        const val ARTIST_RELATED_LIMIT = 10
        const val MAX_ARTIST_RELATED = 20
        const val ARTIST_FILL_LIMIT = 50
        const val PREVIEW_SECONDS = 30
        const val MIME_MP3 = "audio/mpeg"
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
    }
}
