package com.facet.music.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.facet.music.core.ui.theme.Motion
import com.facet.music.core.ui.theme.cloudPress
import com.facet.music.navigation.BottomNavItem

enum class DockLabels {
    ALWAYS,
    SELECTED,
    NEVER,
}

private const val LARGE_FONT_SCALE = 1.6f

@Composable
fun ExpressiveBottomDock(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    labels: DockLabels = DockLabels.ALWAYS,
) {
    val fontScale = LocalDensity.current.fontScale
    val effectiveLabels = if (fontScale >= LARGE_FONT_SCALE) DockLabels.NEVER else labels
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            DockItem(
                item = item,
                selected = item.screen.route == selectedRoute,
                onClick = { onSelect(item) },
                labels = effectiveLabels,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    labels: DockLabels,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.spatialFast(),
        label = "dockIconColor",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.spatialFast(),
        label = "dockLabelColor",
    )
    val showLabel = when (labels) {
        DockLabels.ALWAYS -> true
        DockLabels.SELECTED -> selected
        DockLabels.NEVER -> false
    }
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = Motion.bounce(),
        label = "dockSelectionScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = Motion.spatialFast(),
        label = "dockContainer",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            )
            .cloudPress(interactionSource)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = if (!showLabel) stringResource(item.labelRes) else null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(
            visible = showLabel,
            enter = expandVertically(animationSpec = Motion.spatialFast()),
            exit = shrinkVertically(animationSpec = Motion.spatialFast()),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    maxLines = 1,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
