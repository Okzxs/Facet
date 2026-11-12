package com.facet.music.core.import

import com.facet.music.data.model.Track

data class ImportedPlaylist(
    val name: String,
    val service: ImportService,
    val tracks: List<Track>,
)
