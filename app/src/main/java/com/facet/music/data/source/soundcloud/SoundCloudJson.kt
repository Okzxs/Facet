package com.facet.music.data.source.soundcloud

import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/** Причина, по которой api-v2 не отдал поток. */
internal enum class SoundCloudFailure {
    GEO_BLOCKED,
    SUBSCRIPTION_ONLY,
    PROGRESSIVE_UNAVAILABLE,
    UNAVAILABLE,
    PARSE,
    HTTP,
}

internal class SoundCloudException(
    val failure: SoundCloudFailure,
    message: String,
    val code: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

internal data class SoundCloudTranscoding(
    val url: String,
    val protocol: String,
    val mimeType: String,
    val preset: String,
    val snipped: Boolean,
) {

    val isProgressive: Boolean
        get() = protocol.equals("progressive", ignoreCase = true)

    val isHls: Boolean
        get() = protocol.contains("hls", ignoreCase = true)

    val isEncrypted: Boolean
        get() = protocol.contains("encrypted", ignoreCase = true)

    val mimeBase: String
        get() = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)

    val isMp3: Boolean
        get() = mimeBase in MP3_MIMES

    val isOpusOrAac: Boolean
        get() = mimeBase in OPUS_AAC_MIMES

    val quality: Int
        get() = Regex("""(\d{2,3})k""").find(preset)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: when {
                preset.contains("abr") -> 80
                else -> 0
            }

    private companion object {
        val MP3_MIMES = setOf("audio/mpeg", "audio/mp3", "audio/mpegurl")
        val OPUS_AAC_MIMES = setOf("audio/mp4", "audio/aac", "audio/ogg", "audio/opus")
    }
}

internal data class SoundCloudTrackDto(
    val id: String,
    val permalinkUrl: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String?,
    val policy: String,
    val monetizationModel: String,
    val trackAuthorization: String?,
    val transcodings: List<SoundCloudTranscoding>,
    val playbackCount: Long = 0L,
) {

    val isGeoBlocked: Boolean
        get() = policy.equals("BLOCK", ignoreCase = true)

    val isSubscriptionOnly: Boolean
        get() = policy.equals("SNIP", ignoreCase = true) ||
            monetizationModel.equals("SUB_HIGH_TIER", ignoreCase = true) ||
            transcodings.any { it.snipped } && transcodings.none { !it.snipped && !it.isEncrypted }

    fun toTrack(): Track = Track(
        id = permalinkUrl,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        streamUrl = null,
        durationMs = durationMs,
        genre = genre,
        transcodingUrl = permalinkUrl,
        externalId = id,
        isSubscriptionOnly = isSubscriptionOnly,
    )
}

/**
 * Чистый разбор ответов api-v2.soundcloud.com. Отделён от сети, чтобы
 * выбор транскодинга и типизацию ошибок можно было проверить JVM-тестами.
 */
internal object SoundCloudJson {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTrack(body: String): SoundCloudTrackDto {
        val root = parseObject(body)
        val kind = root.string("kind")
        if (kind != null && kind != "track") {
            throw SoundCloudException(
                SoundCloudFailure.UNAVAILABLE,
                "SoundCloud вернул не трек: $kind",
            )
        }
        return parseTrackObject(root)
    }

    fun parseCollection(body: String): List<SoundCloudTrackDto> {
        val root = parseObject(body)
        val items = (root["collection"] as? kotlinx.serialization.json.JsonArray)
            ?: (root["tracks"] as? kotlinx.serialization.json.JsonArray)
            ?: return emptyList()
        return items.mapNotNull { element ->
            runCatching { parseTrackObject(element.jsonObject) }.getOrNull()
        }
    }

