package com.facet.music.core.player

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import com.facet.music.core.di.ApplicationScope
import com.facet.music.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerController @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {

    private val lock = Any()
    private var equalizer: Equalizer? = null
    private var boundSession = 0

    init {
        scope.launch {
            combine(
                AudioSessionBridge.sessionId,
                settingsRepository.settings,
            ) { session, settings ->
                Triple(session, settings.equalizerEnabled, settings.equalizerPreset)
            }.collectLatest { (session, enabled, preset) ->
                apply(session, enabled, preset)
            }
        }
    }

    private fun apply(session: Int, enabled: Boolean, presetName: String) {
        synchronized(lock) {
            if (session <= 0) return
            val preset = EqualizerPreset.fromName(presetName)
            var effect = equalizer
            if (effect == null || boundSession != session) {
                releaseLocked()
                effect = runCatching { Equalizer(0, session) }
                    .onFailure { Log.w(TAG, "Эквалайзер недоступен для сессии $session", it) }
                    .getOrNull()
                equalizer = effect
                boundSession = if (effect != null) session else 0
            }
            val eq = effect ?: return
            runCatching {
                if (!enabled) {
                    eq.enabled = false
                    return
                }
                val range = eq.bandLevelRange
                val bands = eq.numberOfBands.toInt()
                for (band in 0 until bands) {
                    val centerHz = eq.getCenterFreq(band.toShort()) / 1_000
                    val gain = EqualizerCurve.gainDb(preset, centerHz)
                    eq.setBandLevel(
                        band.toShort(),
                        EqualizerCurve.levelMillibels(gain, range[0].toInt(), range[1].toInt()),
                    )
                }
                eq.enabled = true
                Log.d(TAG, "Эквалайзер ${preset.name} включён, полос: $bands")
            }.onFailure { Log.w(TAG, "Не удалось применить эквалайзер", it) }
        }
    }

    private fun releaseLocked() {
        runCatching { equalizer?.release() }
        equalizer = null
        boundSession = 0
    }

    private companion object {
        const val TAG = "Equalizer"
    }
}
