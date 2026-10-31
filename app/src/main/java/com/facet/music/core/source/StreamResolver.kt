package com.facet.music.core.source

import androidx.media3.common.MimeTypes
import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.AudioCacheRepository
import com.facet.music.data.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка получения ссылки на поток для «внешних» источников.
 *
 * Порядок:
 * 1. ссылка уже есть в треке (превью Deezer/iTunes, локальный файл) — играем её;
 * 2. провайдер умеет добыть ссылку сам — спрашиваем его (например, обновление
 *    истёкшего preview-URL);
 * 3. иначе ищем трек на YouTube через NewPipe и проверяем совпадение
 *    [YouTubeMatcher]-ом.
 *
 * Если ничего не сработало — честная ошибка, а не чужой трек.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val registry: MusicSourceRegistry,
    private val matcher: YouTubeMatcher,
    private val audioCacheRepository: AudioCacheRepository,
) {

    suspend fun resolve(track: Track): Result<AudioStreamResult> {
        // 0. Кэш прослушанного: локальный файл играется без сети.
        audioCacheRepository.fileFor(track.id)?.let { file ->
            return Result.success(
                AudioStreamResult(
                    url = file.toURI().toString(),
                    isHls = false,
                    mimeType = mimeFor(track.source),
                ),
            )
        }

        // 1. Прямая ссылка: превью или локальный файл.
        track.streamUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return Result.success(
                AudioStreamResult(
                    url = url,
                    isHls = url.contains(".m3u8", ignoreCase = true),
                    mimeType = mimeFor(track.source),
                ),
            )
        }

        // 2. Провайдер может обновить ссылку (например, просроченный preview).
        val provider = registry.provider(track.source)
        if (provider != null) {
            provider.resolve(track).onSuccess { return Result.success(it) }
        }

        // 3. Внешний матч на YouTube.
        return resolveViaYouTube(track)
    }

    /** Поиск на YouTube со скорингом: принимаем только уверенное совпадение. */
    suspend fun resolveViaYouTube(track: Track): Result<AudioStreamResult> {
        val query = listOf(track.artist, track.title)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) {
            return Result.failure(IllegalStateException("Нет данных для поиска на YouTube"))
        }

        val candidates = youTubeRepository
            .search(query = query, limit = MATCH_CANDIDATES)
            .getOrElse { return Result.failure(it) }

        val match = matcher.best(original = track, candidates = candidates)
            ?: return Result.failure(
                IllegalStateException("Не нашли «${track.artist} — ${track.title}» на YouTube"),
            )

        val videoUrl = match.transcodingUrl?.takeIf { it.isNotBlank() }
            ?: match.id.takeIf { it.startsWith("http") }
            ?: match.streamUrl?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("У найденного видео нет ссылки"))

        return youTubeRepository.resolveStreamUrl(videoUrl)
    }

    /** MIME по источнику: Apple отдаёт M4A, Deezer — MP3. */
    private fun mimeFor(source: Source): String? = when (source) {
        Source.APPLE -> MimeTypes.AUDIO_MP4
        Source.DEEZER, Source.LOCAL, Source.SOUNDCLOUD, Source.YOUTUBE,
        Source.SPOTIFY, Source.YANDEX, Source.VK,
        -> MimeTypes.AUDIO_MPEG
    }

    private companion object {
        const val MATCH_CANDIDATES = 10
    }
}
