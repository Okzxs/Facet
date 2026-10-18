package com.my.music.data.source.soundcloud

import android.util.Log
import com.my.music.data.local.dao.ChartCacheDao
import com.my.music.data.local.entity.toChartEntity
import com.my.music.data.local.entity.toTrack
import com.my.music.data.model.Source
import com.my.music.data.model.Track
import com.my.music.data.repository.SoundCloudChartsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundCloudChartsRepositoryImpl @Inject constructor(
    private val chartCacheDao: ChartCacheDao,
) : SoundCloudChartsRepository {

    private val tag = "SCChartsRepo"

    companion object {
        const val CHART_NEW_AND_HOT = "NEW_AND_HOT"
        const val CHART_TOP_50 = "TOP_50"

        private const val URL_NEW_AND_HOT = "https://soundcloud.com/charts/new"
        private const val URL_TOP_50 = "https://soundcloud.com/charts/top"

        // TTL обновления 30 минут
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    override fun observeNewAndHot(): Flow<List<Track>> =
        chartCacheDao.getTracksByChart(CHART_NEW_AND_HOT).map { entities ->
            entities.map { it.toTrack() }
        }

    override fun observeTop50(): Flow<List<Track>> =
        chartCacheDao.getTracksByChart(CHART_TOP_50).map { entities ->
            entities.map { it.toTrack() }
        }

    override suspend fun getNewAndHot(forceRefresh: Boolean): Result<List<Track>> =
        fetchChartWithCache(
            chartType = CHART_NEW_AND_HOT,
            url = URL_NEW_AND_HOT,
            forceRefresh = forceRefresh,
        )

    override suspend fun getTop50(forceRefresh: Boolean): Result<List<Track>> =
        fetchChartWithCache(
            chartType = CHART_TOP_50,
            url = URL_TOP_50,
            forceRefresh = forceRefresh,
        )

    private suspend fun fetchChartWithCache(
        chartType: String,
        url: String,
        forceRefresh: Boolean,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCached = chartCacheDao.getLastCachedTime(chartType)
        val isCacheValid = lastCached != null && (now - lastCached < CACHE_TTL_MS)

        // 1. Если кэш свежий и принудительное обновление не запрошено — отдаём из БД
        if (isCacheValid && !forceRefresh) {
            val cachedTracks = chartCacheDao.getTracksByChartSync(chartType).map { it.toTrack() }
            if (cachedTracks.isNotEmpty()) {
                Log.d(tag, "[$chartType] Загружено из Room кэша: ${cachedTracks.size} треков (возраст ${(now - lastCached!!) / 1000} сек)")
                return@withContext Result.success(cachedTracks)
            }
        }

        // 2. Иначе загружаем из NewPipe
        runCatching {
            val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
            val kioskInfo = KioskInfo.getInfo(service, url)
            Log.d(tag, "[$chartType] Загружено из сети NewPipe: ${kioskInfo.relatedItems.size} элементов")

            val tracks = kioskInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toDomain() }

            if (tracks.isNotEmpty()) {
                // Сохраняем в кэш Room
                val entities = tracks.mapIndexed { index, track ->
                    track.toChartEntity(
                        chartType = chartType,
                        position = index,
                        cachedAt = now,
                    )
                }
                chartCacheDao.replaceChart(chartType, entities)
                Log.d(tag, "[$chartType] Кэш Room обновлен: ${entities.size} треков")
            }

            tracks
        }.recoverCatching { error ->
            Log.w(tag, "[$chartType] Ошибка сетевого запроса к NewPipe: ${error.message}")
            // Если сеть отвалилась — фолбэк на старый кэш из Room (офлайн режим)
            val staleCache = chartCacheDao.getTracksByChartSync(chartType).map { it.toTrack() }
            if (staleCache.isNotEmpty()) {
                Log.d(tag, "[$chartType] Фолбэк на устаревший кэш Room: ${staleCache.size} треков")
                staleCache
            } else {
                throw error
            }
        }
    }

    private fun StreamInfoItem.toDomain(): Track {
        val artworkUrl = thumbnails.maxByOrNull { it.height }?.url
        return Track(
            id = url,
            source = Source.SOUNDCLOUD,
            title = name,
            artist = uploaderName ?: "Unknown",
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = duration * 1000L,
            genre = null,
            transcodingUrl = url,
        )
    }
}
