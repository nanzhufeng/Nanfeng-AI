package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nanzhufeng.ai.domain.AssistantExperienceSettings
import com.nanzhufeng.ai.domain.LoadAssistantExperienceSettingsUseCase
import com.nanzhufeng.ai.domain.SaveAssistantExperienceSettingsUseCase

data class AssistantExperienceSettingsUiState(
    val settings: AssistantExperienceSettings = AssistantExperienceSettings(),
    val notice: String? = null,
    val error: String? = null,
)

/** The screen owns only editable settings state; normal-chat execution reads the saved owner. */
class AssistantExperienceSettingsViewModel(
    private val load: LoadAssistantExperienceSettingsUseCase,
    private val save: SaveAssistantExperienceSettingsUseCase,
) : ViewModel() {
    var state by mutableStateOf(AssistantExperienceSettingsUiState(settings = load.execute()))
        private set

    fun update(transform: (AssistantExperienceSettings) -> AssistantExperienceSettings) {
        val next = runCatching { transform(state.settings) }.getOrElse {
            state = state.copy(notice = null, error = "个性化内容超过可保存范围。")
            return
        }
        val onlyWebSearchToggle = next.copy(webSearchEnabled = state.settings.webSearchEnabled) == state.settings
        val saved = runCatching { save.execute(next) }
        state = saved.fold(
            onSuccess = { state.copy(settings = it, notice = if (onlyWebSearchToggle) null else "已保存在本机。", error = null) },
            onFailure = { state.copy(notice = null, error = "保存失败；已有设置保持不变。") },
        )
    }

    fun clearNotice() {
        state = state.copy(notice = null)
    }

    class Factory(
        private val load: LoadAssistantExperienceSettingsUseCase,
        private val save: SaveAssistantExperienceSettingsUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AssistantExperienceSettingsViewModel(load, save) as T
    }
}
