package com.my.music

import android.app.Application
import com.my.music.core.network.NewPipeDownloader
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

@HiltAndroidApp
class MyMusicApp : Application() {

    /**
     * NewPipe требует глобальную инициализацию перед первым использованием.
     * Делаем это в Application.onCreate() сразу после того, как Hilt
     * внедрит зависимости (inject происходит внутри super.onCreate()).
     */
    @Inject
    lateinit var newPipeDownloader: NewPipeDownloader

    override fun onCreate() {
        super.onCreate() // Hilt инжектит поля здесь
        NewPipe.init(newPipeDownloader)
    }
}
