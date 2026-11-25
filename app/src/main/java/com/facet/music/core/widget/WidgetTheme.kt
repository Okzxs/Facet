package com.facet.music.core.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.facet.music.core.i18n.AppLocale
import com.facet.music.core.ui.theme.accentColorScheme

data class WidgetPalette(
    val dark: Boolean,
    val background: Color,
    val onBackground: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val track: Color,
    val scrim: Color,
    val cornerRadius: Dp,
    val serifTitle: Boolean,
) {
    companion object {

        val SignatureSeed: Color = Color(0xFFC8A46A)
        val JazzSeed: Color = Color(0xFF6C7BC4)
        val PaperSeed: Color = Color(0xFF8A6A34)
        val MonoSeed: Color = Color(0xFF8A8A8A)

        fun of(
            dark: Boolean,
            amoled: Boolean,
            seed: Color,
            shapeStyle: String,
            serifTitle: Boolean,
        ): WidgetPalette {
            val scheme = accentColorScheme(seed, dark)
            val background = if (dark && amoled) Color.Black else scheme.background
            val onBackground = scheme.onBackground
            return WidgetPalette(
                dark = dark,
                background = background,
                onBackground = onBackground,
                muted = scheme.onSurfaceVariant,
                accent = scheme.primary,
                onAccent = scheme.onPrimary,
                accentSoft = scheme.primaryContainer,
                track = onBackground.copy(alpha = 0.16f),
                scrim = background.copy(alpha = if (dark) 0.74f else 0.84f),
                cornerRadius = when (shapeStyle.uppercase()) {
                    "SOFT" -> 20.dp
                    "ROUND" -> 32.dp
                    else -> 24.dp
                },
                serifTitle = serifTitle,
            )
        }
    }
}

object WidgetTheme {

    private const val PREFS_NAME = AppLocale.PREFS_NAME
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AMOLED_BLACK = "amoled_black"
    private const val KEY_ACCENT_ENABLED = "accent_enabled"
    private const val KEY_CUSTOM_ACCENT = "custom_accent_color"
    private const val KEY_PALETTE_MODE = "palette_mode"
    private const val KEY_SHAPE_STYLE = "shape_style"
    private const val KEY_SERIF_DISPLAY = "serif_display"

    private val cacheLock = Any()
    private var cachedKey: String? = null
    private var cachedPalette: WidgetPalette? = null

    fun resolve(context: Context): WidgetPalette {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val dark = when (prefs.getString(KEY_THEME_MODE, null)) {
            "LIGHT" -> false
            "DARK" -> true
            else -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val amoled = prefs.getBoolean(KEY_AMOLED_BLACK, false)
        val accentEnabled = prefs.getBoolean(KEY_ACCENT_ENABLED, false)
        val customAccent = if (
            accentEnabled && prefs.contains(KEY_CUSTOM_ACCENT)
        ) {
            prefs.getInt(KEY_CUSTOM_ACCENT, 0)
        } else {
            null
        }
        val palette = prefs.getString(KEY_PALETTE_MODE, null)
        val shapeStyle = prefs.getString(KEY_SHAPE_STYLE, null) ?: "DEFAULT"
        val serif = prefs.getBoolean(KEY_SERIF_DISPLAY, true)
        val seed = customAccent?.let { Color(it) } ?: seedFor(context, palette, dark)
        val key = "$dark|$amoled|${seed.value}|$shapeStyle|$serif"
        synchronized(cacheLock) {
            if (key == cachedKey) cachedPalette?.let { return it }
        }
        val resolved = WidgetPalette.of(
            dark = dark,
            amoled = amoled,
            seed = seed,
            shapeStyle = shapeStyle,
            serifTitle = serif,
        )
        synchronized(cacheLock) {
            cachedKey = key
            cachedPalette = resolved
        }
        return resolved
    }

    private fun seedFor(context: Context, palette: String?, dark: Boolean): Color = when (palette) {
        "NIGHT_JAZZ" -> WidgetPalette.JazzSeed
        "PAPER" -> WidgetPalette.PaperSeed
        "MONO" -> WidgetPalette.MonoSeed
        "DYNAMIC" -> dynamicSeed(context, dark)
        else -> WidgetPalette.SignatureSeed
    }

    private fun dynamicSeed(context: Context, dark: Boolean): Color {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WidgetPalette.SignatureSeed
        val res = if (dark) {
            android.R.color.system_accent1_200
        } else {
            android.R.color.system_accent1_600
        }
        return runCatching { Color(ContextCompat.getColor(context, res)) }
            .getOrDefault(WidgetPalette.SignatureSeed)
    }
}
