package com.facet.music.core.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistLinkParserExtraTest {

    private fun success(link: String): PlaylistLinkParseResult.Success {
        val result = PlaylistLinkParser.parse(link)
        assertTrue("ожидался успех для $link, получено $result", result is PlaylistLinkParseResult.Success)
        return result as PlaylistLinkParseResult.Success
    }

    @Test
    fun `youtube playlist url is parsed from list parameter`() {
        val parsed = success("https://www.youtube.com/playlist?list=PL123abc")

        assertEquals(ImportService.YOUTUBE, parsed.service)
        assertEquals("PL123abc", parsed.id)
    }

    @Test
    fun `youtube music playlist and watch link with list are supported`() {
        assertEquals(
            "PLmusic",
            success("https://music.youtube.com/playlist?list=PLmusic&si=xyz").id,
        )
        assertEquals(
            "PLwatch",
            success("youtube.com/watch?v=abc&list=PLwatch").id,
        )
    }

    @Test
    fun `youtube link without list is rejected`() {
        val result = PlaylistLinkParser.parse("https://youtube.com/watch?v=abc")

        assertTrue(result is PlaylistLinkParseResult.Failure)
        assertEquals(
            ImportError.UNSUPPORTED_LINK,
            (result as PlaylistLinkParseResult.Failure).error,
        )
    }

    @Test
    fun `soundcloud set becomes canonical url`() {
        val parsed = success("https://soundcloud.com/artist/sets/my-mix?si=123")

        assertEquals(ImportService.SOUNDCLOUD, parsed.service)
        assertEquals("https://soundcloud.com/artist/sets/my-mix", parsed.id)
    }

    @Test
    fun `soundcloud track link is not a playlist`() {
        val result = PlaylistLinkParser.parse("https://soundcloud.com/artist/track-name")

        assertTrue(result is PlaylistLinkParseResult.Failure)
    }
}
