package com.facet.music.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Регрессия «бордового акцента»: чёрный, белый и серый имеют в HSL оттенок 0
 * (красный), и принудительная насыщенность ролей красила такой акцент в бордовый.
 * Ахроматический seed обязан давать строго монохромную палитру.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccentSchemeTest {

    @Test
    fun `black accent gives monochrome scheme in both themes`() {
        assertMonochrome(accentColorScheme(Color.Black, darkTheme = false))
        assertMonochrome(accentColorScheme(Color.Black, darkTheme = true))
    }

    @Test
    fun `white accent gives monochrome scheme in both themes`() {
        assertMonochrome(accentColorScheme(Color.White, darkTheme = false))
        assertMonochrome(accentColorScheme(Color.White, darkTheme = true))
    }

    @Test
    fun `gray accent gives monochrome scheme`() {
        assertMonochrome(accentColorScheme(Color(0xFF808080), darkTheme = false))
        assertMonochrome(accentColorScheme(Color(0xFF808080), darkTheme = true))
    }

    @Test
    fun `black and white accents give distinguishable grays`() {
        val black = accentColorScheme(Color.Black, darkTheme = false).primary
        val white = accentColorScheme(Color.White, darkTheme = false).primary
        assertTrue("black primary must be darker than white primary", black.red < white.red)
    }

    @Test
    fun `saturated accent keeps its hue`() {
        val primary = accentColorScheme(Color(0xFFE5484D), darkTheme = false).primary
        assertTrue("red accent must stay reddish", primary.red > primary.green + 0.1f)
    }

    @Test
    fun `monochrome primary stays readable in both themes`() {
        // Светлая тема: primary достаточно тёмный под белый onPrimary.
        val light = accentColorScheme(Color.Black, darkTheme = false)
        assertTrue(light.primary.red < 0.6f)
        // Тёмная тема: primary достаточно светлый под тёмный onPrimary.
        val dark = accentColorScheme(Color.White, darkTheme = true)
        assertTrue(dark.primary.red > 0.5f)
    }

    private fun assertMonochrome(scheme: ColorScheme) {
        listOf(
            scheme.primary,
            scheme.primaryContainer,
            scheme.secondary,
            scheme.tertiary,
        ).forEach { color ->
            assertEquals("red == green", color.red.toDouble(), color.green.toDouble(), 0.01)
            assertEquals("green == blue", color.green.toDouble(), color.blue.toDouble(), 0.01)
        }
    }
}
