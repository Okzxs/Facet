package com.facet.music.data.source.wave

import com.facet.music.core.source.TrackMatcher
import com.facet.music.core.wave.WaveNetworkGate
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LastFmTrack
import com.facet.music.data.repository.YouTubeRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Превращает метаданные Last.fm в играбельный трек. Сначала ищет полный поток
 * на YouTube и проверяет совпадение через [TrackMatcher]; если не нашлось,
 * оставляет metadata-only трек — PlaybackResolver дотянет его при воспроизведении.
 */
@Singleton
class LastFmTrackResolver @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val trackMatcher: TrackMatcher,
    private val networkGate: WaveNetworkGate,
) {

    suspend fun resolve(track: LastFmTrack): Track {
        val metadata = metadata(track)
        val found = networkGate.withPermit {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                youTubeRepository.searchMusicSongs("${track.artist} ${track.name}", SEARCH_LIMIT)
                    .getOrNull()
                    .orEmpty()
            }
        }.orEmpty()
        return trackMatcher.best(metadata, found)?.track ?: metadata
    }

    fun metadata(track: LastFmTrack): Track = Track(
        id = "lastfm:${slug(track.artist)}:${slug(track.name)}",
        source = Source.SPOTIFY,
        title = track.name,
        artist = track.artist,
        artworkUrl = null,
        streamUrl = null,
        durationMs = track.durationMs,
        genre = null,
        transcodingUrl = null,
    )

    private fun slug(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(NON_WORD, "-")
        .trim('-')

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 4_000L
        const val SEARCH_LIMIT = 8
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    }
}
