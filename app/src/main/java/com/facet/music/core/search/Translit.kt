package com.facet.music.core.search

import java.util.Locale

/** Простая транслитерация для второго поискового прохода. */
object Translit {

    private val cyrToLat = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    private val latToCyr = mapOf(
        "shch" to "щ", "sch" to "щ", "zh" to "ж", "kh" to "х", "ts" to "ц",
        "ch" to "ч", "sh" to "ш", "yu" to "ю", "ya" to "я", "yo" to "ё",
        "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д", "e" to "е",
        "z" to "з", "i" to "и", "y" to "й", "k" to "к", "l" to "л", "m" to "м",
        "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с", "t" to "т",
        "u" to "у", "f" to "ф", "h" to "х", "c" to "к", "j" to "дж", "w" to "в",
        "x" to "кс", "q" to "к",
    )

    fun hasCyrillic(value: String): Boolean = value.any { it in 'а'..'я' || it == 'ё' || it == 'Ё' }

    fun hasLatin(value: String): Boolean = value.any { it in 'a'..'z' || it in 'A'..'Z' }

    fun toLatin(value: String): String = buildString {
        value.lowercase(Locale.ROOT).forEach { char -> append(cyrToLat[char] ?: char) }
    }

    fun toCyrillic(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return buildString(lower.length) {
            var index = 0
            while (index < lower.length) {
                val three = lower.substring(index, (index + 4).coerceAtMost(lower.length))
                val match = latToCyr.keys
                    .filter { three.startsWith(it) }
                    .maxByOrNull { it.length }
                if (match != null) {
                    append(latToCyr[match])
                    index += match.length
                } else {
                    append(lower[index])
                    index++
                }
            }
        }
    }

    /** Второй вариант запроса: кириллица → латиница и наоборот. */
    fun alternate(query: String): String? {
        val value = query.trim()
        if (value.length < 2) return null
        return when {
            hasCyrillic(value) -> toLatin(value).takeIf { it.isNotBlank() && it != value }
            hasLatin(value) -> toCyrillic(value).takeIf { it.isNotBlank() && it != value }
            else -> null
        }
    }
}
