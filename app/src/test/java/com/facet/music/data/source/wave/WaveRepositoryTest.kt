package com.facet.music.data.source.wave

import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LastFmRepository
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.SoundCloudChartsRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.TasteWeight
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveRepositoryTest {

    private val likedTracksRepository = mockk<LikedTracksRepository>()
    private val playHistoryRepository = mockk<PlayHistoryRepository>()
    private val soundCloudRepository = mockk<SoundCloudRepository>()
    private val chartsRepository = mockk<SoundCloudChartsRepository>()
    private val lastFmRepository = mockk<LastFmRepository>()
    private val tasteRepository = mockk<TasteRepository>()

    private lateinit var repository: WaveRepositoryImpl

    @Before
    fun setUp() {
        every { likedTracksRepository.getLikedTracks() } returns flowOf(emptyList())
        every { playHistoryRepository.getPlayHistory() } returns flowOf(emptyList())
        coEvery { soundCloudRepository.search(any(), any()) } returns Result.success(emptyList())
        coEvery { chartsRepository.getTop50() } returns Result.success(emptyList())
        coEvery { lastFmRepository.getSimilarArtists(any(), any()) } returns
            Result.success(emptyList())
        coEvery { tasteRepository.getTopArtists(any()) } returns emptyList()
        coEvery { tasteRepository.getTopGenres(any()) } returns emptyList()
        coEvery { tasteRepository.isRussianPriority() } returns false

        repository = WaveRepositoryImpl(
            likedTracksRepository = likedTracksRepository,
            playHistoryRepository = playHistoryRepository,
            soundCloudRepository = soundCloudRepository,
            chartsRepository = chartsRepository,
            lastFmRepository = lastFmRepository,
            tasteRepository = tasteRepository,
            dislikedTrackDao = mockk(relaxed = true),
        )
    }

    @Test
    fun `combines all sources without duplicates and updates session counters`() = runBlocking {
        val liked = (1..12).map { track("l$it", "Rock") }
        val history = (1..5).map { track("h$it", null) }

        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        every { playHistoryRepository.getPlayHistory() } returns flowOf(history)
        coEvery { tasteRepository.getTopArtists(any()) } returns
            listOf(TasteWeight("Artist", 10.0))
        coEvery { lastFmRepository.getSimilarArtists("Artist", 4) } returns
            Result.success(listOf("Similar A", "Similar B"))
        coEvery { soundCloudRepository.search("Similar A", 5) } returns
            Result.success((1..5).map { track("sa$it", null) })
        coEvery { soundCloudRepository.search("Similar B", 5) } returns
            Result.success((1..5).map { track("sb$it", null) })
        coEvery { soundCloudRepository.search("Rock", 6) } returns
            Result.success((1..5).map { track("g$it", "Rock") })

        val result = repository.generateWave().getOrThrow()

        assertEquals(20, result.tracks.size)
        assertEquals(20, result.tracks.distinctBy { it.id }.size)
        assertEquals(10, result.fromLikes)
        assertEquals(4, result.fromHistory)
        assertEquals(2, result.fromGenres)
        assertEquals(4, result.fromSimilar)
        assertEquals(result.tracks.size, result.fromLikes + result.fromHistory +
            result.fromGenres + result.fromSimilar)

        val session = repository.activeSession.value
        assertEquals(20, session?.shownTrackIds?.size)
        assertEquals(1, session?.batchNumber)
        assertFalse(session?.isTerminated ?: true)
    }

    @Test
    fun `next batch skips shown tracks and terminates when sources are exhausted`() = runBlocking {
        val liked = (1..5).map { track("l$it", null) }
        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        coEvery { chartsRepository.getTop50() } returns Result.success(
            liked + (1..5).map { track("c$it", null) },
        )

        repository.startSession().getOrThrow()

        val first = repository.nextBatch(size = 5).getOrThrow()
        assertEquals(liked.map { it.id }.sorted(), first.tracks.map { it.id }.sorted())
        assertEquals(5, first.session.fromLikes)

        val second = repository.nextBatch(size = 5).getOrThrow()
        assertEquals((1..5).map { "c$it" }.sorted(), second.tracks.map { it.id }.sorted())
        assertEquals(5, second.session.fromGenres)

        assertTrue(first.tracks.map { it.id }.intersect(second.tracks.map { it.id }.toSet()).isEmpty())

        val third = repository.nextBatch(size = 5).getOrThrow()
        assertTrue(third.tracks.isEmpty())
        assertTrue(third.session.isTerminated)
        assertEquals(10, third.session.shownTrackIds.size)
        assertEquals(3, third.session.batchNumber)
    }

    @Test
    fun `russian priority puts cyrillic tracks first`() = runBlocking {
        coEvery { tasteRepository.isRussianPriority() } returns true
        coEvery { chartsRepository.getTop50() } returns Result.success(
            listOf(
                track("e1", null, title = "English one"),
                track("e2", null, title = "English two"),
                track("r1", null, title = "Русский трек"),
                track("e3", null, title = "English three"),
                track("r2", null, title = "Ещё один трек"),
            ),
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(size = 5).getOrThrow()

        assertEquals(5, batch.tracks.size)
        val cyrillic = '\u0400'..'\u04FF'
        assertTrue(batch.tracks.take(2).all { it.title.any { char -> char in cyrillic } })
        assertTrue(batch.tracks.drop(2).none { it.title.any { char -> char in cyrillic } })
    }

    @Test
    fun `empty sources yield an empty terminated wave`() = runBlocking {
        repository.startSession().getOrThrow()

        val batch = repository.nextBatch(size = 5).getOrThrow()

        assertTrue(batch.tracks.isEmpty())
        assertTrue(batch.session.isTerminated)
        assertTrue(repository.activeSession.value?.isTerminated ?: false)
    }

    @Test
    fun `russian priority fills the wave with local tracks before foreign ones`() = runBlocking {
        coEvery { tasteRepository.isRussianPriority() } returns true
        val liked = (1..12).map {
            track("ru$it", null, title = "Русский трек $it", artist = "Артист $it")
        }
        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        coEvery { chartsRepository.getTop50() } returns Result.success(
            (1..10).map { track("en$it", null, title = "English $it", artist = "Foreign $it") },
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(size = 20).getOrThrow()

        val cyrillic = '\u0400'..'\u04FF'
        assertEquals(20, batch.tracks.size)
        assertEquals(12, batch.session.fromLikes)
        // Пока хватает своих треков, западные идут только после них
        assertTrue(batch.tracks.take(12).all { it.title.any { char -> char in cyrillic } })
        assertTrue(batch.tracks.drop(12).none { it.title.any { char -> char in cyrillic } })
    }

    @Test
    fun `genre findings skip tracks with a different genre`() = runBlocking {
        val liked = (1..20).map { track("l$it", "House") }
        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        coEvery { soundCloudRepository.search("House", 6) } returns Result.success(
            listOf(
                track("g-ok", "House"),
                track("g-wrong-1", "Rap"),
                track("g-wrong-2", null),
            ),
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(size = 20).getOrThrow()

        assertTrue(batch.tracks.any { it.id == "g-ok" })
        assertTrue(batch.tracks.none { it.id.startsWith("g-wrong") })
    }

    @Test
    fun `similar tracks are taken from the requested artist when available`() = runBlocking {
        val liked = (1..5).map { track("l$it", null) }
        every { likedTracksRepository.getLikedTracks() } returns flowOf(liked)
        coEvery { tasteRepository.getTopArtists(any()) } returns
            listOf(TasteWeight("Artist", 10.0))
        coEvery { lastFmRepository.getSimilarArtists("Artist", 4) } returns
            Result.success(listOf("Similar A"))
        coEvery { soundCloudRepository.search("Similar A", 5) } returns Result.success(
            listOf(
                track("wrong-1", null, artist = "Чужой"),
                track("wrong-2", null, artist = "Другой"),
                track("right-1", null, artist = "Similar A"),
                track("right-2", null, artist = "Similar A feat. X"),
            ),
        )

        repository.startSession().getOrThrow()
        val batch = repository.nextBatch(size = 20).getOrThrow()

        // SoundCloud отдаёт чужие треки по запросу — берём только самого артиста
        assertTrue(batch.tracks.any { it.id == "right-1" })
        assertTrue(batch.tracks.any { it.id == "right-2" })
        assertTrue(batch.tracks.none { it.id.startsWith("wrong") })
    }

    private fun track(
        id: String,
        genre: String?,
        title: String = "Title $id",
        artist: String = "Artist",
    ) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = title,
        artist = artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = genre,
        transcodingUrl = "https://soundcloud.com/test/$id",
    )
}
