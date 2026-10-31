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

    /** Один лайк: трек и момент, когда он был поставлен (0 — неизвестно). */
    data class LikeItem(
        val track: Track,
        val likedAtMillis: Long,
    )

    data class Page(
        val likes: List<LikeItem>,
        val nextHref: String?,
    ) {
        /** Только треки — удобно для превью и старых тестов. */
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
        // api-v2 отдаёт лайк объектом `{ "created_at": …, "track": { … } }`.
        // Старые ответы и тесты могут присылать трек напрямую — поддерживаем оба.
        val track = item["track"]?.jsonObject ?: item
        val permalink = track["permalink_url"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = track["title"]?.jsonPrimitive?.contentOrNull ?: return null
        if (permalink.isBlank() || title.isBlank()) return null
        val artist = track["user"]?.jsonObject?.get("username")
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        val artwork = track["artwork_url"]?.jsonPrimitive?.contentOrNull
        // full_duration — полная длина; duration у превью-треков равна 30 с.
        val duration = track["full_duration"]?.jsonPrimitive?.longOrNull
            ?: track["duration"]?.jsonPrimitive?.longOrNull
            ?: 0L
        // Время лайка храним, чтобы избранное сортировалось как в SoundCloud,
        // а не по порядку импорта.
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
