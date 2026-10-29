package com.my.music.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.my.music.navigation.BottomNavItem

/** Высота контента навигационного бара без системного инсета. */
internal val DockNavHeight = 72.dp

/**
 * Нижняя навигационная панель: полноценный бар во всю ширину, прибитый
 * к нижней кромке экрана. Фон уходит под системную панель навигации,
 * а контент поднят на её инсет через [navigationBarsPadding].
 *
 * Мини-плеер больше не живёт внутри панели — это отдельный плавающий
 * островок (см. [FloatingMiniPlayer]), который висит над баром.
 */
@Composable
fun ExpressiveBottomDock(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Инсет внутри фона: сама поверхность доходит до нижней кромки
                // экрана и не обрезается по краям, как полноценный бар.
                .navigationBarsPadding()
                .height(DockNavHeight),
        ) {
            ExpressiveNavigationBar(
                items = items,
                selectedRoute = selectedRoute,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
