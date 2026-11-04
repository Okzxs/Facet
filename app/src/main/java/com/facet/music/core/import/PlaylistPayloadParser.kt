package com.facet.music.core.import

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Разбор JSON плейлистов Deezer.
 *
 * Вынесен из загрузчика в чистые функции: их можно проверить обычными
 * JVM-тестами без сети и без Android. Ошибки бросаются исключением — сетевой
 * слой загрузчика превращает их в [PlaylistImportResult.Failure].
 */
object PlaylistPayloadParser {

    /**
     * `https://api.deezer.com/playlist/{id}`: название и `tracks.data`.
     *
     * Deezer отвечает `200` и с объектом `error` для несуществующего плейлиста,
     * поэтому ошибку проверяем до разбора треков.
     */
    fun deezerPlaylist(body: String, json: Json = Json): ImportedPlaylist {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.let { element ->
            val message = element.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("Deezer: ${message ?: "не удалось получить плейлист"}")
        }

        val name = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val tracks = root["tracks"]?.jsonObject
            ?.get("data")?.jsonArray
            ?.mapNotNull { element -> parseDeezerTrack(element.jsonObject) }
            .orEmpty()

        if (name.isBlank() && tracks.isEmpty()) {
            error("Deezer вернул пустой плейлист")
        }
        return ImportedPlaylist(
            name = name.ifBlank { DEEZER_FALLBACK_NAME },
            service = ImportService.DEEZER,
            tracks = tracks,
        )
    }

    private fun parseDeezerTrack(item: JsonObject): Track? {
        val id = item["id"]?.jsonPrimitive?.longOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = item["artist"]?.jsonObject
            ?.get("name")?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()
        val album = item["album"]?.jsonObject
        val artwork = album?.get("cover_big")?.jsonPrimitive?.contentOrNull
            ?: album?.get("cover_medium")?.jsonPrimitive?.contentOrNull

        return Track(
            id = "$DEEZER_ID_PREFIX$id",
            source = Source.DEEZER,
            title = title,
            artist = artist.ifBlank { UNKNOWN_ARTIST },
            artworkUrl = artwork,
            // Deezer — только метаданные: полную версию ищет StreamResolver на YouTube.
            streamUrl = null,
            durationMs = (item["duration"]?.jsonPrimitive?.intOrNull ?: 0) * 1000L,
            genre = null,
            transcodingUrl = "https://www.deezer.com/track/$id",
            isrc = item["isrc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            previewSeconds = null,
        )
    }

    private const val DEEZER_ID_PREFIX = "deezer:"
    private const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
    private const val DEEZER_FALLBACK_NAME = "Плейлист Deezer"
}
