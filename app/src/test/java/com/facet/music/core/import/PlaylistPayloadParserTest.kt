package com.facet.music.core.import

import com.facet.music.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор JSON Deezer: проверяем поля доменной модели без сети.
 */
class PlaylistPayloadParserTest {

    @Test
    fun `deezer отдаёт метаданные без превью`() {
        val playlist = PlaylistPayloadParser.deezerPlaylist(DEEZER_BODY)

        assertEquals("Морская", playlist.name)
        assertEquals(ImportService.DEEZER, playlist.service)
        assertEquals(1, playlist.tracks.size)

        val track = playlist.tracks.single()
        assertEquals("deezer:3135556", track.id)
        assertEquals(Source.DEEZER, track.source)
        assertEquals("Cachalot", track.title)
        assertEquals("Deep Sea", track.artist)
        assertEquals("https://cdn.deezer.com/cover.jpg", track.artworkUrl)
        assertEquals(184_000L, track.durationMs)
        assertNull(track.streamUrl)
        assertNull(track.previewSeconds)
        assertEquals("ABC123", track.isrc)
        assertEquals("https://www.deezer.com/track/3135556", track.transcodingUrl)
    }

    @Test
    fun `deezer без названия подставляет запасное имя`() {
        val playlist = PlaylistPayloadParser.deezerPlaylist(
            """{"tracks":{"data":[{"id":1,"title":"Track","artist":{"name":"Artist"}}]}}""",
        )

        assertEquals("Плейлист Deezer", playlist.name)
        assertEquals(1, playlist.tracks.size)
        // Превью нет — трек играет через YouTube-матч, а не как превью.
        assertNull(playlist.tracks.single().streamUrl)
        assertNull(playlist.tracks.single().previewSeconds)
    }

    @Test
    fun `deezer с объектом error бросает исключение`() {
        assertThrows(IllegalStateException::class.java) {
            PlaylistPayloadParser.deezerPlaylist(
                """{"error":{"type":"DataException","message":"no data","code":800}}""",
            )
        }
    }

    @Test
    fun `deezer с пустым списком треков возвращает пустой плейлист`() {
        val playlist = PlaylistPayloadParser.deezerPlaylist(
            """{"title":"Пусто","tracks":{"data":[]}}""",
        )

        assertEquals("Пусто", playlist.name)
        assertTrue(playlist.tracks.isEmpty())
    }

    private companion object {
        const val DEEZER_BODY = """
        {
          "id": 1,
          "title": "Морская",
          "tracks": {
            "data": [
              {
                "id": 3135556,
                "title": "Cachalot",
                "artist": {"name": "Deep Sea"},
                "album": {"cover_big": "https://cdn.deezer.com/cover.jpg"},
                "duration": 184,
                "preview": "https://cdn.deezer.com/preview.mp3",
                "isrc": "ABC123"
              },
              {
                "id": 3135557,
                "title": "   ",
                "artist": {"name": "Broken"}
              }
            ]
          }
        }
        """
    }
}
