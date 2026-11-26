package com.facet.music.core.widget

import android.content.Context

data class WidgetWaveSpec(
    val widthDp: Float,
    val heightDp: Float,
    val accent: Int,
    val track: Int,
)

object WidgetWaveSpecStore {

    private const val PREFS_NAME = "facet_widget_specs"
    private val cache = mutableMapOf<Int, WidgetWaveSpec>()

    fun update(context: Context, appWidgetId: Int, spec: WidgetWaveSpec) {
        synchronized(cache) {
            if (cache[appWidgetId] == spec) return
            cache[appWidgetId] = spec
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(appWidgetId.toString(), encode(spec))
            .apply()
    }

    fun read(context: Context): Map<Int, WidgetWaveSpec> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableMapOf<Int, WidgetWaveSpec>()
        prefs.all.forEach { (key, value) ->
            val id = key.toIntOrNull() ?: return@forEach
            val raw = value as? String ?: return@forEach
            val spec = decode(raw) ?: return@forEach
            result[id] = spec
        }
        synchronized(cache) {
            cache.putAll(result)
        }
        return result
    }

    private fun encode(spec: WidgetWaveSpec): String =
        "${spec.widthDp},${spec.heightDp},${spec.accent},${spec.track}"

    private fun decode(raw: String): WidgetWaveSpec? {
        val parts = raw.split(',')
        if (parts.size != 4) return null
        val width = parts[0].toFloatOrNull() ?: return null
        val height = parts[1].toFloatOrNull() ?: return null
        val accent = parts[2].toIntOrNull() ?: return null
        val track = parts[3].toIntOrNull() ?: return null
        return WidgetWaveSpec(width, height, accent, track)
    }
}
