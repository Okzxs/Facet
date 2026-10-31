package com.facet.music.core.import

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Разбор ответа api-v2.soundcloud.com `/users/{id}/track_likes`.
 *
 * Страница выглядит как `{ "collection": [ …треки… ], "next_href": "…" }`.
 * Треки без permalink или названия пропускаем: их нельзя ни сохранить, ни
 * потом дорезолвить.
 */
object SoundCloudLikesParser {

    data class Page(
        val tracks: List<Track>,
        val nextHref: String?,
    )

    fun parse(body: String, json: Json = Json): Page {
        val root = json.parseToJsonElement(body).jsonObject
        val tracks = root["collection"]?.jsonArray
            ?.mapNotNull { element -> toTrack(element.jsonObject) }
            .orEmpty()
        val next = root["next_href"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        return Page(tracks = tracks, nextHref = next)
    }

    private fun toTrack(item: kotlinx.serialization.json.JsonObject): Track? {
        val permalink = item["permalink_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (permalink.isBlank() || title.isBlank()) return null
        val artist = item["user"]?.jsonObject?.get("username")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        val artwork = item["artwork_url"]?.jsonPrimitive?.contentOrNull
        val duration = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L
        return Track(
            id = permalink,
            source = Source.SOUNDCLOUD,
            title = title,
            artist = artist,
            artworkUrl = artwork,
            streamUrl = null,
            durationMs = duration,
            genre = null,
            transcodingUrl = permalink,
        )
    }
}
