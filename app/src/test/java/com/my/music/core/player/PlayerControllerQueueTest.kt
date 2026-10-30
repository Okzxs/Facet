package com.my.music.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.my.music.data.model.AudioStreamResult
import com.my.music.data.model.DownloadedTrack
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.DownloadRepository
import com.my.music.data.repository.PlayHistoryRepository
import com.my.music.data.repository.SoundCloudRepository
import com.my.music.data.repository.YouTubeRepository
import com.my.music.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

    private lateinit var mediaController: MediaController
    private lateinit var soundCloudRepository: SoundCloudRepository
    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var playHistoryRepository: PlayHistoryRepository
    private lateinit var controller: PlayerController

    private val listenerSlot = slot<Player.Listener>()
    private val mediaItemSlot = slot<MediaItem>()

    private val tracks = listOf(
        track("one"),
        track("two"),
        track("three"),
    )

    @Before
    fun setUp() {
        mediaController = mockk(relaxed = true)
        soundCloudRepository = mockk(relaxed = true)
        youTubeRepository = mockk(relaxed = true)
        playHistoryRepository = mockk(relaxed = true)

        every { mediaController.addListener(capture(listenerSlot)) } just Runs
        every { mediaController.setMediaItem(capture(mediaItemSlot)) } just Runs
        every { mediaController.playbackParameters } returns PlaybackParameters(1f)
        coEvery { playHistoryRepository.addToHistory(any()) } just Runs
        coEvery { soundCloudRepository.resolveStreamUrl(any()) } returns
            Result.failure<AudioStreamResult>(IllegalStateException("test"))

        controller = PlayerController(
            mediaController,
            soundCloudRepository,
            playHistoryRepository,
            youTubeRepository,
        )
    }

    @Test
    fun `repeat off does not wrap on manual next at the end`() {
        controller.setQueueAndPlay(tracks, tracks.lastIndex)

        controller.playNext()

        assertEquals(tracks.lastIndex, controller.state.value.currentIndex)
        assertEquals(RepeatMode.OFF, controller.state.value.repeatMode)
    }

    @Test
    fun `repeat all wraps manual next to the first track`() {
        controller.toggleRepeat()
        controller.setQueueAndPlay(tracks, tracks.lastIndex)

        controller.playNext()

        assertEquals(RepeatMode.ALL, controller.state.value.repeatMode)
        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `repeat one advances on manual next and does not wrap at the end`() {
        controller.toggleRepeat()
        controller.toggleRepeat()
        controller.setQueueAndPlay(tracks, 0)

        controller.playNext()
        assertEquals(1, controller.state.value.currentIndex)

        controller.setQueueAndPlay(tracks, tracks.lastIndex)
        controller.playNext()
        assertEquals(tracks.lastIndex, controller.state.value.currentIndex)
    }

    @Test
    fun `repeat mode cycles off all one`() {
        assertEquals(RepeatMode.OFF, controller.state.value.repeatMode)

        controller.toggleRepeat()
        assertEquals(RepeatMode.ALL, controller.state.value.repeatMode)

        controller.toggleRepeat()
        assertEquals(RepeatMode.ONE, controller.state.value.repeatMode)

        controller.toggleRepeat()
        assertEquals(RepeatMode.OFF, controller.state.value.repeatMode)
    }

    @Test
    fun `previous goes back one track`() {
        controller.setQueueAndPlay(tracks, 1)

        controller.playPrevious()

        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `previous at first track stays with repeat off`() {
        controller.setQueueAndPlay(tracks, 0)

        controller.playPrevious()

        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `previous at first track wraps with repeat all`() {
        controller.toggleRepeat()
        controller.setQueueAndPlay(tracks, 0)

        controller.playPrevious()

        assertEquals(tracks.lastIndex, controller.state.value.currentIndex)
    }

    @Test
    fun `previous restarts current track after three seconds`() {
        every { mediaController.currentPosition } returns 5_000L
        controller.setQueueAndPlay(tracks, 1)

        controller.playPrevious()

        assertEquals(1, controller.state.value.currentIndex)
        verify { mediaController.seekTo(0L) }
    }

    @Test
    fun `append adds only new tracks and keeps playback position`() {
        controller.setQueueAndPlay(tracks, 1)

        controller.appendToQueue(
            listOf(track("two"), track("four"), track("four"), track("")),
        )

        assertEquals(
            listOf("one", "two", "three", "four"),
            controller.state.value.queue.map { it.id },
        )
        assertEquals(1, controller.state.value.currentIndex)
        assertEquals("two", controller.state.value.currentTrack?.id)
    }

    @Test
    fun `appended tracks survive shuffle toggle`() {
        controller.setQueueAndPlay(tracks, 1)
        controller.appendToQueue(listOf(track("four")))

        controller.toggleShuffle()
        controller.toggleShuffle()

        assertEquals(
            listOf("one", "two", "three", "four"),
            controller.state.value.queue.map { it.id },
        )
    }

    @Test
    fun `shuffle keeps current track first and restores original order`() {
        controller.setQueueAndPlay(tracks, 2)

        controller.toggleShuffle()

        assertTrue(controller.state.value.isShuffle)
        assertEquals(tracks.size, controller.state.value.queue.size)
        assertEquals(tracks[2].id, controller.state.value.queue.first().id)
        assertEquals(tracks.map { it.id }.toSet(), controller.state.value.queue.map { it.id }.toSet())

        controller.toggleShuffle()

        assertFalse(controller.state.value.isShuffle)
        assertEquals(tracks.map { it.id }, controller.state.value.queue.map { it.id })
        assertEquals(2, controller.state.value.currentIndex)
    }

    @Test
    fun `shuffle enabled before queue start still restores original order`() {
        controller.toggleShuffle()

        controller.setQueueAndPlay(tracks, 2)

        assertEquals(tracks[2].id, controller.state.value.queue.first().id)

        controller.toggleShuffle()

        assertEquals(tracks.map { it.id }, controller.state.value.queue.map { it.id })
        assertEquals(2, controller.state.value.currentIndex)
    }

    @Test
    fun `track ended with repeat one replays current track`() {
        controller.toggleRepeat()
        controller.toggleRepeat()
        controller.setQueueAndPlay(tracks, 1)

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(1, controller.state.value.currentIndex)
        // seekTo(0) вызывается и при подготовке трека, поэтому проверяем хотя бы один вызов
        verify { mediaController.seekTo(0L) }
        verify(exactly = 1) { mediaController.play() }
    }

    @Test
    fun `track ended with repeat all wraps to first track`() {
        controller.toggleRepeat()
        controller.setQueueAndPlay(tracks, tracks.lastIndex)

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `track ended with repeat off stops at the end of queue`() {
        controller.setQueueAndPlay(tracks, tracks.lastIndex)

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(tracks.lastIndex, controller.state.value.currentIndex)
        assertFalse(controller.state.value.isPlaying)
        verify(exactly = 0) { mediaController.play() }
    }

    @Test
    fun `track ended with repeat off advances when next exists`() {
        controller.setQueueAndPlay(tracks, 0)

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_ENDED)

        assertEquals(1, controller.state.value.currentIndex)
    }

    @Test
    fun `media item contains metadata for notification`() {
        val track = tracks.first().copy(artworkUrl = "https://example.com/cover.jpg")
        coEvery { soundCloudRepository.resolveStreamUrl(any()) } returns
            Result.success(
                AudioStreamResult(
                    url = "https://example.com/stream.mp3",
                    isHls = false,
                    mimeType = MimeTypes.AUDIO_MPEG,
                ),
            )

        controller.setQueueAndPlay(listOf(track), 0)

        val mediaItem = mediaItemSlot.captured
        assertEquals(track.id, mediaItem.mediaId)
        assertEquals(track.title, mediaItem.mediaMetadata.title?.toString())
        assertEquals(track.artist, mediaItem.mediaMetadata.artist?.toString())
        assertEquals(track.artworkUrl, mediaItem.mediaMetadata.artworkUri?.toString())
        assertEquals(MimeTypes.AUDIO_MPEG, mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun `youtube track resolves via youtube repository and uses m4a mime`() {
        val videoUrl = "https://www.youtube.com/watch?v=test"
        val youTubeTrack = track("youtube").copy(
            source = Source.YOUTUBE,
            transcodingUrl = videoUrl,
        )
        coEvery { youTubeRepository.resolveStreamUrl(videoUrl) } returns
            Result.success(
                AudioStreamResult(
                    url = "https://example.com/audio.m4a",
                    isHls = false,
                    mimeType = MimeTypes.AUDIO_MP4,
                ),
            )

        controller.setQueueAndPlay(listOf(youTubeTrack), 0)

        coVerify(exactly = 1) { youTubeRepository.resolveStreamUrl(videoUrl) }
        coVerify(exactly = 0) { soundCloudRepository.resolveStreamUrl(any()) }
        assertEquals(MimeTypes.AUDIO_MP4, mediaItemSlot.captured.localConfiguration?.mimeType)
    }

    @Test
    fun `offline m4a keeps mime type from room`() {
        val youTubeTrack = track("offline").copy(
            source = Source.YOUTUBE,
            transcodingUrl = "https://www.youtube.com/watch?v=offline",
        )
        val downloadRepository = mockk<DownloadRepository>()
        coEvery { downloadRepository.getDownloadedTrack(youTubeTrack.id) } returns DownloadedTrack(
            trackId = youTubeTrack.id,
            title = youTubeTrack.title,
            artist = youTubeTrack.artist,
            artworkUrl = null,
            durationMs = youTubeTrack.durationMs,
            mediaUrl = "file:///data/user/0/com.my.music/files/downloads/offline.m4a",
            mimeType = MimeTypes.AUDIO_MP4,
            downloadedAt = 0L,
        )
        controller = PlayerController(
            mediaController,
            soundCloudRepository,
            playHistoryRepository,
            youTubeRepository,
            downloadRepository,
        )

        controller.setQueueAndPlay(listOf(youTubeTrack), 0)

        assertEquals(MimeTypes.AUDIO_MP4, mediaItemSlot.captured.localConfiguration?.mimeType)
        coVerify(exactly = 0) { youTubeRepository.resolveStreamUrl(any()) }
    }

    @Test
    fun `playNext puts requested track right after current`() {
        controller.setQueueAndPlay(tracks, 0)

        assertTrue(controller.playNext(track("next")))

        assertEquals(
            listOf("one", "next", "two", "three"),
            controller.state.value.queue.map { it.id },
        )
        assertEquals(0, controller.state.value.currentIndex)
        assertEquals("one", controller.state.value.currentTrack?.id)
    }

    @Test
    fun `playNext moves track from the end of the queue and shifts index`() {
        controller.setQueueAndPlay(tracks, 1)

        assertTrue(controller.playNext(track("one")))

        assertEquals(
            listOf("two", "one", "three"),
            controller.state.value.queue.map { it.id },
        )
        assertEquals(0, controller.state.value.currentIndex)
        assertEquals("two", controller.state.value.currentTrack?.id)
    }

    @Test
    fun `playNext for the current track changes nothing`() {
        controller.setQueueAndPlay(tracks, 0)

        assertFalse(controller.playNext(track("one")))

        assertEquals(tracks.map { it.id }, controller.state.value.queue.map { it.id })
        assertEquals(0, controller.state.value.currentIndex)
    }

    @Test
    fun `addToQueue appends only new tracks`() {
        controller.setQueueAndPlay(tracks, 0)

        assertFalse(controller.addToQueue(track("two")))
        assertTrue(controller.addToQueue(track("four")))

        assertEquals(
            listOf("one", "two", "three", "four"),
            controller.state.value.queue.map { it.id },
        )
        assertEquals(0, controller.state.value.currentIndex)
    }

    private fun track(id: String) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist",
        artworkUrl = null,
        streamUrl = null,
        durationMs = 60_000L,
        genre = null,
        transcodingUrl = "https://soundcloud.com/test/$id",
    )
}
