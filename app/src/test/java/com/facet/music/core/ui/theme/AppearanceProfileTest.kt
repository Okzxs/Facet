package com.facet.music.core.ui.theme

import com.facet.music.data.repository.AppSettings
import com.facet.music.data.repository.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceProfileTest {

    @Test
    fun `profile round trips through json`() {
        val original = AppearanceProfile(
            themeMode = ThemeMode.DARK.name,
            paletteMode = "NIGHT_JAZZ",
            customAccentColor = 0xFFC8A46A.toInt(),
            accentEnabled = true,
            amoledBlack = true,
            serifDisplay = false,
            textScale = 1.15f,
            shapeStyle = "ROUND",
            dockStyle = AppSettings.DOCK_ATTACHED,
            dockLabels = AppSettings.LABELS_NEVER,
            tabOrder = listOf("wave", "home", "search", "library"),
            hiddenTabs = setOf("library"),
            homeSectionOrder = listOf(
                AppSettings.SECTION_RECOMMENDATIONS,
                AppSettings.SECTION_HERO,
            ),
            hiddenHomeSections = setOf(AppSettings.SECTION_CHARTS),
            homeHeroStyle = AppSettings.HERO_MINIMAL,
            homeShowWavePromo = false,
            playerLayout = AppSettings.LAYOUT_IMMERSIVE,
            coverShape = AppSettings.COVER_CIRCLE,
            coverSize = AppSettings.COVER_MEDIUM,
            playerBackdrop = AppSettings.BACKDROP_SOLID,
            dismissSensitivity = AppSettings.SENSITIVITY_HIGH,
            progressStyle = AppSettings.PROGRESS_LINE,
            miniPlayerClose = false,
            miniPlayerProgress = false,
            lyricScale = 1.2f,
            lyricBold = false,
            lyricBlur = false,
            eqPreset = "CUSTOM",
            eqCustomBands = listOf(1f, 2f, 3f, 4f, 5f, 6f, -6f),
            skipSilence = true,
            libraryPlaylistLayout = AppSettings.LIBRARY_LIST,
        )

        val parsed = AppearanceProfile.parse(original.toJson())

        assertEquals(original.sanitized(), parsed)
    }

    @Test
    fun `unknown values fall back to signature defaults`() {
        val parsed = AppearanceProfile.parse(
            """
            {
              "schemaVersion": 99,
              "themeMode": "SEPIA",
              "paletteMode": "NEON",
              "shapeStyle": "SHARP",
              "dockStyle": "ORBITAL",
              "dockLabels": "RANDOM",
              "tabOrder": ["nowhere"],
              "homeSectionOrder": ["nowhere"],
              "homeHeroStyle": "GIANT",
              "playerLayout": "FLOATING",
              "coverShape": "TRIANGLE",
              "coverSize": "HUGE",
              "playerBackdrop": "RAINBOW",
              "dismissSensitivity": "PSYCHIC",
              "textScale": 4.5,
              "lyricScale": 3.7,
              "eqCustomBands": [99.0],
              "libraryPlaylistLayout": "CAROUSEL",
              "progressStyle": "RADIAL"
            }
            """.trimIndent(),
        )

        assertNotNull(parsed)
        val safe = requireNotNull(parsed)
        assertEquals(AppearanceProfile.SCHEMA_VERSION, safe.schemaVersion)
        assertEquals(ThemeMode.SYSTEM.name, safe.themeMode)
        assertEquals("SIGNATURE", safe.paletteMode)
        assertEquals("DEFAULT", safe.shapeStyle)
        assertEquals(AppSettings.DOCK_FLOATING, safe.dockStyle)
        assertEquals(AppSettings.LABELS_ALWAYS, safe.dockLabels)
        assertEquals(AppSettings.DEFAULT_TAB_ORDER, safe.tabOrder)
        assertEquals(AppSettings.DEFAULT_HOME_SECTION_ORDER, safe.homeSectionOrder)
        assertEquals(AppSettings.HERO_EDITORIAL, safe.homeHeroStyle)
        assertEquals(AppSettings.LAYOUT_ARTWORK, safe.playerLayout)
        assertEquals(AppSettings.COVER_AUTO, safe.coverShape)
        assertEquals(AppSettings.COVER_LARGE, safe.coverSize)
        assertEquals(AppSettings.BACKDROP_COVER, safe.playerBackdrop)
        assertEquals(AppSettings.SENSITIVITY_MEDIUM, safe.dismissSensitivity)
        assertEquals(AppSettings.PROGRESS_WAVE, safe.progressStyle)
        assertEquals(1.25f, safe.lyricScale, 0.001f)
        assertTrue(safe.lyricBold)
        assertTrue(safe.lyricBlur)
        assertEquals(AppSettings.LIBRARY_GRID, safe.libraryPlaylistLayout)
        assertEquals("FLAT", safe.eqPreset)
        assertEquals(AppSettings.DEFAULT_EQ_BANDS, safe.eqCustomBands)
        assertTrue(safe.skipSilence.not())
        assertEquals(1.3f, safe.textScale, 0.001f)
    }

    @Test
    fun `profile tolerates missing fields`() {
        val parsed = AppearanceProfile.parse("""{"serifDisplay": false}""")

        assertNotNull(parsed)
        assertTrue(requireNotNull(parsed).serifDisplay.not())
        assertEquals("SIGNATURE", requireNotNull(parsed).paletteMode)
    }

    @Test
    fun `broken json is rejected`() {
        assertNull(AppearanceProfile.parse("not a profile"))
        assertNull(AppearanceProfile.parse(""))
        assertNull(AppearanceProfile.parse("""{"textScale": "loud"}"""))
    }

    @Test
    fun `profile from settings preserves dock and home configuration`() {
        val settings = AppSettings(
            paletteMode = "MONO",
            tabOrder = listOf("wave", "library", "search", "home"),
            hiddenTabs = setOf("search"),
            homeSectionOrder = listOf(
                AppSettings.SECTION_RECOMMENDATIONS,
                AppSettings.SECTION_HERO,
                AppSettings.SECTION_WAVE,
            ),
            hiddenHomeSections = setOf(AppSettings.SECTION_WAVE),
        )

        val profile = AppearanceProfile.from(settings)

        assertEquals(listOf("wave", "library", "home"), profile.tabOrder)
        assertEquals(setOf("search"), profile.hiddenTabs)
        assertEquals(
            listOf(
                AppSettings.SECTION_RECOMMENDATIONS,
                AppSettings.SECTION_HERO,
                AppSettings.SECTION_NEW_RELEASES,
                AppSettings.SECTION_CHARTS,
                AppSettings.SECTION_GENRES,
                AppSettings.SECTION_RECENT,
                AppSettings.SECTION_FORGOTTEN,
                AppSettings.SECTION_FRESH_LIKES,
                AppSettings.SECTION_MEMORIES,
            ),
            profile.homeSectionOrder,
        )
        assertEquals(setOf(AppSettings.SECTION_WAVE), profile.hiddenHomeSections)
    }

    @Test
    fun `ordered tabs append missing entries and drop hidden ones`() {
        val settings = AppSettings(
            tabOrder = listOf("library", "home"),
            hiddenTabs = setOf("library"),
        )

        assertEquals(listOf("home", "search", "wave"), settings.orderedTabs())
    }

    @Test
    fun `ordered tabs never empty`() {
        val settings = AppSettings(
            tabOrder = listOf("lost"),
            hiddenTabs = emptySet(),
        )

        assertEquals(AppSettings.DEFAULT_TAB_ORDER, settings.orderedTabs())
    }
}
