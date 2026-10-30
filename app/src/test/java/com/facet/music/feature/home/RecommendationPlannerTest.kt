package com.facet.music.feature.home

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
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
    fun `strict artist match rejects seed name in another artist title`() {
        val allowed = setOf("dark prince")

        assertTrue(
            planner.matchesExactArtist(
                track(artist = "Dark Prince", title = "Night Song"),
                allowed,
            ),
        )
        assertFalse(
            planner.matchesExactArtist(
                track(artist = "Other Artist", title = "Dark Prince - Night Song"),
                allowed,
            ),
        )
        assertFalse(
            planner.matchesExactArtist(
                track(artist = "Unknown", title = "Dark Prince - Night Song"),
                allowed,
            ),
        )
        assertFalse(
            planner.matchesExactArtist(
                track(artist = "", title = "Dark Prince - Another Song"),
                allowed,
            ),
        )
    }

    @Test
    fun `limits tracks per artist and total size`() {
        val candidates = (0 until 8).flatMap { artistIndex ->
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
            maxPerArtist = 2,
        )

        assertEquals(15, selected.size)
        val perArtist = selected.groupingBy { it.artist }.eachCount()
        assertTrue(perArtist.values.all { it <= 2 })
    }

    @Test
    fun `allows third track only for dominant artist`() {
        val candidates = (0 until 3).flatMap { artistIndex ->
            (0 until 5).map { trackIndex ->
                track(
                    id = "track-$artistIndex-$trackIndex",
                    artist = "Artist $artistIndex",
                )
            }
        }

        val selected = planner.selectTracks(
            candidates = candidates,
            limit = 15,
            maxPerArtist = 2,
            dominantArtists = setOf("artist 0"),
            maxPerDominantArtist = 3,
        )

        val perArtist = selected.groupingBy { it.artist }.eachCount()
        assertEquals(3, perArtist["Artist 0"])
        assertEquals(2, perArtist["Artist 1"])
        assertEquals(2, perArtist["Artist 2"])
    }

    @Test
    fun `randomizes artist seed subset between calls`() {
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
        assertTrue(selections.all { it.size in 4..6 })
    }

    @Test
    fun `builds strict artist fallback queries without popular search`() {
        val liked = (0 until 8).map { index ->
            track(
                id = "liked-$index",
                artist = "Artist $index",
                genre = "Rock",
            )
        }

        val queries = planner.buildArtistFallbackQueries(liked = liked, history = emptyList())
        val artistGroups = queries
            .mapNotNull { query -> query.allowedArtists.singleOrNull() }
            .toSet()

        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { query -> query.allowedArtists.size == 1 })
        assertTrue(queries.none { query -> query.text == POPULAR_QUERY })
        assertTrue(artistGroups.size in 4..6)
    }

    @Test
    fun `track seeds prioritize likes and skip duplicate and local tracks`() {
        val liked = listOf(
            track(id = "liked-1", artist = "Liked One"),
            track(id = "liked-2", artist = "Liked Two"),
        )
        val history = listOf(
            track(id = "liked-1", artist = "Liked One"),
            track(id = "history-1", artist = "History One"),
            track(id = "local-1", artist = "Local Artist", source = Source.LOCAL),
        )

        val seeds = planner.buildTrackSeeds(liked = liked, history = history)

        assertEquals(listOf("liked-1", "liked-2", "history-1"), seeds.map { it.track.id })
        assertTrue(seeds.first().weight > seeds.last().weight)
    }

    @Test
    fun `selects four to six track seeds with distinct artists when possible`() {
        val liked = (0 until 8).map { index ->
            track(id = "liked-$index", artist = "Artist $index")
        }
        val seeds = planner.buildTrackSeeds(liked = liked, history = emptyList())

        val selections = (1..20).map { planner.selectTrackSeeds(seeds) }

        assertTrue(selections.all { it.size in 4..6 })
        assertTrue(
            selections.all { selection ->
                selection.map { it.track.artist }.toSet().size == selection.size
            },
        )
    }

    @Test
    fun `detects artist dominating personal history`() {
        val history = listOf(
            track(id = "dominant-1", artist = "Dominant Artist"),
            track(id = "dominant-2", artist = "Dominant Artist"),
            track(id = "dominant-3", artist = "Dominant Artist"),
            track(id = "other-1", artist = "Other One"),
            track(id = "other-2", artist = "Other Two"),
            track(id = "other-3", artist = "Other Three"),
        )

        val dominant = planner.findDominantArtists(liked = emptyList(), history = history)

        assertTrue("dominant artist" in dominant)
        assertFalse("other one" in dominant)
    }

    private fun track(
        id: String = "track",
        title: String = "Title",
        artist: String = "Artist",
        genre: String? = "Pop",
        source: Source = Source.YOUTUBE,
        transcodingUrl: String? = "https://youtube.com/watch?v=$id",
    ) = Track(
        id = id,
        source = source,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = genre,
        transcodingUrl = transcodingUrl,
    )
}
