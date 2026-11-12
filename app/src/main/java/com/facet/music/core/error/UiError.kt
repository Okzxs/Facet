package com.facet.music.core.error

import android.content.Context
import androidx.annotation.StringRes
import com.facet.music.R

sealed class UiError(@StringRes val messageRes: Int) {
    data object NoInternet : UiError(R.string.error_no_internet)
    data object Timeout : UiError(R.string.error_timeout)
    data object SecureConnection : UiError(R.string.error_secure_connection)
    data object Playback : UiError(R.string.error_playback)
    data object Lyrics : UiError(R.string.error_lyrics)
    data object TrackUnavailable : UiError(R.string.error_track_unavailable)
    data object SubscriptionOnly : UiError(R.string.error_subscription_only)
    data object GeoBlocked : UiError(R.string.error_geo_blocked)
    data object NoMatch : UiError(R.string.error_no_match)
    data object PlayerUnavailable : UiError(R.string.error_player_unavailable)
    data object LikeFailed : UiError(R.string.error_like_failed)
    data object DislikeFailed : UiError(R.string.error_dislike_failed)

    fun resolve(context: Context): String = context.getString(messageRes)
}
