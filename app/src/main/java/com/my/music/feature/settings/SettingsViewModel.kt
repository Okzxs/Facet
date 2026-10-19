package com.my.music.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана настроек: оформление (динамические цвета, акцент).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings(),
        )

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
}
