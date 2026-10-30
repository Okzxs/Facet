package com.facet.music.core.search

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * История поисковых запросов: последние [MAX_ENTRIES] уникальных запросов.
 *
 * Нужна, чтобы после очистки поля можно было вернуться к прошлому запросу
 * одним тапом (как в крупных клиентах). Хранится в отдельном
 * SharedPreferences-файле и не попадает в основную таблицу настроек.
 */
@Singleton
class SearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recent(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** Добавляет запрос в начало истории, убирая дубликаты и лишние пробелы. */
    fun add(query: String) {
        val normalized = query.trim().replace(WHITESPACE, " ")
        if (normalized.length < MIN_LENGTH) return

        val current = recent()
        val updated = buildList {
            add(normalized)
            current.forEach { existing ->
                if (!existing.equals(normalized, ignoreCase = true)) add(existing)
            }
        }.take(MAX_ENTRIES)

        prefs.edit {
            putString(
                KEY_HISTORY,
                json.encodeToString(ListSerializer(String.serializer()), updated),
            )
        }
    }

    fun clear() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    /** Совпадение по началу строки — для подсказок под полем ввода. */
    fun matches(prefix: String): List<String> {
        val needle = prefix.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return recent()
        return recent().filter { it.lowercase(Locale.ROOT).startsWith(needle) }
    }

    private companion object {
        const val PREFS_NAME = "search_history"
        const val KEY_HISTORY = "queries"
        const val MAX_ENTRIES = 8
        const val MIN_LENGTH = 2
        val WHITESPACE = Regex("\\s+")
    }
}
