package com.my.music.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.data.model.Track
import com.my.music.feature.player.PlayerViewModel
import com.my.music.navigation.BottomNavItem

private val DockNavHeight = 64.dp
private val DockMiniHeight = 56.dp

/**
 * Единый нижний док: mini-player и навигация в одном плавающем объекте.
 *
 * Ряд навигации закреплён у нижней кромки и не участвует в анимации мини-плеера.
 * Фоновая поверхность растёт отдельно, а строка мини-плеера раскрывается
 * через [AnimatedVisibility] — высота всего дока вместе с детьми не анимируется.
 */
@Composable
fun ExpressiveBottomDock(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by viewModel.playerState.collectAsState()
    val track = playerState.currentTrack
    val shape = RoundedCornerShape(30.dp)

    // Последний трек нужен, чтобы строка успела уехать при закрытии плеера.
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(track) {
        if (track != null) lastTrack = track
    }
    val miniTrack = track ?: lastTrack

    // Прогресс раскрытия читает только фон, поэтому навигация и мини-плеер
    // не рекомпозируются на каждом кадре анимации.
    val reveal = animateFloatAsState(
        targetValue = if (track != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "dockReveal",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        DockBackground(
            reveal = reveal,
            shape = shape,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = track != null,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                expandFrom = Alignment.Bottom,
            ) + fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                shrinkTowards = Alignment.Bottom,
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DockNavHeight),
        ) {
            if (miniTrack != null) {
                MiniPlayerRow(
                    track = miniTrack,
                    isPlaying = playerState.isPlaying,
                    onExpandClick = onExpandClick,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    currentFraction = { viewModel.currentFraction() },
                )
            }
        }

        ExpressiveNavigationBar(
            items = items,
            selectedRoute = selectedRoute,
            onSelect = onSelect,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Фоновая поверхность дока. Высота читается из [reveal] только здесь,
 * поэтому анимация не трогает layout навигации и мини-плеера.
 */
@Composable
private fun DockBackground(
    reveal: State<Float>,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(DockNavHeight + DockMiniHeight * reveal.value),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        ),
        shadowElevation = 8.dp,
    ) {}
}
