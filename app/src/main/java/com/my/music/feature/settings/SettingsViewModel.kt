package com.my.music.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.music.data.repository.AppSettings
import com.my.music.data.repository.SettingsRepository
import com.my.music.data.repository.TasteRepository
import com.my.music.data.repository.ThemeMode
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

    /**
     * Готовый акцент из палитры. Пишется и в legacy-поле для Android ниже 12,
     * и в активный [AppSettings.customAccentColor] — только он перекрывает
     * Dynamic Color на Android 12+, поэтому без второй записи кружки «не работали».
     */
    fun setAccentColor(color: Color) {
        val argb = color.toArgb()
        viewModelScope.launch {
            settingsRepository.setAccentColorArgb(argb)
            settingsRepository.setCustomAccentColor(argb)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    /** Применить собственный цвет; null сбрасывает собственный акцент. */
    fun setCustomAccentColor(color: Color?) {
        viewModelScope.launch {
            settingsRepository.setCustomAccentColor(color?.toArgb())
        }
    }
}
