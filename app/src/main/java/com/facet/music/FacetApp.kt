package com.facet.music

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.facet.music.BuildConfig
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.player.PlaybackCache
import com.facet.music.data.source.soundcloud.SoundCloudClientIdProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class FacetApp : Application() {

    @Inject
    lateinit var newPipeDownloader: NewPipeDownloader

    @Inject
    lateinit var playbackCache: PlaybackCache

    @Inject
    lateinit var soundCloudClientIdProvider: SoundCloudClientIdProvider

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var warmedClientId = false

    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
        NewPipe.init(newPipeDownloader)
        applicationScope.launch { runCatching { playbackCache.warmUp() } }
        cleanLegacyAudioCache()
        warmUpSoundCloudAfterFirstFrame()
    }

    private fun warmUpSoundCloudAfterFirstFrame() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (warmedClientId) return
                    warmedClientId = true
                    applicationScope.launch {
                        runCatching { soundCloudClientIdProvider.warmUp() }
                    }
                }
            },
        )
    }

    private fun enableStrictMode() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
    }

    private fun cleanLegacyAudioCache() {
        applicationScope.launch {
            runCatching { WorkManager.getInstance(this@FacetApp).cancelAllWorkByTag(LEGACY_TAG) }
            val legacy = File(cacheDir, LEGACY_TAG)
            if (legacy.exists()) runCatching { legacy.deleteRecursively() }
            val legacyMedia = File(cacheDir, LEGACY_MEDIA_CACHE)
            if (legacyMedia.exists()) runCatching { legacyMedia.deleteRecursively() }
        }
    }

    private companion object {
        const val LEGACY_TAG = "audio_cache"
        const val LEGACY_MEDIA_CACHE = "media3_cache"
    }
}
