package com.facet.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val PillShape: Shape = RoundedCornerShape(percent = 50)

val CloudCardShape: Shape = RoundedCornerShape(24.dp)

val CloudPanelShape: Shape = RoundedCornerShape(28.dp)
