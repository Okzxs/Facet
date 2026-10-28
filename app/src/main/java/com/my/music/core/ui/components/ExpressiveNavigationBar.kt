package com.my.music.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.music.navigation.BottomNavItem

/**
 * Ряд нижней навигации внутри общего дока: пружинный индикатор активной вкладки,
 * масштабирование иконок и тактильный отклик. Собственного фона и тени нет —
 * форму и поверхность задаёт [ExpressiveBottomDock].
 *
 * Индикатор и масштаб иконки живут в фазе отрисовки ([graphicsLayer]),
 * поэтому анимация не вызывает рекомпозицию ряда и не двигает соседние элементы.
 */
@Composable
fun ExpressiveNavigationBar(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    // Индекс выбранной вкладки пересчитывается только при смене маршрута.
    val selectedIndex = remember(items, selectedRoute) {
        items.indexOfFirst { it.screen.route == selectedRoute }.coerceAtLeast(0)
    }
    val haptics = LocalHapticFeedback.current
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelect)

    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val indicatorBrush = remember(primaryContainer, tertiaryContainer) {
        Brush.linearGradient(listOf(primaryContainer, tertiaryContainer))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(DockNavHeight),
    ) {
        val itemWidth = maxWidth / items.size
        val indicatorWidth = 60.dp
        val indicatorHeight = 32.dp
        // Индикатор совмещён с квадратом иконки: контент пункта центрируется
        // в баре 72dp, поэтому верх иконки оказывается на 13dp.
        val indicatorTop = 13.dp

        // Плавное скольжение без отскока: стандартная пружина Compose.
        val indicatorOffset = animateDpAsState(
            targetValue = itemWidth * selectedIndex + (itemWidth - indicatorWidth) / 2,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "indicatorOffset",
        )

        // Смещение читается в фазе отрисовки: layout ряда не пересчитывается.
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = indicatorOffset.value.toPx()
                    translationY = indicatorTop.toPx()
                }
                .width(indicatorWidth)
                .height(indicatorHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(indicatorBrush),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                key(item.screen.route) {
                    // Лямбда стабильна: при выборе другой вкладки не пересоздаётся.
                    val onClick = remember(item) {
                        {
                            if (index != latestSelectedIndex) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelect(item)
                            }
                        }
                    }
                    ExpressiveNavItem(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = onClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navItemColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navItemScale",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.62f,
        animationSpec = tween(220),
        label = "navItemLabelAlpha",
    )

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(bounded = false),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(top = 9.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }

        Spacer(Modifier.height(1.dp))

        Text(
            text = item.label,
            color = contentColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}
