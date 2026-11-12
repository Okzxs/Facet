package com.facet.music.core.import

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SoundCloudLikesParser {

    data class LikeItem(
        val track: Track,
        val likedAtMillis: Long,
    )

    data class Page(
        val likes: List<LikeItem>,
        val nextHref: String?,
    ) {
        val tracks: List<Track> get() = likes.map { it.track }
    }

    fun parse(body: String, json: Json = Json): Page {
        val root = json.parseToJsonElement(body).jsonObject
        val likes = root["collection"]?.jsonArray
            ?.mapNotNull { element -> toLike(element.jsonObject) }
            .orEmpty()
        val next = root["next_href"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        return Page(likes = likes, nextHref = next)
    }

    private fun toLike(item: kotlinx.serialization.json.JsonObject): LikeItem? {
        val track = item["track"]?.jsonObject ?: item
        val permalink = track["permalink_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = track["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (permalink.isBlank() || title.isBlank()) return null
        val artist = track["user"]?.jsonObject?.get("username")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        val artwork = track["artwork_url"]?.jsonPrimitive?.contentOrNull
        val duration = track["full_duration"]?.jsonPrimitive?.longOrNull
            ?: track["duration"]?.jsonPrimitive?.longOrNull
            ?: 0L
        val likedAt = item["created_at"]?.jsonPrimitive?.contentOrNull
            ?.let { createdAt ->
                runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrNull()
            }
            ?: 0L
        return LikeItem(
            track = Track(
                id = permalink,
                source = Source.SOUNDCLOUD,
                title = title,
                artist = artist,
                artworkUrl = artwork,
                streamUrl = null,
                durationMs = duration,
                genre = null,
                transcodingUrl = permalink,
            ),
            likedAtMillis = likedAt,
        )
    }
}
