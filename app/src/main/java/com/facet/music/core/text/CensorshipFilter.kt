package com.facet.music.core.text

import java.util.Locale

enum class CensorshipVerdict {
    CENSORED,
    ORIGINAL,
    UNKNOWN,
}

object CensorshipSettings {
    @Volatile
    var preferUncensored: Boolean = true
}

object CensorshipFilter {

    private val censoredPatterns = listOf(
        Regex("""\((?:clean|clean version|clean edit)\)"""),
        Regex("""\[(?:clean|clean version|clean edit)\]"""),
        Regex("""-\s*clean\b"""),
        Regex("""\bclean (?:version|edit|mix|audio|lyrics)\b"""),
        Regex("""\bcensored\b"""),
        Regex("""\bbl+e+p\b"""),
        Regex("""\bmuted (?:version|words)\b"""),
        Regex("""\bedited for radio\b"""),
        Regex("""\bradio edit clean\b"""),
        Regex("""цензур(?!ы)"""),
        Regex("""без мата"""),
        Regex("""без нецензурн"""),
        Regex("""без матерн"""),
    )

    private val originalPatterns = listOf(
        Regex("""\bexplicit\b"""),
        Regex("""\buncensored\b"""),
        Regex("""\buncesored\b"""),
        Regex("""\bdirty (?:version|edit)\b"""),
        Regex("""\btrue version\b"""),
        Regex("""\boriginal version\b"""),
        Regex("""\bog version\b"""),
        Regex("""\boriginal (?:mix|upload)\b"""),
        Regex("""без цензуры"""),
        Regex("""без цензур\b"""),
    )

    fun verdict(title: String, description: String? = null): CensorshipVerdict {
        val haystack = normalize("$title ${description.orEmpty()}")
        if (haystack.isBlank()) return CensorshipVerdict.UNKNOWN
        if (censoredPatterns.any { it.containsMatchIn(haystack) }) {
            return CensorshipVerdict.CENSORED
        }
        if (originalPatterns.any { it.containsMatchIn(haystack) }) {
            return CensorshipVerdict.ORIGINAL
        }
        return CensorshipVerdict.UNKNOWN
    }

    fun isCensored(title: String, description: String? = null): Boolean =
        verdict(title, description) == CensorshipVerdict.CENSORED

    fun scoreDelta(title: String, description: String? = null): Double {
        if (!CensorshipSettings.preferUncensored) return 0.0
        return when (verdict(title, description)) {
            CensorshipVerdict.CENSORED -> -CENSORED_PENALTY
            CensorshipVerdict.ORIGINAL -> ORIGINAL_BONUS
            CensorshipVerdict.UNKNOWN -> 0.0
        }
    }

    const val CENSORED_PENALTY = 4.0
    const val ORIGINAL_BONUS = 0.35

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
}
