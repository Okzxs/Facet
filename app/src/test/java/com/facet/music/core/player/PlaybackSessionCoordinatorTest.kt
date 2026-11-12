package com.facet.music.core.player

import com.facet.music.core.analytics.AnalyticsTracker
import com.facet.music.core.network.ConnectivityObserver
import com.facet.music.core.wave.WaveOutcome
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.PlayHistoryRepository
import com.facet.music.data.repository.QueueRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveBatch
import com.facet.music.data.repository.WaveRepository
import com.facet.music.data.repository.WaveSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackSessionCoordinatorTest {

    private val playerState = MutableStateFlow(PlayerState())
    private val activeSession = MutableStateFlow<WaveSession?>(null)
    private val online = MutableStateFlow(true)

    private lateinit var playerController: PlayerController
    private lateinit var queueRepository: QueueRepository
    private lateinit var historyRepository: PlayHistoryRepository
    private lateinit var tasteRepository: TasteRepository
    private lateinit var waveRepository: WaveRepository
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var connectivityObserver: ConnectivityObserver

    @Before
    fun setUp() {
        playerController = mockk(relaxed = true)
        every { playerController.state } returns playerState
        every { playerController.currentPositionMs() } returns 0L
        queueRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        tasteRepository = mockk(relaxed = true)
        waveRepository = mockk(relaxed = true)
        every { waveRepository.activeSession } returns activeSession
        analyticsTracker = mockk(relaxed = true)
        connectivityObserver = mockk(relaxed = true)
        every { connectivityObserver.isOnline } returns online
    }

    private fun TestScope.coordinator(clock: () -> Long): PlaybackSessionCoordinator =
        PlaybackSessionCoordinator(
            playerController = playerController,
            queueRepository = queueRepository,
            playHistoryRepository = historyRepository,
            tasteRepository = tasteRepository,
            waveRepository = waveRepository,
            analyticsTracker = analyticsTracker,
            connectivityObserver = connectivityObserver,
            scope = this,
        ).apply {
            elapsedMs = clock
        }

    @Test
    fun `wave refills automatically when queue tail reaches threshold without ViewModel`() = runTest {
        var now = 1_000L
        val session = WaveSession(sessionId = "w-1")
        activeSession.value = session
        val queue = (1..6).map { track("t-$it") }
        val added = (7..16).map { track("t-$it") }
        coEvery { waveRepository.nextBatch(10) } returns Result.success(WaveBatch(added, session))
        playerState.value = PlayerState(
            queue = queue,
            currentIndex = 0,
            currentTrack = queue[0],
            isPlaying = true,
            queueOrigin = QueueOrigin.WAVE,
        )

        val coord = coordinator { now }
        coord.lastTickAt = now
        now += 1_000L
        coord.tickOnce()
        advanceUntilIdle()

        coVerify(exactly = 1) { waveRepository.nextBatch(10) }
        verify(exactly = 1) { playerController.appendToQueue(added) }
    }

    @Test
    fun `history is written only after 30 seconds of playback`() = runTest {
        var now = 1_000L
        val t1 = track("t-1", durationMs = 240_000L)
        playerState.value = PlayerState(
            queue = listOf(t1),
            currentIndex = 0,
            currentTrack = t1,
            isPlaying = true,
            queueOrigin = QueueOrigin.MAIN,
        )
        val coord = coordinator { now }
        coord.lastTickAt = now

        coord.tickOnce()
        now += 29_000L
        coord.tickOnce()
        coVerify(exactly = 0) { historyRepository.addToHistory(any(), any()) }

        now += 1_000L
        coord.tickOnce()
        coVerify(exactly = 1) { historyRepository.addToHistory(t1, any()) }

        now += 10_000L
        coord.tickOnce()
        coVerify(exactly = 1) { historyRepository.addToHistory(t1, any()) }
    }

    @Test
    fun `impressions and outcomes are recorded only on actual track transitions in wave`() = runTest {
        var now = 1_000L
        val t1 = track("wave-1")
        val t2 = track("wave-2")
        playerState.value = PlayerState(
            queue = listOf(t1, t2),
            currentIndex = 0,
            currentTrack = t1,
            isPlaying = true,
            queueOrigin = QueueOrigin.WAVE,
        )
        val coord = coordinator { now }
        coord.lastTickAt = now

        coord.tickOnce()
        coVerify(exactly = 1) { waveRepository.recordShown(t1) }

        now += 10_000L
        coord.tickOnce()
        coVerify(exactly = 1) { waveRepository.recordShown(t1) }
        coVerify(exactly = 0) { waveRepository.recordOutcome(any(), any(), any()) }

        playerState.value = playerState.value.copy(currentIndex = 1, currentTrack = t2)
        now += 1_000L
        coord.tickOnce()

        coVerify(exactly = 1) { waveRepository.recordOutcome(t1, WaveOutcome.SKIPPED, 11_000L) }
        coVerify(exactly = 1) { waveRepository.recordShown(t2) }
    }

    private fun track(id: String, durationMs: Long = 180_000L) = Track(
        id = id,
        source = Source.SOUNDCLOUD,
        title = "Title $id",
        artist = "Artist $id",
        artworkUrl = null,
        streamUrl = null,
        durationMs = durationMs,
        genre = "rock",
    )
}
