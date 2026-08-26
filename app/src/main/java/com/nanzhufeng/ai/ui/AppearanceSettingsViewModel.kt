package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nanzhufeng.ai.domain.AppearanceSettings
import com.nanzhufeng.ai.domain.LoadAppearanceSettingsUseCase
import com.nanzhufeng.ai.domain.SaveAppearanceSettingsUseCase

data class AppearanceSettingsUiState(
    val settings: AppearanceSettings = AppearanceSettings(),
    val error: String? = null,
)

class AppearanceSettingsViewModel(
    private val load: LoadAppearanceSettingsUseCase,
    private val save: SaveAppearanceSettingsUseCase,
) : ViewModel() {
    var state by mutableStateOf(AppearanceSettingsUiState(load.execute()))
        private set

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val saved = runCatching { save.execute(transform(state.settings)) }
        state = saved.fold(
            onSuccess = { AppearanceSettingsUiState(it) },
            onFailure = { state.copy(error = "保存失败；已有外观保持不变。") },
        )
    }

    class Factory(
        private val load: LoadAppearanceSettingsUseCase,
        private val save: SaveAppearanceSettingsUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppearanceSettingsViewModel(load, save) as T
    }
}
