package com.facet.music.data.local

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.facet.music.core.service.ServiceType
import kotlinx.coroutines.flow.first
import org.junit.Before
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clearSettings() {
        context.getSharedPreferences("facet_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `recents are empty by default`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        assertEquals(emptyList<Int>(), repository.settings.first().recentAccentColors)
    }

    @Test
    fun `new color goes to the front of the list`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        repository.addRecentAccentColor(1)
        repository.addRecentAccentColor(2)
        assertEquals(listOf(2, 1), repository.settings.first().recentAccentColors)
    }

    @Test
    fun `duplicate moves to front without a second copy`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        repository.addRecentAccentColor(1)
        repository.addRecentAccentColor(2)
        repository.addRecentAccentColor(3)
        repository.addRecentAccentColor(1)
        assertEquals(listOf(1, 3, 2), repository.settings.first().recentAccentColors)
    }

    @Test
    fun `list is trimmed to five colors`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        (1..7).forEach { repository.addRecentAccentColor(it) }
        assertEquals(listOf(7, 6, 5, 4, 3), repository.settings.first().recentAccentColors)
    }

    @Test
    fun `recents survive repository recreation`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        repository.addRecentAccentColor(0xFF112233.toInt())
        repository.addRecentAccentColor(-1)
        val reloaded = SettingsRepositoryImpl(context)
        assertEquals(
            listOf(-1, 0xFF112233.toInt()),
            reloaded.settings.first().recentAccentColors,
        )
    }

    @Test
    fun `onboarding is not completed by default`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        assertEquals(false, repository.settings.first().onboardingCompleted)
    }

    @Test
    fun `onboarding flag survives repository recreation`() = runTest {
        SettingsRepositoryImpl(context).setOnboardingCompleted(true)
        val reloaded = SettingsRepositoryImpl(context)
        assertEquals(true, reloaded.settings.first().onboardingCompleted)
    }

    @Test
    fun `search sources default to all`() = runTest {
        val repository = SettingsRepositoryImpl(context)
        assertEquals(ServiceType.entries.toSet(), repository.settings.first().searchSources)
    }

    @Test
    fun `search sources persist selected subset`() = runTest {
        SettingsRepositoryImpl(context).setSearchSources(
            setOf(ServiceType.SOUNDCLOUD, ServiceType.YOUTUBE),
        )
        val reloaded = SettingsRepositoryImpl(context)
        assertEquals(
            setOf(ServiceType.SOUNDCLOUD, ServiceType.YOUTUBE),
            reloaded.settings.first().searchSources,
        )
    }

    @Test
    fun `migrates legacy single search source when search_sources is absent`() = runTest {
        context.getSharedPreferences("facet_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("search_service", ServiceType.SOUNDCLOUD.name)
            .putBoolean("search_all_sources", false)
            .commit()

        val repository = SettingsRepositoryImpl(context)

        assertEquals(setOf(ServiceType.SOUNDCLOUD), repository.settings.first().searchSources)
    }

    @Test
    fun `migrates legacy all sources flag when enabled`() = runTest {
        context.getSharedPreferences("facet_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("search_service", ServiceType.SOUNDCLOUD.name)
            .putBoolean("search_all_sources", true)
            .commit()

        val repository = SettingsRepositoryImpl(context)

        assertEquals(ServiceType.entries.toSet(), repository.settings.first().searchSources)
    }

    @Test
    fun `empty or unknown search sources fall back to all`() = runTest {
        context.getSharedPreferences("facet_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("search_sources", "UNKNOWN,INVALID")
            .commit()

        val repository = SettingsRepositoryImpl(context)
        assertEquals(ServiceType.entries.toSet(), repository.settings.first().searchSources)

        repository.setSearchSources(emptySet())
        assertEquals(ServiceType.entries.toSet(), repository.settings.first().searchSources)
    }
}
