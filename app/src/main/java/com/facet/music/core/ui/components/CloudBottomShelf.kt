package com.facet.music.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.facet.music.core.ui.theme.Motion
import com.facet.music.data.model.Track
import com.facet.music.navigation.BottomNavItem

@Composable
fun CloudBottomShelf(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    labels: DockLabels,
    showDock: Boolean,
    showMiniPlayer: Boolean,
    track: Track?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCloseClick: () -> Unit,
    currentFraction: () -> Float,
    coverModifier: Modifier,
    showClose: Boolean,
    showProgress: Boolean,
    attached: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showDock && !showMiniPlayer) return
    Surface(
        modifier = if (attached) {
            modifier.fillMaxWidth()
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        },
        shape = if (attached) {
            androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        } else {
            MaterialTheme.shapes.extraLarge
        },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = Motion.spatialDefault())
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (showMiniPlayer && track != null) {
                FloatingMiniPlayer(
                    track = track,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onExpandClick = onExpandClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onCloseClick = onCloseClick,
                    currentFraction = currentFraction,
                    coverModifier = coverModifier,
                    showClose = showClose,
                    showProgress = showProgress,
                    embedded = true,
                )
                if (showDock) {
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (showDock) {
                ExpressiveBottomDock(
                    items = items,
                    selectedRoute = selectedRoute,
                    onSelect = onSelect,
                    labels = labels,
                )
            }
        }
    }
}
