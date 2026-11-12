package com.facet.music.core.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLinkParserTest {

    @Test
    fun `spotify user link returns user id`() {
        val parsed = ProfileLinkParser.parse("https://open.spotify.com/user/21xyz?si=1")

        assertEquals(ImportService.SPOTIFY, parsed?.service)
        assertEquals("21xyz", parsed?.id)
    }

    @Test
    fun `soundcloud profile becomes canonical url`() {
        val parsed = ProfileLinkParser.parse("soundcloud.com/onda-andar")

        assertEquals(ImportService.SOUNDCLOUD, parsed?.service)
        assertEquals("https://soundcloud.com/onda-andar", parsed?.id)
    }

    @Test
    fun `soundcloud profile tab still points to profile`() {
        val parsed = ProfileLinkParser.parse("https://soundcloud.com/onda-andar/likes")

        assertEquals(ImportService.SOUNDCLOUD, parsed?.service)
        assertEquals("https://soundcloud.com/onda-andar", parsed?.id)
    }

    @Test
    fun `soundcloud track and set links are not profiles`() {
        assertNull(ProfileLinkParser.parse("https://soundcloud.com/onda-andar/cachalot"))
        assertNull(ProfileLinkParser.parse("https://soundcloud.com/onda-andar/sets/mix"))
    }

    @Test
    fun `spotify playlist link is not a profile`() {
        assertNull(ProfileLinkParser.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
    }

    @Test
    fun `empty and foreign links are rejected`() {
        assertNull(ProfileLinkParser.parse("   "))
        assertNull(ProfileLinkParser.parse("https://deezer.com/profile/123"))
    }
}
