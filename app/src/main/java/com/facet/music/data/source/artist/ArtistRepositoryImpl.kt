package com.facet.music.data.source.artist

import android.util.Log
import com.facet.music.core.network.NewPipeDownloader
import com.facet.music.core.search.ArtistMerger
import com.facet.music.core.search.TrackMerger
import com.facet.music.core.util.rethrowCancellation
import com.facet.music.data.local.dao.ArtistCacheDao
import com.facet.music.data.local.dao.FollowedArtistDao
import com.facet.music.data.local.entity.ArtistCacheEntity
import com.facet.music.data.local.entity.FollowedArtistEntity
import com.facet.music.data.model.Artist
import com.facet.music.data.model.ArtistPage
import com.facet.music.data.model.ArtistPlaylist
import com.facet.music.data.model.ArtistProfile
import com.facet.music.data.model.ArtistRelease
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.TrackPage
import com.facet.music.data.repository.ArtistRepository
import com.facet.music.data.repository.LastFmRepository
import com.facet.music.data.repository.SoundCloudRepository
import com.facet.music.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val newPipeDownloader: NewPipeDownloader,
    private val cacheDao: ArtistCacheDao,
    private val followedDao: FollowedArtistDao,
    private val json: Json,
    private val youTubeRepository: YouTubeRepository,
    private val soundCloudRepository: SoundCloudRepository,
    private val lastFmRepository: LastFmRepository,
) : ArtistRepository {

    private val tag = "ArtistRepo"

    private data class ChannelBundle(
        val source: Source,
        val service: StreamingService,
        val info: ChannelInfo,
        val tabsInfo: ChannelInfo,
        val profile: ArtistProfile,
    )

    private data class TabPageState(
        val service: StreamingService,
        val handler: ListLinkHandler,
        val page: Page,
    )

    private data class TabResult(
        val items: List<InfoItem>,
        val nextToken: String?,
    )

    private val channels = ConcurrentHashMap<String, ChannelBundle>()
    private val pageStates = ConcurrentHashMap<String, TabPageState>()
    private val pageSeq = AtomicLong()

    override suspend fun artist(source: Source, url: String): Result<ArtistPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bundle = loadChannel(source, url)
                val tracks = loadTracks(bundle)
                val popular = mergePopular(bundle, tracks.tracks)
                val page = ArtistPage(
                    profile = bundle.profile,
                    popular = popular.take(POPULAR_LIMIT),
                    allTracks = tracks.tracks,
                    nextPage = tracks.nextToken,
                )
                storePage(source, url, page)
                page
            }.rethrowCancellation().onFailure {
                Log.w(tag, "Страница артиста не загрузилась: ${it.javaClass.simpleName}")
            }
        }

    override suspend fun cached(source: Source, url: String): ArtistPage? =
        withContext(Dispatchers.IO) {
            val key = cacheKey(source, url)
            val entity = cacheDao.get(key) ?: return@withContext null
            val age = System.currentTimeMillis() - entity.fetchedAt
            if (age > CACHE_TTL_MS) {
                cacheDao.delete(key)
                return@withContext null
            }
            runCatching { json.decodeFromString<ArtistPage>(entity.json) }
                .getOrNull()
                ?.copy(nextPage = null)
        }

    override suspend fun store(page: ArtistPage) = withContext(Dispatchers.IO) {
        storePage(page.profile.source, page.profile.url, page)
    }

    private suspend fun storePage(source: Source, url: String, page: ArtistPage) {
        val now = System.currentTimeMillis()
        cacheDao.deleteOlderThan(now - CACHE_TTL_MS)
        cacheDao.upsert(
            ArtistCacheEntity(
                key = cacheKey(source, url),
                json = json.encodeToString(ArtistPage.serializer(), page),
                fetchedAt = now,
            ),
        )
    }

    override suspend fun popularTracks(
        source: Source,
        url: String,
        limit: Int,
    ): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = loadChannel(source, url)
            loadTracks(bundle).tracks
                .sortedByDescending { it.playCount }
                .take(limit)
        }.rethrowCancellation()
    }

    override suspend fun releases(source: Source, url: String): Result<List<ArtistRelease>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bundle = loadChannel(source, url)
                val items = loadTab(bundle, ChannelTabs.ALBUMS).items
                val fromTab = items
                    .filterIsInstance<PlaylistInfoItem>()
                    .map { it.toRelease() }
                    .distinctBy { it.url }
                if (fromTab.isNotEmpty()) {
                    fromTab
                } else if (source == Source.YOUTUBE) {
                    youTubeRepository
                        .searchMusicAlbums(bundle.profile.name, RELEASE_LIMIT)
                        .getOrNull()
                        .orEmpty()
                } else {
                    emptyList()
                }
            }.rethrowCancellation().onFailure {
                Log.w(tag, "Релизы артиста не загрузились: ${it.javaClass.simpleName}")
            }
        }

    override suspend fun allTracks(
        source: Source,
        url: String,
        nextToken: String?,
        limit: Int,
    ): Result<TrackPage> = withContext(Dispatchers.IO) {
        runCatching {
            if (nextToken == null) {
                val bundle = loadChannel(source, url)
                loadTracks(bundle, limit)
            } else if (nextToken.startsWith(FALLBACK_TOKEN_PREFIX)) {
                val bundle = loadChannel(source, url)
                youTubeRepository.searchPage(
                    bundle.profile.name,
                    limit,
                    nextToken,
                ).getOrElse { throw it }.onlyArtist(bundle.profile.name)
            } else if (source == Source.SOUNDCLOUD && nextToken.all(Char::isDigit)) {
                val bundle = loadChannel(source, url)
                soundCloudRepository.searchPage(
                    bundle.profile.name,
                    limit,
                    nextToken,
                ).getOrElse { throw it }.onlyArtist(bundle.profile.name)
            } else {
                val state = pageStates.remove(nextToken)
                    ?: error("Страница артиста устарела")
                val page = newPipeDownloader.withCancellation {
                    ChannelTabInfo.getMoreItems(state.service, state.handler, state.page)
                }
                val tracks = page.items
                    .filterIsInstance<StreamInfoItem>()
                    .take(limit)
                    .map { it.toTrack(source) }
                TrackPage(tracks, rememberPage(state.service, state.handler, page.nextPage))
            }
        }.rethrowCancellation()
    }

    override suspend fun releaseTracks(source: Source, url: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val service = serviceFor(source)
                val info = newPipeDownloader.withCancellation {
                    PlaylistInfo.getInfo(service, url)
                }
                info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toTrack(source) }
            }.rethrowCancellation()
        }

    override suspend fun playlists(source: Source, url: String): Result<List<ArtistPlaylist>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bundle = loadChannel(source, url)
                val fromTab = loadTab(bundle, ChannelTabs.PLAYLISTS).items
                    .filterIsInstance<PlaylistInfoItem>()
                    .map { it.toPlaylist() }
                    .distinctBy { it.url }
                if (fromTab.isNotEmpty()) {
                    fromTab
                } else if (source == Source.YOUTUBE) {
                    youTubeRepository
                        .searchMusicPlaylists(bundle.profile.name, RELEASE_LIMIT)
                        .getOrNull()
                        .orEmpty()
                } else {
                    emptyList()
                }
            }.rethrowCancellation().onFailure {
                Log.w(tag, "Плейлисты артиста не загрузились: ${it.javaClass.simpleName}")
            }
        }

    override suspend fun related(
        source: Source,
        url: String,
        name: String,
    ): Result<List<Artist>> = withContext(Dispatchers.IO) {
        runCatching {
            when (source) {
                Source.SOUNDCLOUD -> soundCloudRelated(url, name)
                else -> youTubeRelated(name)
            }
        }.rethrowCancellation()
    }

    override suspend fun resolveByName(name: String): Result<Artist?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (name.isBlank()) return@runCatching null
                val youtube = youTubeRepository.searchArtists(name, ARTIST_SEARCH_LIMIT)
                    .getOrNull()
                    .orEmpty()
                val soundCloud = soundCloudRepository.searchArtists(name, ARTIST_SEARCH_LIMIT)
                    .getOrNull()
                    .orEmpty()
                val merged = ArtistMerger.merge(listOf(youtube, soundCloud), name)
                ArtistMerger.bestMatch(name, merged) ?: merged.firstOrNull()
            }.rethrowCancellation()
        }

    override fun observeFollowed(): Flow<Set<String>> =
        followedDao.observeKeys().map { it.toSet() }

    override suspend fun setFollowed(profile: ArtistProfile, followed: Boolean) =
        withContext(Dispatchers.IO) {
            if (followed) {
                followedDao.insert(
                    FollowedArtistEntity(
                        artistKey = profile.key,
                        source = profile.source.name,
                        url = profile.url,
                        name = profile.name,
                        avatarUrl = profile.avatarUrl,
                    ),
                )
            } else {
                followedDao.delete(profile.key)
            }
        }

    private suspend fun loadChannel(source: Source, url: String): ChannelBundle {
        val cacheKey = channelCacheKey(source, url)
        channels[cacheKey]?.let { return it }

        val service = serviceFor(source)
        val info = newPipeDownloader.withCancellation {
            ChannelInfo.getInfo(service, url)
        }
        val isTopic = info.name.endsWith(TOPIC_SUFFIX, ignoreCase = true)
        val displayName = info.name.removeSuffix(TOPIC_SUFFIX).trim().ifBlank { info.name }

        val parentInfo = if (source == Source.YOUTUBE && isTopic) {
            info.parentChannelUrl?.takeIf { it.isNotBlank() }?.let { parentUrl ->
                runCatching {
                    newPipeDownloader.withCancellation {
                        ChannelInfo.getInfo(service, parentUrl)
                    }
                }.getOrNull()
            }
        } else {
            null
        }
        val tabsInfo = parentInfo ?: info
        val meta = parentInfo ?: info

        val profile = ArtistProfile(
            source = source,
            url = tabsInfo.url.ifBlank { url },
            name = displayName,
            avatarUrl = meta.avatars.bestImage() ?: info.avatars.bestImage(),
            bannerUrl = meta.banners.bestImage() ?: info.banners.bestImage(),
            subscriberCount = meta.subscriberCount.takeIf { it > 0 },
            description = meta.description.takeIf { it.isNotBlank() },
            isVerified = meta.isVerified,
        )
        val bundle = ChannelBundle(source, service, info, tabsInfo, profile)
        channels[cacheKey] = bundle
        channels[channelCacheKey(source, profile.url)] = bundle
        return bundle
    }

    private suspend fun loadTracks(
        bundle: ChannelBundle,
        limit: Int = TRACK_LIMIT,
    ): TrackPage {
        val tab = if (bundle.source == Source.SOUNDCLOUD) ChannelTabs.TRACKS else ChannelTabs.VIDEOS
        val result = loadTab(bundle, tab)
        var tracks = result.items
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map { it.toTrack(bundle.source) }
        if (tracks.isEmpty()) {
            return fallbackPage(bundle, limit)
        }
        return TrackPage(tracks, result.nextToken)
    }

    private suspend fun fallbackPage(bundle: ChannelBundle, limit: Int): TrackPage {
        val page = when (bundle.source) {
            Source.YOUTUBE -> youTubeRepository.searchPage(
                bundle.profile.name,
                limit,
                null,
            )
            else -> soundCloudRepository.searchPage(
                bundle.profile.name,
                limit,
                null,
            )
        }.getOrNull() ?: return TrackPage(emptyList(), null)

        return page.onlyArtist(bundle.profile.name)
    }

    private fun TrackPage.onlyArtist(name: String): TrackPage {
        val expected = ArtistMerger.normalize(name)
        val matching = tracks.filter {
            ArtistMerger.normalize(it.artist) == expected ||
                ArtistMerger.normalize(it.artist).contains(expected)
        }
        return copy(tracks = matching.ifEmpty { tracks })
    }

    private suspend fun mergePopular(
        bundle: ChannelBundle,
        primary: List<Track>,
    ): List<Track> {
        val otherSource = if (bundle.source == Source.YOUTUBE) Source.SOUNDCLOUD else Source.YOUTUBE
        val counterpart = resolveCounterpart(otherSource, bundle.profile.name) ?: return primary
        if (counterpart.url == bundle.profile.url) return primary

        val secondary = popularTracks(
            otherSource,
            counterpart.url,
            POPULAR_LIMIT,
        ).getOrNull().orEmpty()
        if (secondary.isEmpty()) return primary

        val primaryTotal = primary.sumOf { it.playCount }
        val secondaryTotal = secondary.sumOf { it.playCount }
        val groups = if (secondaryTotal > primaryTotal) {
            listOf(secondary, primary)
        } else {
            listOf(primary, secondary)
        }
        return TrackMerger.merge(groups, bundle.profile.name)
            .sortedByDescending { it.playCount }
            .take(POPULAR_LIMIT)
    }

    private suspend fun resolveCounterpart(source: Source, name: String): Artist? {
        val candidates = if (source == Source.YOUTUBE) {
            youTubeRepository.searchArtists(name, ARTIST_SEARCH_LIMIT).getOrNull().orEmpty()
        } else {
            soundCloudRepository.searchArtists(name, ARTIST_SEARCH_LIMIT).getOrNull().orEmpty()
        }
        return ArtistMerger.bestMatch(name, candidates)
    }

    private suspend fun soundCloudRelated(url: String, artistName: String): List<Artist> {
        val seeds = popularTracks(Source.SOUNDCLOUD, url, RELATED_SEEDS)
            .getOrNull()
            .orEmpty()
        val relatedTracks = seeds.flatMap { seed ->
            soundCloudRepository
                .getRelated(seed.transcodingUrl ?: seed.id, RELATED_TRACKS_PER_SEED)
                .getOrNull()
                .orEmpty()
        }
        val expected = ArtistMerger.normalize(artistName)
        val names = relatedTracks
            .mapNotNull { track ->
                val trackArtist = track.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artistUrl = track.artistUrl ?: return@mapNotNull null
                if (ArtistMerger.normalize(trackArtist) == expected) return@mapNotNull null
                trackArtist to artistUrl
            }
            .distinctBy { ArtistMerger.normalize(it.first) }
            .take(RELATED_LIMIT)

        return names.mapNotNull { (name, fallbackUrl) ->
            val found = soundCloudRepository.searchArtists(name, 1)
                .getOrNull()
                .orEmpty()
                .firstOrNull { ArtistMerger.normalize(it.name) == ArtistMerger.normalize(name) }
            found ?: Artist(
                id = fallbackUrl,
                source = Source.SOUNDCLOUD,
                name = name,
                avatarUrl = null,
                url = fallbackUrl,
            )
        }
    }

    private suspend fun youTubeRelated(artistName: String): List<Artist> {
        val names = lastFmRepository
            .getSimilarArtists(artistName, RELATED_LIMIT)
            .getOrNull()
            .orEmpty()
        return names.mapNotNull { name ->
            val youtube = youTubeRepository.searchArtists(name, 2).getOrNull().orEmpty()
            val soundCloud = if (youtube.none { ArtistMerger.normalize(it.name) == ArtistMerger.normalize(name) }) {
                soundCloudRepository.searchArtists(name, 2).getOrNull().orEmpty()
            } else {
                emptyList()
            }
            val merged = ArtistMerger.merge(listOf(youtube, soundCloud), name)
            ArtistMerger.bestMatch(name, merged)
        }
    }

    private suspend fun loadTab(bundle: ChannelBundle, tab: String): TabResult {
        val handler = tabHandler(bundle, tab) ?: return TabResult(emptyList(), null)
        val page = try {
            newPipeDownloader.withCancellation {
                ChannelTabInfo.getInfo(bundle.service, handler)
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(tag, "Вкладка $tab не открылась: ${error.javaClass.simpleName}")
            return TabResult(emptyList(), null)
        }
        return TabResult(
            items = page.relatedItems,
            nextToken = rememberPage(bundle.service, handler, page.nextPage),
        )
    }

    private fun tabHandler(bundle: ChannelBundle, tab: String): ListLinkHandler? {
        bundle.tabsInfo.tabs.firstOrNull { handler ->
            handler.contentFilters.any { it.equals(tab, ignoreCase = true) }
        }?.let { return it }

        val path = tabPath(bundle.source, tab)
        if (path != null) {
            val base = bundle.profile.url.substringBefore('?').trimEnd('/')
            val directUrl = "$base/$path"
            val parsed = runCatching {
                bundle.service.channelTabLHFactory.fromUrl(directUrl)
            }.getOrNull()
            return ListLinkHandler(
                parsed?.url ?: directUrl,
                bundle.profile.url.ifBlank { bundle.info.id.ifBlank { parsed?.id ?: directUrl } },
                parsed?.originalUrl ?: directUrl,
                listOf(tab),
                parsed?.sortFilter.orEmpty(),
            )
        }

        return runCatching {
            val url = bundle.service.channelTabLHFactory
                .getUrl(bundle.profile.url, listOf(tab), "")
            bundle.service.channelTabLHFactory.fromUrl(url)
        }.getOrNull()
    }

    private fun tabPath(source: Source, tab: String): String? = when {
        source == Source.YOUTUBE && tab == ChannelTabs.VIDEOS -> "videos"
        source == Source.YOUTUBE && tab == ChannelTabs.ALBUMS -> "releases"
        source == Source.YOUTUBE && tab == ChannelTabs.PLAYLISTS -> "playlists"
        source == Source.SOUNDCLOUD && tab == ChannelTabs.TRACKS -> "tracks"
        source == Source.SOUNDCLOUD && tab == ChannelTabs.ALBUMS -> "albums"
        source == Source.SOUNDCLOUD && tab == ChannelTabs.PLAYLISTS -> "playlists"
        else -> null
    }

    private fun rememberPage(
        service: StreamingService,
        handler: ListLinkHandler,
        page: Page?,
    ): String? {
        if (page == null) return null
        val token = "artist-${pageSeq.incrementAndGet()}"
        pageStates[token] = TabPageState(service, handler, page)
        return token
    }

    private fun serviceFor(source: Source): StreamingService = when (source) {
        Source.YOUTUBE -> NewPipe.getService(ServiceList.YouTube.serviceId)
        Source.SOUNDCLOUD -> NewPipe.getService(ServiceList.SoundCloud.serviceId)
        else -> error("Экран артиста доступен только для YouTube и SoundCloud")
    }

    private fun cacheKey(source: Source, url: String): String = channelCacheKey(source, url)

    private fun channelCacheKey(source: Source, url: String): String = "${source.name}:$url"

    private fun List<org.schabi.newpipe.extractor.Image>.bestImage(): String? =
        maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() }

    private fun StreamInfoItem.toTrack(source: Source): Track {
        val artist = uploaderName
            ?.removeSuffix(TOPIC_SUFFIX)
            ?.trim()
            .orEmpty()
            .ifBlank { UNKNOWN_ARTIST }
        return Track(
            id = url,
            source = source,
            title = name,
            artist = artist,
            artistUrl = uploaderUrl?.takeIf { it.isNotBlank() },
            artworkUrl = thumbnails.bestImage(),
            streamUrl = null,
            durationMs = duration.coerceAtLeast(0L) * 1000L,
            genre = null,
            transcodingUrl = url,
            playCount = viewCount.coerceAtLeast(0L),
        )
    }

    private fun PlaylistInfoItem.toRelease(): ArtistRelease {
        val cover = thumbnails.bestImage()
        return ArtistRelease(
            id = url,
            title = name,
            coverUrl = cover,
            year = null,
            url = url,
            trackCount = streamCount.coerceAtLeast(0L).toInt(),
        )
    }

    private fun PlaylistInfoItem.toPlaylist(): ArtistPlaylist = ArtistPlaylist(
        id = url,
        title = name,
        coverUrl = thumbnails.bestImage(),
        url = url,
        trackCount = streamCount.coerceAtLeast(0L).toInt(),
    )

    private companion object {
        const val TAG = "ArtistRepo"
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val TOPIC_SUFFIX = " - Topic"
        const val TRACK_LIMIT = 50
        const val POPULAR_LIMIT = 10
        const val FALLBACK_TOKEN_PREFIX = "yt-"
        const val RELEASE_LIMIT = 20
        const val ARTIST_SEARCH_LIMIT = 5
        const val RELATED_LIMIT = 8
        const val RELATED_SEEDS = 2
        const val RELATED_TRACKS_PER_SEED = 6
        const val UNKNOWN_ARTIST = "Unknown"
    }
}
