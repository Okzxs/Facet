package com.facet.music.core.search

import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.data.repository.LikedTracksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подсказки под полем поиска: удалённые suggestion API YouTube/SoundCloud
 * плюс локальные совпадения из истории и лайков.
 */
@Singleton
class SearchSuggestions @Inject constructor(
    private val newPipeDownloader: NewPipeDownloader,
    private val searchHistoryStore: SearchHistoryStore,
    private val likedTracksRepository: LikedTracksRepository,
) {

    suspend fun suggest(query: String): List<String> = coroutineScope {
        val normalized = ArtistMerger.normalize(query)
        if (normalized.length < 2) return@coroutineScope emptyList()

        val remote = listOf(ServiceList.YouTube, ServiceList.SoundCloud)
            .map { service ->
                async {
                    withTimeoutOrNull(REMOTE_TIMEOUT_MS) {
                        runCatching { remoteSuggestions(service.serviceId, query) }
                            .getOrDefault(emptyList())
                    }.orEmpty()
                }
            }
            .awaitAll()
            .flatten()

        (localSuggestions(normalized) + remote)
            .filter { it.isNotBlank() && ArtistMerger.normalize(it).contains(normalized) }
            .distinctBy { ArtistMerger.normalize(it) }
            .take(MAX_SUGGESTIONS)
    }

    private suspend fun localSuggestions(normalized: String): List<String> {
        val history = searchHistoryStore.recent()
        val likedArtists = runCatching {
            likedTracksRepository.getLikedTracks().first().map { it.artist }
        }.getOrDefault(emptyList())
        return (history + likedArtists)
            .filter { ArtistMerger.normalize(it).contains(normalized) }
            .distinctBy { ArtistMerger.normalize(it) }
            .take(MAX_LOCAL)
    }

    private suspend fun remoteSuggestions(serviceId: Int, query: String): List<String> =
        withContext(Dispatchers.IO) {
            val service = NewPipe.getService(serviceId)
            newPipeDownloader.withCancellation {
                service.suggestionExtractor.suggestionList(query)
            }
        }

    private companion object {
        const val REMOTE_TIMEOUT_MS = 2_500L
        const val MAX_LOCAL = 4
        const val MAX_SUGGESTIONS = 8
    }
}