    /** Пользователи SoundCloud для секции «Исполнители». */
    fun parseUsers(body: String): List<Artist> {
        val root = parseObject(body)
        val items = (root["collection"] as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        return items.mapNotNull { element ->
            val user = element as? JsonObject ?: return@mapNotNull null
            val name = user.string("username") ?: return@mapNotNull null
            val permalink = user.string("permalink_url") ?: return@mapNotNull null
            val id = user.string("id") ?: permalink
            Artist(
                id = id,
                source = Source.SOUNDCLOUD,
                name = name,
                avatarUrl = user.string("avatar_url"),
                subscriberCount = user.long("followers_count")?.takeIf { it > 0 },
                isVerified = (user["verified"] as? JsonPrimitive)?.booleanOrNull ?: false,
                url = permalink,
            )
        }
    }

    fun parseStreamUrl(body: String): String =
        parseObject(body).string("url")
            ?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: throw SoundCloudException(
                SoundCloudFailure.PARSE,
                "SoundCloud не вернул URL потока",
            )

    /**
     * Progressive MP3 важнее HLS MP3, HLS MP3 — важнее HLS Opus/AAC.
     * Зашифрованные протоколы и `snipped` отбрасываются всегда.
     */
    fun selectTranscoding(
        track: SoundCloudTrackDto,
        requireProgressive: Boolean,
    ): SoundCloudTranscoding = selectTranscodings(track, requireProgressive).first()

    /**
     * Все подходящие транскодинги в порядке приоритета: если первый
     * endpoint отвечает 404 (у SoundCloud бывает у abr_sq), пробуем следующий.
     */
    fun selectTranscodings(
        track: SoundCloudTrackDto,
        requireProgressive: Boolean,
    ): List<SoundCloudTranscoding> {
        if (track.isGeoBlocked) {
            throw SoundCloudException(
                SoundCloudFailure.GEO_BLOCKED,
                "Трек заблокирован в вашем регионе",
            )
        }

        val usable = track.transcodings.filter { transcoding ->
            transcoding.url.isNotBlank() && !transcoding.snipped && !transcoding.isEncrypted
        }

        if (usable.isEmpty()) {
            if (track.transcodings.any { it.snipped } || track.isSubscriptionOnly) {
                throw SoundCloudException(
                    SoundCloudFailure.SUBSCRIPTION_ONLY,
                    "Трек доступен только по подписке SoundCloud Go",
                )
            }
            throw SoundCloudException(
                SoundCloudFailure.UNAVAILABLE,
                "У трека нет доступных транскодингов",
            )
        }

        val progressive = usable
            .filter { it.isProgressive && it.isMp3 }
            .sortedByDescending { it.quality }
        if (progressive.isNotEmpty()) return progressive

        if (requireProgressive) {
            throw SoundCloudException(
                SoundCloudFailure.PROGRESSIVE_UNAVAILABLE,
                "У трека нет progressive MP3",
            )
        }

        val hlsMp3 = usable
            .filter { it.isHls && it.isMp3 }
            .sortedByDescending { it.quality }
        val hlsOther = usable
            .filter { it.isHls && it.isOpusOrAac }
            .sortedByDescending { it.quality }
        val hls = hlsMp3 + hlsOther
        if (hls.isNotEmpty()) return hls

        throw SoundCloudException(
            SoundCloudFailure.UNAVAILABLE,
            "Нет подходящего аудио-транскодинга",
        )
    }

    fun transcodingUrl(
        track: SoundCloudTrackDto,
        transcoding: SoundCloudTranscoding,
        clientId: String,
    ): String {
        val url = transcoding.url
        val separator = if (url.contains('?')) '&' else '?'
        return buildString {
            append(url)
            if (!url.contains("client_id=")) {
                append(separator)
                append("client_id=")
                append(encode(clientId))
            }
            val authorization = track.trackAuthorization
            if (!authorization.isNullOrBlank() && !url.contains("track_authorization=")) {
                append('&')
                append("track_authorization=")
                append(encode(authorization))
            }
        }
    }

    fun parseObject(body: String): JsonObject = try {
        json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
        throw SoundCloudException(
            SoundCloudFailure.PARSE,
            "Некорректный JSON SoundCloud",
            cause = e,
        )
    }

    private fun parseTrackObject(root: JsonObject): SoundCloudTrackDto {
        val id = root.string("id")
            ?: throw SoundCloudException(SoundCloudFailure.PARSE, "У трека нет id")
        val permalink = root.string("permalink_url")
            ?: throw SoundCloudException(SoundCloudFailure.PARSE, "У трека нет permalink")
        val media = root["media"] as? JsonObject
        val transcodings = (media?.get("transcodings") ?: root["transcodings"])
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { runCatching { parseTranscoding(it.jsonObject) }.getOrNull() }
            .orEmpty()
        val user = root["user"] as? JsonObject

        return SoundCloudTrackDto(
            id = id,
            permalinkUrl = permalink,
            title = root.string("title") ?: "Unknown",
            artist = user?.string("username") ?: root.string("username") ?: "Unknown",
            artworkUrl = root.string("artwork_url"),
            durationMs = root.long("full_duration") ?: root.long("duration") ?: 0L,
            genre = root.string("genre"),
            policy = root.string("policy").orEmpty(),
            monetizationModel = root.string("monetization_model").orEmpty(),
            trackAuthorization = root.string("track_authorization"),
            transcodings = transcodings,
            playbackCount = root.long("playback_count") ?: 0L,
        )
    }

    private fun parseTranscoding(obj: JsonObject): SoundCloudTranscoding {
        val format = obj["format"] as? JsonObject
        return SoundCloudTranscoding(
            url = obj.string("url").orEmpty(),
            protocol = format?.string("protocol").orEmpty(),
            mimeType = format?.string("mime_type").orEmpty(),
            preset = obj.string("preset").orEmpty(),
            snipped = (obj["snipped"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
