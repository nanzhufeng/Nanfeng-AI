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
    val error: String? = null,
)

/** The screen owns only editable settings state; normal-chat execution reads the saved owner. */
class AssistantExperienceSettingsViewModel(
    private val load: LoadAssistantExperienceSettingsUseCase,
    private val save: SaveAssistantExperienceSettingsUseCase,
    private val onAutoHistoryKnowledgeChanged: (Boolean) -> Unit = {},
) : ViewModel() {
    var state by mutableStateOf(AssistantExperienceSettingsUiState(settings = load.execute()))
        private set

    fun update(transform: (AssistantExperienceSettings) -> AssistantExperienceSettings) {
        val next = runCatching { transform(state.settings) }.getOrElse {
            state = state.copy(error = "个性化内容超过可保存范围。")
            return
        }
        val saved = runCatching { save.execute(next) }
        state = saved.fold(
            onSuccess = {
                if (it.historyLibraryEnabled != state.settings.historyLibraryEnabled) onAutoHistoryKnowledgeChanged(it.historyLibraryEnabled)
                // Every successful personalization edit already updates its visible control.
                // Do not obscure the page with a redundant success dialog; failures remain explicit.
                state.copy(settings = it, error = null)
            },
            onFailure = { state.copy(error = "保存失败；已有设置保持不变。") },
        )
    }

    class Factory(
        private val load: LoadAssistantExperienceSettingsUseCase,
        private val save: SaveAssistantExperienceSettingsUseCase,
        private val onAutoHistoryKnowledgeChanged: (Boolean) -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AssistantExperienceSettingsViewModel(load, save, onAutoHistoryKnowledgeChanged) as T
    }
}
