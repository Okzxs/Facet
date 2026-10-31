package com.facet.music.core.import

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * api-v2 `/track_likes`: collection треков и next_href для пагинации.
 */
class SoundCloudLikesParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses collection and next href`() {
        val body = """
            {
              "collection": [
                {
                  "permalink_url": "https://soundcloud.com/user/track-one",
                  "title": "Track One",
                  "duration": 180000,
                  "artwork_url": "https://i1.sndcdn.com/artworks-1-large.jpg",
                  "user": { "username": "Artist" }
                },
                {
                  "permalink_url": "https://soundcloud.com/user/track-two",
                  "title": "Track Two",
                  "duration": 200000,
                  "artwork_url": null,
                  "user": { "username": "Artist" }
                }
              ],
              "next_href": "https://api-v2.soundcloud.com/users/1/track_likes?offset=2"
            }
        """.trimIndent()

        val page = SoundCloudLikesParser.parse(body, json)

        assertEquals(2, page.tracks.size)
        assertEquals("Track One", page.tracks.first().title)
        assertEquals("Artist", page.tracks.first().artist)
        assertEquals("https://soundcloud.com/user/track-one", page.tracks.first().id)
        assertEquals(
            "https://api-v2.soundcloud.com/users/1/track_likes?offset=2",
            page.nextHref,
        )
    }

    @Test
    fun `last page has no next href`() {
        val body = """{"collection":[],"next_href":null}"""

        val page = SoundCloudLikesParser.parse(body, json)

        assertEquals(0, page.tracks.size)
        assertNull(page.nextHref)
    }

    @Test
    fun `unwraps like object with nested track`() {
        // Реальный ответ api-v2: элемент коллекции — лайк, а трек внутри `track`.
        val body = """
            {
              "collection": [
                {
                  "created_at": "2026-08-21T04:15:41Z",
                  "kind": "like",
                  "track": {
                    "permalink_url": "https://soundcloud.com/user/nested",
                    "title": "Nested Track",
                    "duration": 30000,
                    "full_duration": 240000,
                    "user": { "username": "Nested Artist" }
                  }
                }
              ],
              "next_href": null
            }
        """.trimIndent()

        val page = SoundCloudLikesParser.parse(body, json)

        assertEquals(1, page.tracks.size)
        assertEquals("Nested Track", page.tracks.single().title)
        assertEquals("Nested Artist", page.tracks.single().artist)
        assertEquals(240000L, page.tracks.single().durationMs)
    }

    @Test
    fun `broken items are skipped`() {
        val body = """
            {
              "collection": [
                { "title": "No permalink" },
                { "permalink_url": "https://soundcloud.com/user/ok", "title": "Ok" }
              ]
            }
        """.trimIndent()

        val page = SoundCloudLikesParser.parse(body, json)

        assertEquals(1, page.tracks.size)
        assertEquals("Ok", page.tracks.single().title)
        assertNull(page.nextHref)
    }
}
