package com.facet.music.core.service

import com.facet.music.core.source.PlayableSourceRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServiceStatusManagerTest {

    private lateinit var probe: ServiceProbe
    private lateinit var manager: ServiceStatusManager

    @Before
    fun setUp() {
        probe = mockk()
        coEvery { probe.isReachable(any()) } returns true
        manager = ServiceStatusManager(PlayableSourceRegistry(emptySet()), probe)
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
    }

    @Test
    fun `недоступный сервис помечается только он`() = runTest {
        coEvery { probe.isReachable(ServiceType.SOUNDCLOUD) } returns false

        manager.runChecks()

        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
        assertTrue(manager.statuses.value.getValue(ServiceType.YOUTUBE).isAvailable)
        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isChecking)
    }

    @Test
    fun `исключение в проверке не роняет остальные`() = runTest {
        coEvery { probe.isReachable(ServiceType.YOUTUBE) } throws IllegalStateException("boom")

        manager.runChecks()

        assertTrue(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
        assertFalse(manager.statuses.value.getValue(ServiceType.YOUTUBE).isAvailable)
    }

    @Test
    fun `успешный поиск возвращает сервис в онлайн`() = runTest {
        coEvery { probe.isReachable(ServiceType.SOUNDCLOUD) } returns false
        manager.runChecks()
        assertFalse(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)

        manager.markAvailable(ServiceType.SOUNDCLOUD)

        assertTrue(manager.statuses.value.getValue(ServiceType.SOUNDCLOUD).isAvailable)
    }
}
