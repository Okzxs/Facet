package com.my.music.navigation

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

sealed class Screen(val route: String) {
    // Вкладки нижней навигации
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Wave : Screen("wave")

    // Полноэкранные экраны (без нижней панели)
    object Player : Screen("player")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Главная", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, "Поиск", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Library, "Библиотека", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomNavItem(Screen.Wave, "Моя волна", Icons.Filled.Waves, Icons.Outlined.Waves),
)
