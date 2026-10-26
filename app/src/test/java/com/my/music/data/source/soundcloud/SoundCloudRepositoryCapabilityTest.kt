package com.my.music.data.source.soundcloud

import com.my.music.core.download.DownloadCapability
import com.my.music.core.network.NewPipeDownloader
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Регрессия: SoundCloud умеет отдавать progressive MP3, поэтому резолвер
 * должен пробовать его первым, а не сразу искать трек на YouTube.
 */
class SoundCloudRepositoryCapabilityTest {

    @Test
    fun `soundcloud advertises progressive downloads`() {
        val repository = SoundCloudRepositoryImpl(
            NewPipeDownloader(OkHttpClient()),
        )

        assertEquals(DownloadCapability.PROGRESSIVE, repository.downloadCapability)
    }
}
