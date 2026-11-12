package com.facet.music.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import kotlin.math.max

private const val MIN_CONTRAST = 4.5

fun wcagContrast(first: Color, second: Color): Double {
    val a = first.luminance()
    val b = second.luminance()
    val lighter = max(a, b)
    val darker = if (lighter == a) b else a
    return (lighter + 0.05) / (darker + 0.05)
}

fun contrastContentColor(background: Color): Color =
    if (wcagContrast(background, Color.White) >= wcagContrast(background, Color.Black)) {
        Color.White
    } else {
        Color.Black
    }

fun bestContentColor(background: Color): Pair<Color, Color> {
    val lum = background.luminance()
    val whiteContrast = 1.05 / (lum + 0.05)
    val blackContrast = (lum + 0.05) / 0.05
    val useWhite = whiteContrast >= blackContrast
    val content = if (useWhite) Color.White else Color.Black
    if (max(whiteContrast, blackContrast) >= MIN_CONTRAST) {
        return background to content
    }

    val hct = Hct.fromInt(background.toArgb())
    var lo = if (useWhite) 0.0 else hct.tone
    var hi = if (useWhite) hct.tone else 100.0
    var best = background
    repeat(24) {
        val mid = (lo + hi) / 2.0
        val candidate = Color(Hct.from(hct.hue, hct.chroma, mid).toInt())
        val contrast = wcagContrast(candidate, content)
        if (contrast >= MIN_CONTRAST) {
            best = candidate
            if (useWhite) lo = mid else hi = mid
        } else {
            if (useWhite) hi = mid else lo = mid
        }
    }
    return best to content
}