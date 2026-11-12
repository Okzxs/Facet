package com.facet.music.core.player

import android.net.Uri

object PlaybackUri {

    private const val SCHEME = "facet"
    private const val HOST = "track"

    fun forTrack(trackId: String): Uri =
        Uri.parse("$SCHEME://$HOST/${Uri.encode(trackId)}")

    fun trackId(uri: Uri): String? {
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        return uri.lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
