package com.facet.music.core.service

import com.facet.music.data.model.Source

enum class ServiceType(val displayName: String, val source: Source) {
    SOUNDCLOUD("SoundCloud", Source.SOUNDCLOUD),
    YOUTUBE("YouTube", Source.YOUTUBE),
    SPOTIFY("Spotify", Source.SPOTIFY),
}
