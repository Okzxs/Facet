package com.facet.music.core.player

import com.facet.music.core.location.LocationProvider
import com.facet.music.core.util.SeasonProvider
import com.facet.music.data.local.dao.DislikedTrackDao
import com.facet.music.data.local.entity.DislikedTrackEntity
import com.facet.music.data.model.Track
import com.facet.music.data.repository.LikedTracksRepository
import com.facet.music.data.repository.TasteRepository
import com.facet.music.data.repository.WaveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Лайк из UI и из кнопки медиа-уведомления использует одну и ту же логику. */
@Singleton
class TrackLikeManager @Inject constructor(
    private val likedTracksRepository: LikedTracksRepository,
    private val dislikedTrackDao: DislikedTrackDao,
    private val locationProvider: LocationProvider,
    private val tasteRepository: TasteRepository,
    private val waveRepository: WaveRepository,
) {

    private val mutex = Mutex()

    suspend fun isLiked(trackId: String): Boolean =
        likedTracksRepository.isLiked(trackId).first()

    suspend fun toggle(track: Track): Result<Boolean> = mutex.withLock {
        runCatching {
            val addingLike = !isLiked(track.id)
            val point = if (addingLike) locationProvider.lastKnownLocation() else null
            val likedNow = likedTracksRepository.toggleLike(
                track = track,
                latitude = point?.latitude,
                longitude = point?.longitude,
                season = if (addingLike) SeasonProvider.currentSeason() else null,
            )
            if (likedNow) {
                runCatching { dislikedTrackDao.delete(track.id) }
                runCatching { tasteRepository.onLike(track) }
                runCatching { waveRepository.onTrackLiked(track) }
            } else {
                runCatching { tasteRepository.onUnlike(track) }
            }
            likedNow
        }
    }
}

/** Кнопка лайка в системной медиа-сессии. */
object PlaybackSessionCommands {

    val LIKE = androidx.media3.session.SessionCommand(
        "com.facet.music.LIKE",
        android.os.Bundle.EMPTY,
    )

    fun likeButton(liked: Boolean): androidx.media3.session.CommandButton =
        androidx.media3.session.CommandButton.Builder(
            if (liked) {
                androidx.media3.session.CommandButton.ICON_HEART_FILLED
            } else {
                androidx.media3.session.CommandButton.ICON_HEART_UNFILLED
            },
        )
            .setSessionCommand(LIKE)
            .setDisplayName(if (liked) "Убрать лайк" else "Нравится")
            .setSlots(androidx.media3.session.CommandButton.SLOT_FORWARD_SECONDARY)
            .build()
}
