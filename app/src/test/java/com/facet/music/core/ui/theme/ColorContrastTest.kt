package com.facet.music.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Контраст пар ролей HCT-схемы: 24 оттенка (шаг 15°) × светлая/тёмная тема.
 * Каждая пара обязана давать не меньше 4.5:1 по WCAG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColorContrastTest {

    @Test
    fun `all hue pairs meet wcag contrast in both themes`() {
        for (step in 0 until 24) {
            val hue = step * 15f
            val seed = Color.hsv(hue, 0.70f, 0.85f)
            for (dark in listOf(false, true)) {
                val scheme = accentColorScheme(seed, darkTheme = dark)
                val label = "hue=$hue dark=$dark"
                assertPair(label, "primary/onPrimary", scheme.primary, scheme.onPrimary)
                assertPair(
                    label,
                    "primaryContainer/onPrimaryContainer",
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer,
                )
                assertPair(label, "surface/onSurface", scheme.surface, scheme.onSurface)
                assertPair(label, "surface/onSurfaceVariant", scheme.surface, scheme.onSurfaceVariant)
            }
        }
    }

    @Test
    fun `bestContentColor keeps contrast for mid luminance backgrounds`() {
        for (step in 0..20) {
            val gray = Color.hsv(0f, 0f, step / 20f)
            val (bg, content) = bestContentColor(gray)
            assertTrue(
                "gray step=$step contrast=${wcagContrast(bg, content)}",
                wcagContrast(bg, content) >= 4.5,
            )
        }
    }

    @Test
    fun `bestContentColor chooses by max contrast not luminance half`() {
        val darkish = Color.hsv(210f, 0.5f, 0.45f)
        val (_, content) = bestContentColor(darkish)
        assertTrue(content == Color.White || content == Color.Black)
        assertTrue(wcagContrast(darkish, content) >= 4.5)
    }

    private fun assertPair(label: String, pair: String, bg: Color, content: Color) {
        val contrast = wcagContrast(bg, content)
        assertTrue("$label $pair contrast=$contrast < 4.5", contrast >= 4.5)
    }
}