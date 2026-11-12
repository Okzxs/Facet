package com.facet.music.core.search

import com.facet.music.data.model.Artist
import com.facet.music.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMergerTest {

    private fun artist(
        name: String,
        source: Source,
        avatar: String? = null,
        subscribers: Long? = null,
        verified: Boolean = false,
    ) = Artist(
        id = "$source-$name",
        source = source,
        name = name,
        avatarUrl = avatar,
        subscriberCount = subscribers,
        isVerified = verified,
        url = "https://example.com/$name",
    )

    @Test
    fun `нормализация убирает регистр, ё, диакритику и суффиксы`() {
        assertEquals("елка", ArtistMerger.normalize("Ёлка"))
        assertEquals("bjork", ArtistMerger.normalize("Björk"))
        assertEquals("artist", ArtistMerger.normalize("ARTIST - Topic"))
        assertEquals("artist", ArtistMerger.normalize("Artist Official"))
        assertEquals("artist", ArtistMerger.normalize("Artist VEVO"))
    }

    @Test
    fun `аватар берётся с YouTube, если его нет у SoundCloud`() {
        val merged = ArtistMerger.merge(
            listOf(
                listOf(artist("Artist", Source.SOUNDCLOUD, avatar = "sc", subscribers = 100)),
                listOf(artist("artist", Source.YOUTUBE, avatar = "yt", subscribers = 10)),
            ),
            query = "artist",
        )

        assertEquals(1, merged.size)
        assertEquals("yt", merged.first().avatarUrl)
        assertEquals(100L, merged.first().subscriberCount)
    }

    @Test
    fun `сортировка ставит точное имя и verified выше`() {
        val merged = ArtistMerger.merge(
            listOf(
                listOf(
                    artist("Artist Tribute", Source.YOUTUBE),
                    artist("Artist", Source.SOUNDCLOUD, verified = true),
                ),
            ),
            query = "Artist",
        )

        assertEquals("Artist", merged.first().name)
    }

    @Test
    fun `лучший артист находится по точному имени и префиксу`() {
        val artists = listOf(
            artist("Other", Source.YOUTUBE),
            artist("Odesza", Source.SOUNDCLOUD),
        )

        assertEquals("Odesza", ArtistMerger.bestMatch("odesza", artists)?.name)
        assertEquals("Odesza", ArtistMerger.bestMatch("ode", artists)?.name)
        assertNull(ArtistMerger.bestMatch("od", artists))
        assertEquals("Odesza", ArtistMerger.bestMatch("odeszaa", artists)?.name)
        assertNull(ArtistMerger.bestMatch("metallica", artists))
        assertTrue(ArtistMerger.jaroWinkler("odesza", "odeszaa") > 0.9)
    }

    @Test
    fun `слияние объединяет суффиксы Topic Official VEVO и сортирует по подписчикам`() {
        val merged = ArtistMerger.merge(
            listOf(
                listOf(
                    artist("Daft Punk - Topic", Source.YOUTUBE, avatar = "yt-avatar", subscribers = 500),
                    artist("Daft Punk Cover Band", Source.YOUTUBE, subscribers = 10_000, verified = true),
                ),
                listOf(
                    artist("Daft Punk Official", Source.SOUNDCLOUD, avatar = "sc-avatar", subscribers = 2_000, verified = true),
                ),
            ),
            query = "Daft Punk",
        )

        assertEquals(2, merged.size)
        val main = merged.first()
        assertEquals("yt-avatar", main.avatarUrl)
        assertEquals(2_000L, main.subscriberCount)
        assertTrue(main.isVerified)
    }
}
