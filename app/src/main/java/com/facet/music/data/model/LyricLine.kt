package com.facet.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class LyricsResult(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val isInstrumental: Boolean = false,
    val provider: String = "LRCLIB",
)
