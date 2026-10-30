package com.facet.music.core.import

import com.facet.music.data.model.Track

/**
 * Плейлист, загруженный по ссылке, но ещё не сохранённый в Room.
 *
 * @param name название из сервиса (для трека Apple — его название);
 * @param service источник, из которого пришли треки;
 * @param tracks треки в порядке сервиса.
 */
data class ImportedPlaylist(
    val name: String,
    val service: ImportService,
    val tracks: List<Track>,
)
