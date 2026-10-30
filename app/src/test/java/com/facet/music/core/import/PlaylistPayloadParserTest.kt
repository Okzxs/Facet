package com.facet.music.core.import

import com.facet.music.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор JSON Deezer и iTunes: проверяем поля доменной модели без сети.
 */
class PlaylistPayloadParserTest {

    @Test
    fun `deezer отдаёт название и треки с превью`() {
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
        assertEquals("https://cdn.deezer.com/preview.mp3", track.streamUrl)
        assertEquals(30, track.previewSeconds)
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

    @Test
    fun `itunes lookup возвращает только треки и крупную обложку`() {
        val tracks = PlaylistPayloadParser.appleLookupTracks(APPLE_BODY)

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals("apple:1440833098", track.id)
        assertEquals(Source.APPLE, track.source)
        assertEquals("Cachalot", track.title)
        assertEquals("Deep Sea", track.artist)
        assertEquals("https://is1.mzstatic.com/600x600bb.jpg", track.artworkUrl)
        assertEquals(184_000L, track.durationMs)
        assertEquals("https://audio.mzstatic.com/preview.m4a", track.streamUrl)
        assertEquals(30, track.previewSeconds)
        assertEquals("Electronic", track.genre)
    }

    @Test
    fun `itunes lookup без результатов даёт пустой список`() {
        assertTrue(
            PlaylistPayloadParser.appleLookupTracks("""{"resultCount":0,"results":[]}""").isEmpty(),
        )
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

        const val APPLE_BODY = """
        {
          "resultCount": 2,
          "results": [
            {
              "wrapperType": "collection",
              "collectionType": "Album",
              "collectionName": "Морская"
            },
            {
              "wrapperType": "track",
              "trackId": 1440833098,
              "trackName": "Cachalot",
              "artistName": "Deep Sea",
              "artworkUrl100": "https://is1.mzstatic.com/100x100bb.jpg",
              "trackTimeMillis": 184000,
              "previewUrl": "https://audio.mzstatic.com/preview.m4a",
              "primaryGenreName": "Electronic",
              "trackViewUrl": "https://music.apple.com/ru/song/1440833098"
            }
          ]
        }
        """
    }
}
