package com.my.music.core.download

import com.my.music.data.model.Source
import com.my.music.data.model.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат выбора источника: качать нужно [streamUrl], но в библиотеке
 * трек остаётся с исходными метаданными и исходным [Track.id].
 */
data class ResolvedDownload(
    val track: Track,
    val source: Source,
    val streamUrl: String,
    val mimeType: String?,
)

/** Ни один progressive-источник не смог отдать файл. */
class DownloadSourceUnavailableException(
    message: String = "Не удалось найти источник",
) : IllegalStateException(message)

/**
 * Единый резолвер источника скачивания.
 *
 * Порядок:
 * 1. текущий источник, если он PROGRESSIVE и у трека есть ссылка;
 * 2. остальные PROGRESSIVE-источники с точечным поиском;
 * 3. ошибка, если ни один вариант не подошёл.
 *
 * Related- и поисковые кандидаты проходят строгую проверку: артист должен
 * совпасть полностью, а название — с точностью до служебных пометок
 * (feat., remaster, official audio). Подстроки в названиях запрещены.
 */
@Singleton
class DownloadSourceResolver @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards DownloadSource>,
    private val recentlyFailedSources: RecentlyFailedSources,
) {

    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun resolve(track: Track): Result<ResolvedDownload> {
        if (track.source == Source.LOCAL) {
            return Result.failure(
                DownloadSourceUnavailableException("Локальный трек уже доступен офлайн"),
            )
        }

        // Один трек — один сетевой запрос: быстрые повторные тапы ждут
        // результат первого вызова, а не запускают параллельный поиск.
        val mutex = mutexes.computeIfAbsent(track.id) { Mutex() }
        return mutex.withLock { resolveLocked(track) }
    }

    private suspend fun resolveLocked(track: Track): Result<ResolvedDownload> {
        val progressiveSources = sources.filter {
            it.capability == DownloadCapability.PROGRESSIVE
        }
        if (progressiveSources.isEmpty()) {
            return Result.failure(
                DownloadSourceUnavailableException("Нет источников с progressive-аудио"),
            )
        }

        val currentSource = progressiveSources.firstOrNull { it.source == track.source }
        if (currentSource != null && track.hasDownloadUrl()) {
            val current = currentSource.resolve(track)
            current.getOrNull()?.let { stream ->
                if (stream.isProgressive()) {
                    return Result.success(
                        ResolvedDownload(
                            track = track,
                            source = track.source,
                            streamUrl = stream.url,
                            mimeType = stream.mimeType,
                        ),
                    )
                }
            }
            recentlyFailedSources.markFailed(track.id, track.source)
        }

        for (source in progressiveSources) {
            if (source.source == track.source) continue
            if (recentlyFailedSources.isFailed(track.id, source.source)) continue

            val resolved = trySource(track, source)
            if (resolved != null) {
                recentlyFailedSources.clear(track.id, source.source)
                return Result.success(resolved)
            }
        }

        return Result.failure(DownloadSourceUnavailableException())
    }

    /**
     * Ищет трек в одном источнике и сразу проверяет, что он отдаёт
     * цельный progressive-файл. Любая неудача запоминается на 30 минут.
     */
    private suspend fun trySource(
        track: Track,
        source: DownloadSource,
    ): ResolvedDownload? {
        val query = buildSearchQuery(track)
        val candidates = source.search(query, SEARCH_LIMIT).getOrElse {
            recentlyFailedSources.markFailed(track.id, source.source)
            return null
        }

        val match = candidates.firstOrNull { candidate ->
            candidate.source == source.source && matchesTrack(track, candidate)
        }
        if (match == null) {
            recentlyFailedSources.markFailed(track.id, source.source)
            return null
        }

        val stream = source.resolve(match).getOrElse {
            recentlyFailedSources.markFailed(track.id, source.source)
            return null
        }
        if (!stream.isProgressive()) {
            recentlyFailedSources.markFailed(track.id, source.source)
            return null
        }

        return ResolvedDownload(
            track = track,
            source = source.source,
            streamUrl = stream.url,
            mimeType = stream.mimeType,
        )
    }

    private fun buildSearchQuery(track: Track): String =
        listOf(track.artist.trim(), track.title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun Track.hasDownloadUrl(): Boolean =
        listOfNotNull(transcodingUrl, streamUrl).any { it.isHttpUrl() }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    companion object {
        /** Сколько кандидатов запрашивать у источника при точечном поиске. */
        private const val SEARCH_LIMIT = 10

        /**
         * Строгое совпадение трека. Артист сравнивается целиком, название —
         * после удаления служебных пометок. Никаких `contains` в названии.
         */
        internal fun matchesTrack(seed: Track, candidate: Track): Boolean {
            val artist = normalizeArtist(seed.artist)
            if (artist != normalizeArtist(candidate.artist)) return false

            // YouTube часто пишет название как «Артист — Трек». Префикс
            // снимаем только при точном совпадении артиста, поэтому
            // частичные совпадения названий по-прежнему запрещены.
            val seedTitle = stripArtistPrefix(normalizeTitle(seed.title), artist)
            val candidateTitle = stripArtistPrefix(normalizeTitle(candidate.title), artist)
            return seedTitle.isNotBlank() && seedTitle == candidateTitle
        }

        private fun stripArtistPrefix(title: String, artist: String): String =
            if (artist.isBlank()) title else title.removePrefix("$artist ").trim()

        internal fun normalizeArtist(value: String): String {
            val withoutTopic = value
                .replace(Regex("""\s*-\s*topic\s*$""", RegexOption.IGNORE_CASE), "")
            val normalized = normalizeText(withoutTopic)
            // YouTube-каналы часто называются «ArtistVEVO» или «Artist Vevo».
            return normalized.removeSuffix("vevo").trim()
        }

        internal fun normalizeTitle(value: String): String {
            var result = value
            // Пометки в скобках: (Official Audio), [Remastered 2011], (feat. X).
            result = BRACKETED_NOISE.replace(result, " ")
            // feat./ft. без скобок отрезают хвост названия.
            result = FEATURING_NOISE.replace(result, " ")
            // Хвостовые «Official Audio», «Remastered», «Lyrics» и т.п.
            result = TRAILING_NOISE.replace(result, " ")
            val normalized = normalizeText(result)
            // Не позволяем названию из одного служебного слова стать пустым.
            return normalized.ifBlank { normalizeText(value) }
        }

        private fun normalizeText(value: String): String {
            val withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(Regex("""\p{M}+"""), "")
            return withoutDiacritics
                .lowercase(Locale.ROOT)
                .replace("&", " and ")
                .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                .trim()
                .replace(Regex("""\s+"""), " ")
        }

        private val BRACKETED_NOISE = Regex(
            """[\(\[\{][^\)\]\}]*(?:feat\.?|ft\.?|remaster|official|audio|video|lyrics|visuali[sz]er|explicit|clean)[^\)\]\}]*[\)\]\}]""",
            RegexOption.IGNORE_CASE,
        )

        private val FEATURING_NOISE = Regex(
            """\b(?:feat\.?|ft\.?)\s+.*$""",
            RegexOption.IGNORE_CASE,
        )

        private val TRAILING_NOISE = Regex(
            """\s*(?:[-–—]\s*)?(?:official\s+(?:audio|video|music\s+video)|audio|lyrics|remaster(?:ed)?(?:\s+\d{4})?)\s*$""",
            RegexOption.IGNORE_CASE,
        )
    }
}

private fun com.my.music.data.model.AudioStreamResult.isProgressive(): Boolean =
    !isHls && url.startsWith("http", ignoreCase = true)
