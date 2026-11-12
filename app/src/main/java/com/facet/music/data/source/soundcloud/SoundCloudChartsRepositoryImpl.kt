package com.facet.music.data.source.soundcloud

import android.util.Log
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.data.local.dao.ChartCacheDao
import com.facet.music.data.local.entity.toChartEntity
import com.facet.music.data.local.entity.toTrack
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.SoundCloudChartsRepository
import kotlinx.coroutines.CancellationException
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
    private val newPipeDownloader: NewPipeDownloader,
) : SoundCloudChartsRepository {

    private val tag = "SCChartsRepo"

    companion object {
        const val CHART_NEW_AND_HOT = "NEW_AND_HOT"
        const val CHART_TOP_50 = "TOP_50"

        private const val URL_NEW_AND_HOT = "https://soundcloud.com/charts/new"
        private const val URL_TOP_50 = "https://soundcloud.com/charts/top"

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

        if (isCacheValid && !forceRefresh) {
            val cachedTracks = chartCacheDao.getTracksByChartSync(chartType).map { it.toTrack() }
            if (cachedTracks.isNotEmpty()) {
                Log.d(tag, "[$chartType] Загружено из Room кэша: ${cachedTracks.size} треков (возраст ${(now - lastCached!!) / 1000} сек)")
                return@withContext Result.success(cachedTracks)
            }
        }

        runCatching {
            val service = NewPipe.getService(ServiceList.SoundCloud.serviceId)
            val kioskInfo = newPipeDownloader.withCancellation {
                KioskInfo.getInfo(service, url)
            }
            Log.d(tag, "[$chartType] Загружено из сети NewPipe: ${kioskInfo.relatedItems.size} элементов")

            val tracks = filterJunk(
                kioskInfo.relatedItems.filterIsInstance<StreamInfoItem>(),
            ).map { it.toDomain() }

            if (tracks.isNotEmpty()) {
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
            if (error is CancellationException) throw error
            Log.w(tag, "[$chartType] Ошибка сетевого запроса к NewPipe: ${error.message}")
            val staleCache = chartCacheDao.getTracksByChartSync(chartType).map { it.toTrack() }
            if (staleCache.isNotEmpty()) {
                Log.d(tag, "[$chartType] Фолбэк на устаревший кэш Room: ${staleCache.size} треков")
                staleCache
            } else {
                throw error
            }
        }
    }

    private fun filterJunk(items: List<StreamInfoItem>): List<StreamInfoItem> {
        val hasViewData = items.any { it.viewCount > 0 }
        val filtered = items.filter { item ->
            val tooShort = item.duration in 1..29
            val noPlays = hasViewData && item.viewCount == 0L
            !tooShort && !noPlays
        }
        val removed = items.size - filtered.size
        if (removed > 0) {
            Log.d(tag, "Отфильтровано $removed мусорных треков (короткие / без прослушиваний)")
        }
        return filtered.ifEmpty { items }
    }

    private fun StreamInfoItem.toDomain(): Track {
        val artworkUrl = thumbnails.maxByOrNull { it.height }?.url
        return Track(
            id = url,
            source = Source.SOUNDCLOUD,
            title = name,
            artist = uploaderName ?: "Unknown",
            artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            artworkUrl = artworkUrl,
            streamUrl = null,
            durationMs = duration * 1000L,
            genre = null,
            transcodingUrl = url,
            playCount = viewCount.coerceAtLeast(0L),
        )
    }
}
