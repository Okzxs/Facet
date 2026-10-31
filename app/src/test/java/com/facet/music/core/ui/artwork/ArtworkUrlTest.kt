package com.facet.music.core.ui.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrlTest {

    @Test
    fun `soundcloud large becomes 500`() {
        assertEquals(
            "https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            ArtworkUrl.highRes("https://i1.sndcdn.com/artworks-abc-large.jpg"),
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            ArtworkUrl.highRes("https://i1.sndcdn.com/artworks-abc-t500x500.jpg"),
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            ArtworkUrl.highRes("https://i1.sndcdn.com/artworks-abc-crop.jpg"),
        )
    }

    @Test
    fun `youtube thumb becomes maxres`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            ArtworkUrl.highRes("https://i.ytimg.com/vi/abc/hqdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            ArtworkUrl.highRes("https://i.ytimg.com/vi/abc/maxresdefault.jpg"),
        )
    }

    @Test
    fun `deezer and apple get larger sizes`() {
        assertEquals(
            "https://e-cdns-images.dzcdn.net/images/cover/abc/1000x1000-000000-80-0-0.jpg",
            ArtworkUrl.highRes(
                "https://e-cdns-images.dzcdn.net/images/cover/abc/500x500-000000-80-0-0.jpg",
            ),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/abc/600x600bb.jpg",
            ArtworkUrl.highRes("https://is1-ssl.mzstatic.com/image/thumb/abc/100x100bb.jpg"),
        )
    }

    @Test
    fun `unknown urls and blanks are returned as is`() {
        assertEquals(
            "https://example.com/cover.jpg",
            ArtworkUrl.highRes("https://example.com/cover.jpg"),
        )
        assertNull(ArtworkUrl.highRes(null))
        assertNull(ArtworkUrl.highRes("   "))
    }
}
