package com.facet.music.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

enum class FacetShapeStyle {
    SOFT,
    DEFAULT,
    ROUND,
    ;

    companion object {
        fun fromName(raw: String?): FacetShapeStyle =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

fun shapesFor(style: FacetShapeStyle): Shapes = when (style) {
    FacetShapeStyle.SOFT -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(7.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(22.dp),
    )
    FacetShapeStyle.DEFAULT -> AppShapes
    FacetShapeStyle.ROUND -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
}
