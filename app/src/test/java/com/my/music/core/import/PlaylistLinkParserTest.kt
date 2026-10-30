package com.my.music.core.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор ссылок на плейлисты: каждый сервис, query-параметры, мусор и пустая строка.
 * Тесты чистые JVM — парсер не трогает Android и сеть.
 */
class PlaylistLinkParserTest {

    private fun success(link: String): PlaylistLinkParseResult.Success =
        PlaylistLinkParser.parse(link) as? PlaylistLinkParseResult.Success
            ?: throw AssertionError("Ожидался успешный разбор для «$link»")

    private fun failure(link: String): PlaylistLinkParseResult.Failure =
        PlaylistLinkParser.parse(link) as? PlaylistLinkParseResult.Failure
            ?: throw AssertionError("Ожидалась ошибка для «$link»")

    @Test
    fun `open spotify без схемы разбирается в плейлист`() {
        val result = success("open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        assertEquals(ImportService.SPOTIFY, result.service)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", result.id)
    }

    @Test
    fun `spotify с query-параметром si сохраняет id плейлиста`() {
        val result = success(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=AbC123&nd=1",
        )

        assertEquals(ImportService.SPOTIFY, result.service)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", result.id)
    }

    @Test
    fun `spotify без поддомена open тоже поддержан`() {
        val result = success("https://spotify.com/playlist/5vB2z3kQ1x")

        assertEquals(ImportService.SPOTIFY, result.service)
        assertEquals("5vB2z3kQ1x", result.id)
    }

    @Test
    fun `яндекс музыка со ссылкой пользователя`() {
        val result = success("https://music.yandex.ru/users/souler/playlists/1034")

        assertEquals(ImportService.YANDEX, result.service)
        assertEquals("1034", result.id)
    }

    @Test
    fun `яндекс музыка по короткому пути playlists`() {
        val result = success("https://music.yandex.ru/playlists/1034?utm_source=share")

        assertEquals(ImportService.YANDEX, result.service)
        assertEquals("1034", result.id)
    }

    @Test
    fun `apple music плейлист берёт сегмент pl`() {
        val result = success(
            "https://music.apple.com/ru/playlist/my-favourites/pl.u-1a2b3c4d",
        )

        assertEquals(ImportService.APPLE, result.service)
        assertEquals("pl.u-1a2b3c4d", result.id)
    }

    @Test
    fun `apple music с параметром i это трек`() {
        val result = success(
            "https://music.apple.com/ru/playlist/my-favourites/pl.u-1a2b3c4d?i=1450331355",
        )

        assertEquals(ImportService.APPLE, result.service)
        assertEquals("pl.u-1a2b3c4d", result.id)
        assertEquals("1450331355", result.trackId)
    }

    @Test
    fun `apple music ссылка на трек по song`() {
        val result = success("https://music.apple.com/ru/song/cachalot/1440833098")

        assertEquals(ImportService.APPLE, result.service)
        assertEquals("1440833098", result.trackId)
    }

    @Test
    fun `deezer плейлист`() {
        val result = success("https://www.deezer.com/playlist/908622995")

        assertEquals(ImportService.DEEZER, result.service)
        assertEquals("908622995", result.id)
    }

    @Test
    fun `deezer с языковым сегментом ru`() {
        val result = success("http://deezer.com/ru/playlist/908622995/?utm=share")

        assertEquals(ImportService.DEEZER, result.service)
        assertEquals("908622995", result.id)
    }

    @Test
    fun `мусорная ссылка даёт понятную ошибку`() {
        val result = failure("https://example.com/playlist/123")

        assertEquals(ImportError.UNSUPPORTED_LINK, result.error)
        assertTrue(result.message.isNotBlank())
    }

    @Test
    fun `пустая строка даёт ошибку empty link`() {
        val result = failure("")

        assertEquals(ImportError.EMPTY_LINK, result.error)
    }

    @Test
    fun `строка из пробелов считается пустой`() {
        val result = failure("   \n\t ")

        assertEquals(ImportError.EMPTY_LINK, result.error)
    }

    @Test
    fun `spotify без идентификатора плейлиста не угадывается`() {
        val result = failure("https://open.spotify.com/playlist/")

        assertEquals(ImportError.UNSUPPORTED_LINK, result.error)
    }
}
