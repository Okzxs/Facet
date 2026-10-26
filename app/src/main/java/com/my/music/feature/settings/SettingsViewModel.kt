package com.my.music.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import com.my.music.data.repository.TasteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана настроек: оформление (динамические цвета, акцент).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val tasteRepository: TasteRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

    private val _russianPriority = MutableStateFlow(true)
    val russianPriority: StateFlow<Boolean> = _russianPriority.asStateFlow()

    init {
        viewModelScope.launch {
            _russianPriority.value = tasteRepository.isRussianPriority()
        }
    }

    fun setRussianPriority(enabled: Boolean) {
        _russianPriority.value = enabled
        viewModelScope.launch {
            tasteRepository.setRussianPriority(enabled)
        }
    }

    fun setDynamicCoverColors(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicCoverColors(enabled)
        }
    }

    fun setAccentColor(color: Color) {
        viewModelScope.launch {
            settingsRepository.setAccentColorArgb(color.toArgb())
        }
    }

    /** Калибровка задержки волны плеера в миллисекундах. */
    fun setVisualizerDelayMs(delayMs: Int) {
        viewModelScope.launch {
            settingsRepository.setVisualizerDelayMs(delayMs)
        }
    }
}
