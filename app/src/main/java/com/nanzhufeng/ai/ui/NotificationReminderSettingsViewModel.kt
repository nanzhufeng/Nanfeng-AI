package com.nanzhufeng.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nanzhufeng.ai.domain.LoadNotificationReminderSettingsUseCase
import com.nanzhufeng.ai.domain.NotificationReminderSettings
import com.nanzhufeng.ai.domain.SaveNotificationReminderSettingsUseCase

data class NotificationReminderSettingsUiState(
    val settings: NotificationReminderSettings = NotificationReminderSettings(),
    val notice: String? = null,
    val error: String? = null,
)

/** Switches persist immediately because each setting is an independent user decision. */
class NotificationReminderSettingsViewModel(
    private val load: LoadNotificationReminderSettingsUseCase,
    private val save: SaveNotificationReminderSettingsUseCase,
) : ViewModel() {
    var state by mutableStateOf(NotificationReminderSettingsUiState(settings = load.execute()))
        private set

    fun update(transform: (NotificationReminderSettings) -> NotificationReminderSettings) {
        val next = transform(state.settings)
        val saved = runCatching { save.execute(next) }
        state = saved.fold(
            onSuccess = { NotificationReminderSettingsUiState(settings = it) },
            onFailure = { state.copy(notice = null, error = "保存失败；已有设置保持不变。") },
        )
    }

    class Factory(
        private val load: LoadNotificationReminderSettingsUseCase,
        private val save: SaveNotificationReminderSettingsUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            NotificationReminderSettingsViewModel(load, save) as T
    }
}
