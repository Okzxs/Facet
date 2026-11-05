package com.facet.music.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri
import com.facet.music.R
import com.facet.music.data.model.Source

sealed class Screen(val route: String) {
    // Вкладки нижней навигации
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Wave : Screen("wave")

    // Полноэкранные экраны (без нижней панели)
    object Player : Screen("player")
    object Settings : Screen("settings")

    /** Коллекция любимых треков — открывается из чипса медиатеки. */
    object LikedTracks : Screen("liked")

    /** Импорт из профиля Spotify/SoundCloud и из текстового списка. */
    object Import : Screen("import")

    /** Экран одного плейлиста: маршрут с аргументом-id. */
    object Playlist : Screen("playlist/{$PLAYLIST_ID_ARG}") {
        const val ARG_ID = PLAYLIST_ID_ARG

        /** Собирает конкретный маршрут для навигации. */
        fun createRoute(playlistId: Long): String = "playlist/$playlistId"
    }

    /**
     * Старый маршрут артиста: ищет лучшего артиста по имени и открывает его.
     * Оставлен для переходов по имени из мест, где нет ссылки на канал.
     */
    object Artist : Screen("artist/{$ARTIST_NAME_ARG}") {
        const val ARG_NAME = ARTIST_NAME_ARG

        /** Собирает маршрут артиста с экранированием имени. */
        fun createRoute(artistName: String): String = "artist/${Uri.encode(artistName)}"
    }

    /**
     * Точный экран артиста: источник и URL канала, имя — подсказка для шапки
     * до загрузки. URL кодируется, иначе «/» ломает путь.
     */
    object ArtistDetail : Screen(
        "artist/{$ARTIST_SOURCE_ARG}/{$ARTIST_URL_ARG}?$ARTIST_NAME_ARG={$ARTIST_NAME_ARG}",
    ) {
        const val ARG_SOURCE = ARTIST_SOURCE_ARG
        const val ARG_URL = ARTIST_URL_ARG
        const val ARG_NAME = ARTIST_NAME_ARG

        fun createRoute(
            source: Source,
            url: String,
            name: String? = null,
        ): String {
            val base = "artist/${source.name}/${Uri.encode(url)}"
            val hint = name?.takeIf { it.isNotBlank() }
                ?.let { "?$ARTIST_NAME_ARG=${Uri.encode(it)}" }
                .orEmpty()
            return base + hint
        }
    }
}

/** Имя аргумента маршрута плейлиста — общее для [Screen.Playlist] и навграфа. */
const val PLAYLIST_ID_ARG = "playlistId"

/** Имя аргумента маршрута артиста — общее для [Screen.Artist] и навграфа. */
const val ARTIST_NAME_ARG = "artistName"

/** Источник точного экрана артиста: YOUTUBE или SOUNDCLOUD. */
const val ARTIST_SOURCE_ARG = "artistSource"

/** URL канала/профиля в точном маршруте артиста. */
const val ARTIST_URL_ARG = "artistUrl"

/**
 * Подпись вкладки хранится ссылкой на строковый ресурс: язык интерфейса
 * меняется на лету, поэтому строку нельзя зашивать в элемент списка.
 */
data class BottomNavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Library, R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomNavItem(Screen.Wave, R.string.nav_wave, Icons.Filled.Waves, Icons.Outlined.Waves),
)
