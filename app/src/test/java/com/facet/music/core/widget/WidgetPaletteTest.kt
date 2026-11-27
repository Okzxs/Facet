package com.facet.music.core.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPaletteTest {

    @Test
    fun `dark palette keeps light text and dark surface`() {
        val palette = WidgetPalette.of(
            dark = true,
            amoled = false,
            seed = WidgetPalette.SignatureSeed,
            shapeStyle = "DEFAULT",
            serifTitle = true,
        )
        assertTrue(palette.dark)
        assertTrue(palette.onBackground.luminance() > palette.background.luminance())
        assertEquals(true, palette.serifTitle)
        assertEquals(28.dp, palette.cornerRadius)
    }

    @Test
    fun `amoled palette uses pure black`() {
        val palette = WidgetPalette.of(
            dark = true,
            amoled = true,
            seed = WidgetPalette.SignatureSeed,
            shapeStyle = "DEFAULT",
            serifTitle = false,
        )
        assertEquals(Color.Black, palette.background)
        assertEquals(false, palette.serifTitle)
    }

    @Test
    fun `custom seed changes accent`() {
        val brass = WidgetPalette.of(
            dark = true,
            amoled = false,
            seed = WidgetPalette.SignatureSeed,
            shapeStyle = "DEFAULT",
            serifTitle = true,
        )
        val blue = WidgetPalette.of(
            dark = true,
            amoled = false,
            seed = Color(0xFF1F5FD8),
            shapeStyle = "DEFAULT",
            serifTitle = true,
        )
        assertNotEquals(brass.accent, blue.accent)
        assertNotEquals(brass.accentSoft, blue.accentSoft)
    }

    @Test
    fun `shape style controls corner radius`() {
        fun radius(style: String) = WidgetPalette.of(
            dark = false,
            amoled = false,
            seed = WidgetPalette.SignatureSeed,
            shapeStyle = style,
            serifTitle = true,
        ).cornerRadius

        assertEquals(20.dp, radius("SOFT"))
        assertEquals(28.dp, radius("DEFAULT"))
        assertEquals(32.dp, radius("ROUND"))
        assertEquals(28.dp, radius("UNKNOWN"))
    }

    @Test
    fun `scrim follows background with transparency`() {
        val palette = WidgetPalette.of(
            dark = true,
            amoled = false,
            seed = WidgetPalette.SignatureSeed,
            shapeStyle = "DEFAULT",
            serifTitle = true,
        )
        assertTrue(palette.scrim.alpha in 0.5f..0.9f)
        assertEquals(palette.background.red, palette.scrim.red, 0.0001f)
        assertEquals(palette.background.green, palette.scrim.green, 0.0001f)
        assertEquals(palette.background.blue, palette.scrim.blue, 0.0001f)
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
