package com.facet.music.core.ui.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppearanceProfileStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun names(): List<String> = prefs.all.keys
        .mapNotNull { key -> key.takeIf { it.startsWith(KEY_PREFIX) } }
        .map { it.removePrefix(KEY_PREFIX) }
        .sorted()

    fun profileJson(name: String): String? = prefs.getString(KEY_PREFIX + name, null)

    fun profile(name: String): AppearanceProfile? =
        profileJson(name)?.let(AppearanceProfile::parse)

    fun save(name: String, profile: AppearanceProfile): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        prefs.edit().putString(KEY_PREFIX + normalized, profile.toJson()).apply()
        return true
    }

    fun delete(name: String) {
        prefs.edit().remove(KEY_PREFIX + name).apply()
    }

    private companion object {
        const val PREFS_NAME = "facet_appearance_profiles"
        const val KEY_PREFIX = "profile:"
    }
}
