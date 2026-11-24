package com.facet.music.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsTimingStoreTest {

    private lateinit var context: Context
    private lateinit var store: LyricsTimingStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lyrics_timings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = LyricsTimingStore(context)
    }

    @Test
    fun `timings survive a new store instance`() {
        store.set("sc:1", listOf(1_000L, 2_500L, 4_000L))

        assertEquals(listOf(1_000L, 2_500L, 4_000L), LyricsTimingStore(context).get("sc:1"))
    }

    @Test
    fun `empty list removes the entry`() {
        store.set("sc:1", listOf(1_000L))
        store.set("sc:1", emptyList())

        assertTrue(store.get("sc:1").isEmpty())
    }

    @Test
    fun `malformed values are ignored`() {
        context.getSharedPreferences("lyrics_timings", Context.MODE_PRIVATE)
            .edit()
            .putString("sc:1", "100,oops,200")
            .commit()

        assertEquals(listOf(100L, 200L), store.get("sc:1"))
    }
}
