package com.facet.music.data.model

data class TrackVariant(
    val track: Track,
    val confidence: Double,
    val lastCheckedAt: Long,
)

data class CanonicalTrack(
    val key: String,
    val title: String,
    val artist: String,
    val primary: Track,
    val variants: List<TrackVariant>,
) {
    val sources: List<Source> = variants.map { it.track.source }.distinct()

    val sourceCount: Int = sources.size

    val hasAlternatives: Boolean = variants.size > 1

    fun trackFor(source: Source): Track? =
        variants.firstOrNull { it.track.source == source }?.track
}
