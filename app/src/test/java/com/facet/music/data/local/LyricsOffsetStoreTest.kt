package com.facet.music.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LyricsOffsetStoreTest {

    private lateinit var context: Context
    private lateinit var store: LyricsOffsetStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = LyricsOffsetStore(context)
    }

    @Test
    fun `offset survives a new store instance`() {
        store.set("sc:1", 350)

        assertEquals(350, LyricsOffsetStore(context).get("sc:1"))
    }

    @Test
    fun `offset is clamped and zero clears the entry`() {
        store.set("sc:1", 9_999)
        assertEquals(2_000, store.get("sc:1"))

        store.set("sc:1", -9_999)
        assertEquals(-2_000, store.get("sc:1"))

        store.set("sc:1", 0)
        assertEquals(0, store.get("sc:1"))
    }

    @Test
    fun `blank track id is ignored`() {
        store.set("", 500)

        assertEquals(0, store.get(""))
    }

    @Test
    fun `offsets are isolated per track`() {
        store.set("sc:1", 100)
        store.set("yt:2", -200)

        assertEquals(100, store.get("sc:1"))
        assertEquals(-200, store.get("yt:2"))
    }
}
