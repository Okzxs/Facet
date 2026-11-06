package com.facet.music.data.local

import com.facet.music.core.text.MusicNormalizer
import com.facet.music.core.wave.TasteProfileProvider
import com.facet.music.core.wave.WaveTasteProfile
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.TasteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTasteProfileProvider @Inject constructor(
    private val tasteRepository: TasteRepository,
    private val likedTracksRepository: LikedTracksRepository,
) : TasteProfileProvider {

    override suspend fun profile(): WaveTasteProfile = withContext(Dispatchers.IO) {
        val artists = tasteRepository.getTopArtists(TASTE_LIMIT)
        val genres = tasteRepository.getTopGenres(TASTE_LIMIT)
        val maxArtist = artists.maxOfOrNull { it.weight }?.coerceAtLeast(0.0001) ?: 1.0
        val maxGenre = genres.maxOfOrNull { it.weight }?.coerceAtLeast(0.0001) ?: 1.0
        val likedMarkers = likedTracksRepository.getLikedTracks().first()
            .flatMap { MusicNormalizer.markersIn(it.title) }
            .toSet()
        WaveTasteProfile(
            artists = artists.associate { it.value to (it.weight / maxArtist).coerceIn(0.0, 1.0) },
            genres = genres.associate { it.value to (it.weight / maxGenre).coerceIn(0.0, 1.0) },
            likedMarkers = likedMarkers,
        )
    }

    private companion object {
        const val TASTE_LIMIT = 200
    }
}
