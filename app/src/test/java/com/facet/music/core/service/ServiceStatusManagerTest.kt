package com.facet.music.core.service

import com.facet.music.data.model.Track
import com.facet.music.core.source.MusicSourceRegistry
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ServiceStatusManagerTest {

    private lateinit var soundCloudRepository: SoundCloudRepository
    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var manager: ServiceStatusManager

    @Before
    fun setUp() {
        soundCloudRepository = mockk()
        youTubeRepository = mockk()
        coEvery {
            soundCloudRepository.search(any(), any())
        } returns Result.success(emptyList())
        coEvery {
            youTubeRepository.search(any(), any())
        } returns Result.success(emptyList())
        manager = ServiceStatusManager(soundCloudRepository, youTubeRepository, MusicSourceRegistry(emptySet()))
    }

    @Test
    fun `начальное состояние показывает проверку`() {
        val statuses = manager.statuses.value
        assertEquals(ServiceType.entries.toSet(), statuses.keys)
        assertTrue(statuses.values.all { it.isChecking })
    }

    @Test
    fun `оба сервиса онлайн после успешной проверки`() = runTest {
        manager.runChecks()

        assertEquals(
            ServiceHealth(isAvailable = true, isChecking = false),
            manager.statuses.value[ServiceType.SOUNDCLOUD],
        )
        assertEquals(
            ServiceHealth(isAvailable = true, isChecking = false),
            manager.statuses.value[ServiceType.YOUTUBE],
        )
        coVerify(exactly = 1) { soundCloudRepository.search("test", 1) }
        coVerify(exactly = 1) { youTubeRepository.search("test", 1) }
    }

    @Test
    fun `Result failure помечает только упавший сервис`() = runTest {
        coEvery {
            soundCloudRepository.search(any(), any())
        } returns Result.failure<List<Track>>(IOException("SoundCloud недоступен"))

        manager.runChecks()

        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
        assertTrue(manager.statuses.value.getValue(ServiceType.YOUTUBE).isAvailable)
        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isChecking)
        assertFalse(manager.statuses.value.getValue(ServiceType.YOUTUBE).isChecking)
    }

    @Test
    fun `исключение в репозитории не роняет проверку`() = runTest {
        coEvery {
            youTubeRepository.search(any(), any())
        } throws IllegalStateException("boom")

        manager.runChecks()

        assertTrue(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
        assertFalse(manager.statuses.value.getValue(ServiceType.YOUTUBE).isAvailable)
    }

    @Test
    fun `успешный поиск возвращает сервис в онлайн`() = runTest {
        coEvery {
            soundCloudRepository.search(any(), any())
        } returns Result.failure<List<Track>>(IOException("offline"))
        manager.runChecks()
        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)

        manager.markAvailable(ServiceType.SOUNDCLOUD)

        assertTrue(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isChecking)
    }
}
