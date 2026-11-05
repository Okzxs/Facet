package com.facet.music.data.model

import kotlinx.serialization.Serializable

/** Полная шапка канала: имя, аватары, баннер и статистика. */
@Serializable
data class ArtistProfile(
    val source: Source,
    val url: String,
    val name: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val subscriberCount: Long? = null,
    val description: String? = null,
    val isVerified: Boolean = false,
) {
    val key: String get() = "${source.name}:$url"

    fun toArtist(): Artist = Artist(
        id = url,
        source = source,
        name = name,
        avatarUrl = avatarUrl,
        subscriberCount = subscriberCount,
        isVerified = isVerified,
        url = url,
    )
}

/** Альбом или сингл артиста. */
@Serializable
data class ArtistRelease(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val year: String? = null,
    val url: String,
    val trackCount: Int = 0,
    val isAlbum: Boolean = true,
)

/** Плейлист артиста. */
@Serializable
data class ArtistPlaylist(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val url: String,
    val trackCount: Int = 0,
)

/** Первая страница экрана артиста; [nextPage] — токен «Всех треков». */
@Serializable
data class ArtistPage(
    val profile: ArtistProfile,
    val popular: List<Track> = emptyList(),
    val releases: List<ArtistRelease> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val playlists: List<ArtistPlaylist> = emptyList(),
    val related: List<Artist> = emptyList(),
    val nextPage: String? = null,
)
