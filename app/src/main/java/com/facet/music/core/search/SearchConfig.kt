package com.facet.music.core.search

/** Веса ранжирования поисковой выдачи. */
object SearchConfig {
    const val EXACT_ARTIST_BOOST = 3.0
    const val ALL_TOKENS_BOOST = 2.0
    const val EXTRA_SOURCE_BOOST = 0.3
    const val VERSION_PENALTY = 1.5
    const val ARTIST_PREFIX_MIN = 3
    const val JARO_WINKLER_THRESHOLD = 0.92

    val VERSION_WORDS = listOf(
        "cover",
        "remix",
        "nightcore",
        "sped up",
        "slowed",
        "karaoke",
        "8d",
    )
}
