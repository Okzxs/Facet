package com.facet.music.core.import

/**
 * Плейлист из публичного профиля другого сервиса — до импорта.
 *
 * @param id идентификатор внутри сервиса (Spotify) либо URL (SoundCloud);
 * @param url прямая ссылка на плейлист для повторного разбора;
 * @param trackCount сколько треков обещает сервис.
 */
data class ProfilePlaylist(
    val service: ImportService,
    val id: String,
    val url: String?,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
)
