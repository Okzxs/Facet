package com.facet.music.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.facet.music.core.source.PlaybackResolver
import com.facet.music.core.source.ResolvedStream
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.SettingsRepository
import com.facet.music.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerControllerQueueTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var player: Player
    private lateinit var resolver: PlaybackResolver
    private lateinit var queueRepository: QueueRepository
    private lateinit var controller: PlayerController
    private val listenerSlot = slot<Player.Listener>()
    private val mediaItemsSlot = slot<List<MediaItem>>()
    private val startIndexSlot = slot<Int>()
    private val positionSlot = slot<Long>()

    private var items: List<MediaItem> = emptyList()
    private var currentIndex = 0
    private var position = 0L

    private val tracks = listOf(track("one"), track("two"), track("three"))

    @Before
    fun setUp() = runTest {
        player = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        queueRepository = mockk(relaxed = true)
        every { player.addListener(capture(listenerSlot)) } just Runs
        every { player.setMediaItems(capture(mediaItemsSlot), capture(startIndexSlot), capture(positionSlot)) } answers {
            items = mediaItemsSlot.captured
            currentIndex = startIndexSlot.captured.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            position = positionSlot.captured
            listenerSlot.captured.onMediaItemTransition(
                items.getOrNull(currentIndex),
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
            )
        }
        every { player.addMediaItems(any(), any()) } answers {
            val index = firstArg<Int>()
            val added = secondArg<List<MediaItem>>()
            items = items.toMutableList().apply { addAll(index.coerceIn(0, size), added) }
        }
        every { player.removeMediaItem(any()) } answers {
            val index = firstArg<Int>()
            if (index in items.indices) items = items.filterIndexed { i, _ -> i != index }
        }
        every { player.replaceMediaItem(any(), any()) } answers {
            val index = firstArg<Int>()
            val item = secondArg<MediaItem>()
            if (index in items.indices) {
                items = items.toMutableList().apply { set(index, item) }
            }
        }
        every { player.mediaItemCount } answers { items.size }
        every { player.getMediaItemAt(any()) } answers { items[firstArg<Int>()] }
        every { player.currentMediaItemIndex } answers { currentIndex }
        every { player.currentMediaItem } answers { items.getOrNull(currentIndex) }
        every { player.playbackState } returns Player.STATE_READY
        every { player.currentPosition } answers { position }
        every { player.duration } returns 180_000L
        every { player.bufferedPosition } returns 0L
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false
        every { player.repeatMode } returns Player.REPEAT_MODE_OFF
        every { player.hasNextMediaItem() } answers { currentIndex < items.lastIndex }
        every { player.hasPreviousMediaItem() } answers { currentIndex > 0 }
        every { player.seekToNextMediaItem() } answers {
            currentIndex = (currentIndex + 1).coerceAtMost(items.lastIndex)
            listenerSlot.captured.onMediaItemTransition(
                items.getOrNull(currentIndex),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            )
        }
        every { player.seekToPreviousMediaItem() } answers {
            currentIndex = (currentIndex - 1).coerceAtLeast(0)
            listenerSlot.captured.onMediaItemTransition(
                items.getOrNull(currentIndex),
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            )
        }
        every { player.seekTo(any<Long>()) } answers { position = firstArg() }
        every { player.seekTo(any<Int>(), any<Long>()) } answers {
            currentIndex = firstArg<Int>().coerceIn(0, items.lastIndex.coerceAtLeast(0))
            position = secondArg()
            listenerSlot.captured.onMediaItemTransition(
                items.getOrNull(currentIndex),
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            )
        }
        every { player.prepare() } just Runs
        every { player.play() } just Runs
        every { player.pause() } just Runs
        every { player.stop() } just Runs
        every { player.clearMediaItems() } answers {
            items = emptyList()
            currentIndex = -1
        }
        every { player.setRepeatMode(any()) } just Runs
        every { player.setShuffleModeEnabled(any()) } just Runs
        coEvery { resolver.resolve(any<Track>(), any()) } returns Result.success(
            ResolvedStream(
                url = "https://example.com/stream.mp3",
                mimeType = MimeTypes.AUDIO_MPEG,
                isHls = false,
                source = Source.SOUNDCLOUD,
            ),
        )

        controller = PlayerController(
            connection = FakeConnection(player),
            playbackResolver = resolver,
            queueRepository = queueRepository,
            settingsRepository = mockk(relaxed = true),
        )
    }

    @Test
    fun `set queue delegates whole queue to player without stop`() = runTest {
        controller.setQueueAndPlay(tracks, 1)

        assertEquals(tracks.map { it.id }, mediaItemsSlot.captured.map { it.mediaId })
        assertEquals(1, startIndexSlot.captured)
        verify { player.prepare() }
        verify { player.play() }
        verify(exactly = 0) { player.stop() }
        verify(exactly = 0) { player.pause() }
    }

    @Test
    fun `append adds items to the player`() = runTest {
        controller.setQueueAndPlay(tracks, 0)
        val before = items.size

        controller.appendToQueue(listOf(track("four")))

        assertEquals(before + 1, items.size)
        assertEquals("four", items.last().mediaId)
        verify(exactly = 0) { player.stop() }
    }

    @Test
    fun `play next inserts after current without recreating queue`() = runTest {
        controller.setQueueAndPlay(tracks, 1)
        val before = items.size

        assertTrue(controller.playNext(track("next")))

        assertEquals(before + 1, items.size)
        assertEquals("next", items[2].mediaId)
        verify { player.addMediaItems(2, any()) }
    }

    @Test
    fun `remove track uses removeMediaItem`() = runTest {
        controller.setQueueAndPlay(tracks, 1)

        controller.removeTrackAt(2)

        assertEquals(listOf("one", "two"), items.map { it.mediaId })
        verify { player.removeMediaItem(2) }
    }

    @Test
    fun `repeat maps to exoplayer repeat mode`() = runTest {
        controller.toggleRepeat()
        verify { player.setRepeatMode(Player.REPEAT_MODE_ALL) }
        controller.toggleRepeat()
        verify { player.setRepeatMode(Player.REPEAT_MODE_ONE) }
    }

    @Test
    fun `shuffle rebuilds queue and preserves position`() = runTest {
        controller.setQueueAndPlay(tracks, 2)
        position = 42_000L

        controller.toggleShuffle()

        assertEquals(tracks[2].id, mediaItemsSlot.captured.first().mediaId)
        assertEquals(0, startIndexSlot.captured)
        assertEquals(42_000L, positionSlot.captured)
        verify { player.shuffleModeEnabled = false }
    }

    @Test
    fun `play next delegates to player queue`() = runTest {
        controller.setQueueAndPlay(tracks, 0)

        controller.playNext()

        verify { player.seekToNextMediaItem() }
    }

    @Test
    fun `previous restarts current track after three seconds`() = runTest {
        controller.setQueueAndPlay(tracks, 1)
        position = 5_000L

        controller.playPrevious()

        verify { player.seekTo(0L) }
        verify(exactly = 0) { player.seekToPreviousMediaItem() }
    }

    @Test
    fun `previous switches media item before three seconds`() = runTest {
        controller.setQueueAndPlay(tracks, 1)
        position = 1_000L

        controller.playPrevious()

        verify { player.seekToPreviousMediaItem() }
    }

    @Test
    fun `media item contains metadata and serialized track`() = runTest {
        val withArtwork = tracks.first().copy(artworkUrl = "https://example.com/cover.jpg")
        controller.setQueueAndPlay(listOf(withArtwork), 0)

        val item = mediaItemsSlot.captured.single()
        assertEquals(withArtwork.id, item.mediaId)
        assertEquals(PlaybackUri.forTrack(withArtwork.id), item.localConfiguration?.uri)
        assertEquals(withArtwork.id, item.localConfiguration?.customCacheKey)
        assertEquals(withArtwork.title, item.mediaMetadata.title?.toString())
        assertEquals(withArtwork.artist, item.mediaMetadata.artist?.toString())
        assertEquals(withArtwork.artworkUrl, item.mediaMetadata.artworkUri?.toString())
        assertEquals(withArtwork, TrackMediaItem.trackOf(item))
    }

    @Test
    fun `player error auto advances to next track`() = runTest {
        controller.setQueueAndPlay(tracks, 0)

        listenerSlot.captured.onPlayerError(
            androidx.media3.common.PlaybackException(
                "test",
                null,
                androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED,
            ),
        )

        verify { player.seekToNextMediaItem() }
        assertEquals("two", controller.state.value.currentTrack?.id)
    }

    @Test
    fun `three consecutive errors stop and retry uses saved position`() = runTest {
        controller.setQueueAndPlay(tracks, 0)
        position = 33_000L
        repeat(3) {
            listenerSlot.captured.onPlayerError(
                androidx.media3.common.PlaybackException(
                    "test",
                    null,
                    androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED,
                ),
            )
        }

        assertFalse(controller.state.value.error.isNullOrBlank())
        assertTrue(controller.state.value.isBuffering.not())

        position = 0L
        controller.retryAfterError()

        verify { player.seekTo(33_000L) }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `timeline callback updates current index and track`() = runTest {
        controller.setQueueAndPlay(tracks, 0)
        currentIndex = 2
        position = 0L

        listenerSlot.captured.onMediaItemTransition(
            items[2],
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )

        assertEquals(2, controller.state.value.currentIndex)
        assertEquals("three", controller.state.value.currentTrack?.id)
    }

    private fun track(id: String) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 180_000L,
        genre = "Genre",
    )

    private class FakeConnection(fakePlayer: Player) : PlayerConnection {
        private val flow = MutableStateFlow<Player?>(fakePlayer)
        override val player: StateFlow<Player?> = flow
        override suspend fun connect(): Player = player.value!!
        override fun release() = Unit
    }
}
