package com.facet.music.core.source

import android.util.Log
import androidx.media3.common.MimeTypes
import com.facet.music.core.di.ApplicationScope
import com.facet.music.data.local.dao.ResolvedMatchDao
import com.facet.music.data.local.entity.ResolvedMatchEntity
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.model.isMetadataOnly
import com.facet.music.data.repository.DownloadRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Единственное место получения ссылки на поток.
 *
 * Порядок: in-memory кэш → скачанный файл → собственный провайдер → внешний
 * матч SoundCloud/YouTube → сохранённый матч в Room. Параллельные запросы
 * одного трека делят один Deferred, поэтому повторный Play, предзагрузка
 * и ResolvingDataSource не запускают второй резолв.
 */
@Singleton
class PlaybackResolver @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val registry: PlayableSourceRegistry,
    private val matcher: TrackMatcher,
    private val matchDao: ResolvedMatchDao,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private data class CachedStream(
        val stream: ResolvedStream,
        val expiresAtMs: Long?,
    )

    private val cache = ConcurrentHashMap<String, CachedStream>()
    private val knownTracks = ConcurrentHashMap<String, Track>()
    private val inFlight = mutableMapOf<String, Deferred<Result<ResolvedStream>>>()
    private val flightMutex = Mutex()

    private val _activeTrackId = MutableStateFlow<String?>(null)
    val activeTrackId: StateFlow<String?> = _activeTrackId.asStateFlow()

    /** Запомнить трек для ResolvingDataSource: у URI есть только id. */
    fun remember(track: Track) {
        knownTracks[track.id] = track
    }

    fun setActiveTrack(track: Track) {
        remember(track)
        _activeTrackId.value = track.id
    }

    fun invalidate(trackId: String) {
        cache.remove(trackId)
    }

    fun isKnownTrack(trackId: String): Boolean = knownTracks.containsKey(trackId)

    suspend fun resolveById(trackId: String): Result<ResolvedStream> {
        val track = knownTracks[trackId]
            ?: return Result.failure(
                ResolveException(ResolveError.NoMatch, "Трек не зарегистрирован для резолва"),
            )
        return resolve(track)
    }

    suspend fun resolve(
        track: Track,
        requireProgressive: Boolean = false,
    ): Result<ResolvedStream> {
        remember(track)
        cachedStream(track.id, requireProgressive)?.let { return Result.success(it) }

        val key = flightKey(track.id, requireProgressive)
        val deferred = flightMutex.withLock {
            cachedStream(track.id, requireProgressive)?.let {
                return@withLock completed(it)
            }
            inFlight[key]?.takeIf { it.isActive } ?: scope.async {
                resolveFresh(track, requireProgressive).onSuccess { stream ->
                    cache[track.id] = CachedStream(stream, stream.expiresAtMs ?: defaultExpiry(stream))
                }
            }.also { inFlight[key] = it }
        }

        scope.launch {
            try {
                deferred.await()
            } finally {
                flightMutex.withLock {
                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
            }
        }

        return deferred.await()
    }

    private fun completed(stream: ResolvedStream): Deferred<Result<ResolvedStream>> =
        CompletableDeferred(Result.success(stream))

    private fun cachedStream(trackId: String, requireProgressive: Boolean): ResolvedStream? {
        val entry = cache[trackId] ?: return null
        val expiresAt = entry.expiresAtMs
        if (expiresAt != null && expiresAt <= System.currentTimeMillis() + EXPIRY_GRACE_MS) {
            cache.remove(trackId)
            return null
        }
        if (requireProgressive && entry.stream.isHls) return null
        return entry.stream
    }

    private suspend fun resolveFresh(
        track: Track,
        requireProgressive: Boolean,
    ): Result<ResolvedStream> {
        downloadedStream(track)?.let { return Result.success(it) }
        if (track.source == Source.LOCAL) {
            return Result.failure(
                ResolveException(ResolveError.NotAvailable, "Локальный трек недоступен"),
            )
        }

        val ownSource = registry.provider(track.source)
        if (ownSource != null && !track.source.isMetadataOnly) {
            val own = retrying(track) { ownSource.resolveStream(track) }
            own.getOrNull()?.let { stream ->
                if (!requireProgressive || !stream.isHls) return Result.success(stream)
            }
            val ownError = (own.exceptionOrNull() as? ResolveException)?.error
            if (ownError == ResolveError.GeoBlocked || ownError == ResolveError.SubscriptionOnly) {
                return own
            }
        }

        if (track.source == Source.LOCAL) {
            return Result.failure(ResolveException(ResolveError.NotAvailable))
        }
        return resolveExternal(track, requireProgressive)    }

    private suspend fun downloadedStream(track: Track): ResolvedStream? {
        val downloaded = runCatching {
            downloadRepository.getDownloadedTrack(track.id)
        }.getOrNull() ?: return null

        return ResolvedStream(
            url = downloaded.mediaUrl,
            mimeType = downloaded.mimeType,
            isHls = downloaded.mimeType == MimeTypes.APPLICATION_M3U8,
            expiresAtMs = null,
            source = track.source,
        )
    }

    private suspend fun resolveExternal(
        track: Track,
        requireProgressive: Boolean,
    ): Result<ResolvedStream> {
        storedMatch(track.id)?.let { saved ->
            val savedStream = resolveStored(track, saved, requireProgressive)
            if (savedStream != null) return Result.success(savedStream)
            runCatching { matchDao.delete(track.id) }
        }

        val matches = searchMatches(track)
        if (matches.isEmpty()) {
            return Result.failure(noMatch(track))
        }

        var lastError: ResolveException? = null
        for (match in matches) {
            val provider = registry.provider(match.track.source) ?: continue
            val result = retrying(track) { provider.resolveStream(match.track) }
            result.exceptionOrNull()?.let { lastError = it.asResolveException(track) }
            val stream = result.getOrNull() ?: continue
            if (requireProgressive && stream.isHls) continue

            saveMatch(track, match)
            return Result.success(stream)
        }

        return Result.failure(
            lastError ?: ResolveException(
                ResolveError.NoMatch,
                "Не нашли уверенного совпадения",
            ),
        )
    }

    private suspend fun resolveStored(
        original: Track,
        saved: ResolvedMatchEntity,
        requireProgressive: Boolean,
    ): ResolvedStream? {
        val source = runCatching { Source.valueOf(saved.matchedSource) }.getOrNull()
            ?: return null
        val provider = registry.provider(source) ?: return null
        val candidate = original.copy(
            id = saved.matchedUrl,
            source = source,
            streamUrl = null,
            transcodingUrl = saved.matchedUrl,
            externalId = null,
            isSubscriptionOnly = false,
        )
        val result = retrying(original) { provider.resolveStream(candidate) }
        val stream = result.getOrNull() ?: return null
        if (requireProgressive && stream.isHls) return null
        Log.d(TAG, "Использован сохранённый матч: ${saved.matchedSource}")
        return stream
    }

    private suspend fun searchMatches(track: Track): List<TrackMatch> {
        val query = listOf(track.artist.trim(), track.title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return emptyList()

        return coroutineScope {
            val youtube = async { matchesFrom(Source.YOUTUBE, track, query) }
            val soundcloud = async { matchesFrom(Source.SOUNDCLOUD, track, query) }
            (youtube.await() + soundcloud.await())
                .distinctBy { it.track.id }
                .sortedByDescending { it.score }
                .take(MAX_MATCHES)
        }
    }

    private suspend fun matchesFrom(
        source: Source,
        original: Track,
        query: String,
    ): List<TrackMatch> {
        val provider = registry.provider(source) ?: return emptyList()
        return try {
            withTimeout(MATCH_TIMEOUT_MS) {
                provider.search(
                    query = query,
                    limit = MATCH_CANDIDATES,
                    page = 1,
                    useCache = true,
                )
            }.getOrElse { return emptyList() }
                .mapNotNull { matcher.match(original, it) }
                .sortedByDescending { it.score }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Поиск матча ${source.name} не удался: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private suspend fun saveMatch(original: Track, match: TrackMatch) {
        val source = match.track.source
        if (source != Source.SOUNDCLOUD && source != Source.YOUTUBE) return
        val url = match.track.transcodingUrl
            ?: match.track.streamUrl
            ?: match.track.id
        runCatching {
            matchDao.upsert(
                ResolvedMatchEntity(
                    originalTrackId = original.id,
                    matchedSource = source.name,
                    matchedUrl = url,
                    confidence = match.confidence,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }.onFailure {
            Log.w(TAG, "Не удалось сохранить матч: ${it.javaClass.simpleName}")
        }
    }

    private suspend fun storedMatch(trackId: String): ResolvedMatchEntity? =
        runCatching { matchDao.get(trackId) }.getOrNull()

    private suspend fun retrying(
        track: Track,
        block: suspend () -> Result<ResolvedStream>,
    ): Result<ResolvedStream> {
        var attempt = 0
        while (true) {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) return result

            val exception = result.exceptionOrNull()
                ?.asResolveException(track)
                ?: ResolveException(ResolveError.Unknown)
            if (!exception.error.isRetryable() || attempt >= RETRY_DELAYS_MS.size) {
                return Result.failure(exception)
            }
            delay(RETRY_DELAYS_MS[attempt] + Random.nextLong(JITTER_MAX_MS))
            attempt++
        }
    }

    private fun Throwable.asResolveException(track: Track): ResolveException {
        if (this is ResolveException) return this
        return ResolveException(classify(track), cause = this)
    }

    private fun Throwable.classify(track: Track): ResolveError {
        val chain = generateSequence(this) { it.cause }.take(8).toList()
        val messages = chain.mapNotNull { it.message?.lowercase() }
        val joined = messages.joinToString(" ")

        val codes = chain.mapNotNull { error ->
            (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                ?.responseCode
        } + HTTP_CODE.findAll(joined).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()

        if (codes.any { it == 404 || it == 410 }) return ResolveError.NotAvailable
        if (codes.any { it in 500..599 }) return ResolveError.Network
        if (codes.any { it == 401 || it == 403 || it == 408 || it == 429 }) {
            return ResolveError.Network
        }

        if (GEO_MARKERS.any { joined.contains(it) }) return ResolveError.GeoBlocked
        if (SUBSCRIPTION_MARKERS.any { joined.contains(it) }) return ResolveError.SubscriptionOnly
        if (NOT_AVAILABLE_MARKERS.any { joined.contains(it) }) return ResolveError.NotAvailable
        if (chain.any {
                it is IOException ||
                    it is SocketTimeoutException ||
                    it is UnknownHostException
            }
        ) {
            return ResolveError.Network
        }
        if (track.source == Source.SOUNDCLOUD && joined.contains("soundcloud")) {
            return ResolveError.Network
        }
        return ResolveError.Unknown
    }

    private fun noMatch(track: Track): ResolveException = ResolveException(
        ResolveError.NoMatch,
        "Не нашли «${track.artist} — ${track.title}» в SoundCloud и YouTube",
    )

    private fun defaultExpiry(stream: ResolvedStream): Long {
        if (stream.url.startsWith("file://")) return Long.MAX_VALUE
        return System.currentTimeMillis() + DEFAULT_TTL_MS
    }

    private fun flightKey(trackId: String, requireProgressive: Boolean): String =
        if (requireProgressive) "$trackId#progressive" else trackId

    private fun ResolveError.isRetryable(): Boolean = when (this) {
        ResolveError.Network -> true
        ResolveError.NotAvailable,
        ResolveError.SubscriptionOnly,
        ResolveError.GeoBlocked,
        ResolveError.NoMatch,
        ResolveError.Unknown,
        -> false
    }

    private companion object {
        const val TAG = "PlaybackResolver"
        const val MATCH_CANDIDATES = 10
        const val MAX_MATCHES = 8
        const val MATCH_TIMEOUT_MS = 10_000L
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L
        const val EXPIRY_GRACE_MS = 30_000L
        const val JITTER_MAX_MS = 150L

        val RETRY_DELAYS_MS = longArrayOf(400L, 1_200L, 3_000L)

        val HTTP_CODE = Regex("""\b(4\d{2}|5\d{2})\b""")

        val GEO_MARKERS = listOf(
            "region", "geo", "country", "not available in your", "недоступен в вашем регионе",
        )
        val SUBSCRIPTION_MARKERS = listOf(
            "subscription", "premium", "paid", "подписк", "go+", "artist pro",
        )
        val NOT_AVAILABLE_MARKERS = listOf(
            "not found", "deleted", "private", "unavailable", "no stream",
            "could not get any stream", "недоступ", "не найдено",
        )
    }
}
