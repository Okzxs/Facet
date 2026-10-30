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

    /** Экран одного плейлиста: маршрут с аргументом-id. */
    object Playlist : Screen("playlist/{$PLAYLIST_ID_ARG}") {
        const val ARG_ID = PLAYLIST_ID_ARG

        /** Собирает конкретный маршрут для навигации. */
        fun createRoute(playlistId: Long): String = "playlist/$playlistId"
    }

    /**
     * Экран артиста: имя приходит аргументом и кодируется в URL,
     * иначе пробелы и «/» ломают маршрут.
     */
    object Artist : Screen("artist/{$ARTIST_NAME_ARG}") {
        const val ARG_NAME = ARTIST_NAME_ARG

        /** Собирает маршрут артиста с экранированием имени. */
        fun createRoute(artistName: String): String = "artist/${Uri.encode(artistName)}"
    }
}

/** Имя аргумента маршрута плейлиста — общее для [Screen.Playlist] и навграфа. */
const val PLAYLIST_ID_ARG = "playlistId"

/** Имя аргумента маршрута артиста — общее для [Screen.Artist] и навграфа. */
const val ARTIST_NAME_ARG = "artistName"

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
