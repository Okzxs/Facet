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
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Wave : Screen("wave")

    object Player : Screen("player")
    object Settings : Screen("settings")

    object LikedTracks : Screen("liked")

    object Import : Screen("import")

    object Playlist : Screen("playlist/{$PLAYLIST_ID_ARG}") {
        const val ARG_ID = PLAYLIST_ID_ARG

        fun createRoute(playlistId: Long): String = "playlist/$playlistId"
    }

    object Artist : Screen("artist/{$ARTIST_NAME_ARG}") {
        const val ARG_NAME = ARTIST_NAME_ARG

        fun createRoute(artistName: String): String = "artist/${Uri.encode(artistName)}"
    }

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

const val PLAYLIST_ID_ARG = "playlistId"

const val ARTIST_NAME_ARG = "artistName"

const val ARTIST_SOURCE_ARG = "artistSource"

const val ARTIST_URL_ARG = "artistUrl"

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
