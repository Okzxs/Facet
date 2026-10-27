package com.my.music.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.my.music.feature.player.PlayerViewModel
import com.my.music.navigation.BottomNavItem

/**
 * Единый нижний док: mini-player и навигация в одном плавающем объекте.
 *
 * Форма 30dp, тень 8dp, фон surfaceContainerHigh 96% и бордер 1dp.
 * Появление мини-плеера — рост дока на 56dp (пружина 0.8/MediumLow),
 * ряд навигации при этом остаётся на месте.
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = shape, clip = false),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        ),
    ) {
        Column {
            AnimatedVisibility(
                visible = track != null,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    expandFrom = Alignment.Bottom,
                    initialHeight = { 0 },
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    shrinkTowards = Alignment.Bottom,
                    targetHeight = { 0 },
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                if (track != null) {
                    MiniPlayerRow(
                        track = track,
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
            )
        }
    }
}
