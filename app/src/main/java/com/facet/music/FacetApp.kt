package com.facet.music

import android.app.Application
import androidx.work.WorkManager
import com.facet.music.core.di.ApplicationScope
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.service.ServiceStatusManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class FacetApp : Application() {

    /**
     * NewPipe требует глобальную инициализацию перед первым использованием.
     * Делаем это в Application.onCreate() сразу после того, как Hilt
     * внедрит зависимости (inject происходит внутри super.onCreate()).
     */
    @Inject
    lateinit var newPipeDownloader: NewPipeDownloader

    @Inject
    lateinit var serviceStatusManager: ServiceStatusManager

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate() // Hilt инжектит поля здесь
        NewPipe.init(newPipeDownloader)
        // Проверяем сервисы в фоне после инициализации NewPipe
        serviceStatusManager.checkAllServices()
        cleanLegacyAudioCache()
    }

    /**
     * Старый AudioCacheWorker удалён: снимаем оставшиеся задачи и стираем
     * каталог прошлой версии. Media3 SimpleCache живёт отдельно.
     */
    private fun cleanLegacyAudioCache() {
        applicationScope.launch {
            runCatching { WorkManager.getInstance(this@FacetApp).cancelAllWorkByTag(LEGACY_TAG) }
            val legacy = File(cacheDir, LEGACY_TAG)
            if (legacy.exists()) runCatching { legacy.deleteRecursively() }
        }
    }

    private companion object {
        const val LEGACY_TAG = "audio_cache"
    }
}
