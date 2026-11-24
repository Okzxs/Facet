package com.facet.music.core.ui.theme

enum class FacetPalette {
    DYNAMIC,
    SIGNATURE,
    NIGHT_JAZZ,
    PAPER,
    MONO,
    ;

    companion object {
        fun fromName(raw: String?): FacetPalette =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SIGNATURE
    }
}
