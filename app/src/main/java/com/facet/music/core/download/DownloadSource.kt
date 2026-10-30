package com.facet.music.core.download

import com.facet.music.data.model.AudioStreamResult
import com.facet.music.data.model.Source
import com.facet.music.data.model.Track

/**
 * Адаптер одного источника для скачивания.
 *
 * Резолвер не знает о конкретных репозиториях: он работает с набором
 * [DownloadSource] из Hilt и выбирает только те, что умеют progressive.
 */
interface DownloadSource {

    /** Доменный источник, который представляет адаптер. */
    val source: Source

    /** Умеет ли источник отдать цельный файл. */
    val capability: DownloadCapability

    /** Поиск трека в этом источнике. */
    suspend fun search(query: String, limit: Int): Result<List<Track>>

    /**
     * Получить прямой progressive-поток для [track].
     * Для HLS-источников не вызывается.
     */
    suspend fun resolve(track: Track): Result<AudioStreamResult>
}
