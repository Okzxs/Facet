package com.facet.music.data.source.soundcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudJsonTest {

    @Test
    fun `трек разбирается в доменную модель с numeric id`() {
        val dto = SoundCloudJson.parseTrack(trackJson(progressiveMp3()))

        assertEquals("12345", dto.id)
        assertEquals("https://soundcloud.com/artist/track", dto.permalinkUrl)
        assertEquals("Artist", dto.artist)
        assertEquals("Track", dto.title)
        assertEquals(30_000L, dto.durationMs)
        assertTrue(dto.trackAuthorization != null)

        val track = dto.toTrack()
        assertEquals("12345", track.externalId)
        assertEquals("https://soundcloud.com/artist/track", track.id)
    }

    @Test
    fun `policy SNIP помечает трек как Go+`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(progressiveMp3(snipped = true), policy = "SNIP"),
        )

        assertTrue(dto.isSubscriptionOnly)
    }

    @Test
    fun `для сниппета берётся полная длительность`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(progressiveMp3(snipped = true), fullDuration = 227_918L),
        )

        assertEquals(227_918L, dto.durationMs)
    }

    @Test
    fun `progressive MP3 важнее HLS`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(progressiveMp3(), hlsMp3(preset = "aac_160k")),
        )

        val selected = SoundCloudJson.selectTranscoding(dto, requireProgressive = false)

        assertTrue(selected.isProgressive)
        assertEquals("audio/mpeg", selected.mimeBase)
    }

    @Test
    fun `HLS MP3 важнее HLS Opus`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(hlsOpus(preset = "aac_160k"), hlsMp3(preset = "abr_sq")),
        )

        val selected = SoundCloudJson.selectTranscoding(dto, requireProgressive = false)

        assertTrue(selected.isHls)
        assertEquals("audio/mpegurl", selected.mimeBase)
    }

    @Test
    fun `зашифрованные протоколы пропускаются`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(
                transcoding(
                    protocol = "ctr-encrypted-hls",
                    mime = "audio/mp4",
                    preset = "aac_160k",
                ),
                hlsMp3(preset = "abr_sq"),
            ),
        )

        val selected = SoundCloudJson.selectTranscoding(dto, requireProgressive = false)

        assertEquals("abr_sq", selected.preset)
    }

    @Test
    fun `только snipped даёт SubscriptionOnly`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(progressiveMp3(snipped = true)),
        )

        val error = assertThrows(SoundCloudException::class.java) {
            SoundCloudJson.selectTranscoding(dto, requireProgressive = false)
        }

        assertEquals(SoundCloudFailure.SUBSCRIPTION_ONLY, error.failure)
        assertEquals(
            "Трек доступен только по подписке SoundCloud Go",
            error.message,
        )
    }

    @Test
    fun `policy BLOCK даёт GeoBlocked`() {
        val dto = SoundCloudJson.parseTrack(
            trackJson(progressiveMp3(), policy = "BLOCK"),
        )

        val error = assertThrows(SoundCloudException::class.java) {
            SoundCloudJson.selectTranscoding(dto, requireProgressive = false)
        }

        assertEquals(SoundCloudFailure.GEO_BLOCKED, error.failure)
    }

    @Test
    fun `скачивание не принимает HLS`() {
        val dto = SoundCloudJson.parseTrack(trackJson(hlsMp3()))

        val error = assertThrows(SoundCloudException::class.java) {
            SoundCloudJson.selectTranscoding(dto, requireProgressive = true)
        }

        assertEquals(SoundCloudFailure.PROGRESSIVE_UNAVAILABLE, error.failure)
    }

    @Test
    fun `URL транскодинга получает client_id и track_authorization`() {
        val dto = SoundCloudJson.parseTrack(trackJson(progressiveMp3()))
        val transcoding = SoundCloudJson.selectTranscoding(dto, requireProgressive = true)

        val url = SoundCloudJson.transcodingUrl(dto, transcoding, "cid123")

        assertTrue(url.contains("client_id=cid123"))
        assertTrue(url.contains("track_authorization=auth+1"))
    }

    @Test
    fun `коллекция пропускает битые элементы`() {
        val body = """{"collection":[${trackJson(progressiveMp3())},{"id":1}]}"""

        val tracks = SoundCloudJson.parseCollection(body)

        assertEquals(1, tracks.size)
        assertEquals("12345", tracks.first().id)
    }

    @Test
    fun `битый JSON — ошибка разбора`() {
        val error = assertThrows(SoundCloudException::class.java) {
            SoundCloudJson.parseTrack("not json")
        }

        assertEquals(SoundCloudFailure.PARSE, error.failure)
    }
}

private fun progressiveMp3(snipped: Boolean = false): String = transcoding(
    protocol = "progressive",
    mime = "audio/mpeg",
    preset = "mp3_128",
    snipped = snipped,
)

private fun hlsMp3(preset: String = "aac_160k"): String = transcoding(
    protocol = "hls",
    mime = "audio/mpegurl",
    preset = preset,
)

private fun hlsOpus(preset: String = "aac_96k"): String = transcoding(
    protocol = "hls",
    mime = "audio/mp4; codecs=\"mp4a.40.2\"",
    preset = preset,
)

private fun transcoding(
    protocol: String,
    mime: String,
    preset: String,
    snipped: Boolean = false,
): String = """
    {
      "url": "https://api-v2.soundcloud.com/media/1?foo=bar",
      "preset": "$preset",
      "snipped": $snipped,
      "format": {"protocol": "$protocol", "mime_type": "${mime.replace("\"", "\\\"")}"}
    }
""".trimIndent()

private fun trackJson(
    vararg transcodings: String,
    policy: String = "ALLOW",
    fullDuration: Long? = null,
): String = """
    {
      "kind": "track",
      "id": 12345,
      "permalink_url": "https://soundcloud.com/artist/track",
      "title": "Track",
      "duration": 30000,
      ${fullDuration?.let { "\"full_duration\": $it," }.orEmpty()}
      "genre": "Electronic",
      "policy": "$policy",
      "monetization_model": "NOT_APPLICABLE",
      "track_authorization": "auth 1",
      "user": {"username": "Artist", "avatar_url": "https://i1.sndcdn.com/avatar.jpg"},
      "artwork_url": "https://i1.sndcdn.com/artwork.jpg",
      "media": {"transcodings": [${transcodings.joinToString(",")}]}
    }
""".trimIndent()
