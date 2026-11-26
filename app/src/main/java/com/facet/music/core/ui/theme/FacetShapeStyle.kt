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
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    FacetShapeStyle.DEFAULT -> AppShapes
    FacetShapeStyle.ROUND -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(18.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
}
