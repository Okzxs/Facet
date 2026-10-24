package com.my.music.feature.home

import com.my.music.data.model.Source
import com.my.music.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecommendationPlannerTest {

    private val planner = RecommendationPlanner().apply {
        random = Random(7)
    }

    @Test
    fun `normalizes artist names and removes featured credits`() {
        assertEquals("the weeknd", normalizeArtist("  The   Weeknd feat. Daft Punk "))
        assertEquals("artist", normalizeArtist("Artist prod. Beatmaker"))
        assertEquals("artist", normalizeArtist("Artist (feat. Someone)"))
        assertEquals("prodigy", normalizeArtist("Prodigy"))
    }

    @Test
    fun `rejects seed artist name in title of another artist`() {
        val allowed = setOf("dark prince")

        assertTrue(
            planner.matchesSeedArtist(
                track(artist = "Dark Prince", title = "Night Song"),
                allowed,
            ),
        )
        assertFalse(
            planner.matchesSeedArtist(
                track(artist = "Other Artist", title = "Dark Prince - Night Song"),
                allowed,
            ),
        )
    }

    @Test
    fun `uses strict title prefix only when artist metadata is missing`() {
        val allowed = setOf("dark prince")

        assertTrue(
            planner.matchesSeedArtist(
                track(artist = "Unknown", title = "Dark Prince - Night Song"),
                allowed,
            ),
        )
        assertTrue(
            planner.matchesSeedArtist(
                track(artist = "", title = "Dark Prince - Another Song"),
                allowed,
            ),
        )
        assertFalse(
            planner.matchesSeedArtist(
                track(artist = "", title = "The Dark Prince - Night Song"),
                allowed,
            ),
        )
    }

    @Test
    fun `limits tracks per artist and total size`() {
        val candidates = (0 until 6).flatMap { artistIndex ->
            (0 until 4).map { trackIndex ->
                track(
                    id = "track-$artistIndex-$trackIndex",
                    artist = "Artist $artistIndex",
                )
            }
        }

        val selected = planner.selectTracks(
            candidates = candidates,
            limit = 15,
            maxPerArtist = 3,
        )

        assertEquals(15, selected.size)
        val perArtist = selected.groupingBy { it.artist }.eachCount()
        assertTrue(perArtist.values.all { it <= 3 })
    }

    @Test
    fun `randomizes seed subset between calls`() {
        val seeds = (0 until 8).map { index ->
            ArtistSeed(
                name = "Artist $index",
                normalizedName = "artist $index",
                weight = 10.0 - index,
                genre = null,
            )
        }

        val selections = (1..20).map {
            planner.selectSeeds(seeds)
                .map { seed -> seed.normalizedName }
                .toSet()
        }

        assertTrue(selections.toSet().size > 1)
        assertTrue(selections.all { it.size in 4..5 })
    }

    @Test
    fun `builds one or two genre queries and four or five artist groups`() {
        val liked = (0 until 8).map { index ->
            track(
                id = "liked-$index",
                artist = "Artist $index",
                genre = "Rock",
            )
        }

        val queries = planner.buildQueries(liked = liked, history = emptyList())
        val artistGroups = queries
            .filter { query -> query.allowedArtists?.size == 1 }
            .mapNotNull { query -> query.allowedArtists?.single() }
            .toSet()
        val genreQueries = queries.count { query -> query.allowedArtists?.size == 8 }

        assertTrue(artistGroups.size in 4..5)
        assertTrue(genreQueries in 1..2)
    }

    private fun track(
        id: String = "track",
        title: String = "Title",
        artist: String = "Artist",
        genre: String? = "Pop",
    ) = Track(
        id = id,
        source = Source.YOUTUBE,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = genre,
        transcodingUrl = "https://youtube.com/watch?v=$id",
    )
}
