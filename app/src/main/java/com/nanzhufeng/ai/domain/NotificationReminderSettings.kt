package com.nanzhufeng.ai.domain

/**
 * User-owned controls for the three existing reminder surfaces.
 *
 * Defaults intentionally preserve the behaviour available before these controls were added, so
 * an upgrade never silently disables a monitor result, a useful conversation suggestion, or an
 * unread marker that the user already relied on.
 */
data class NotificationReminderSettings(
    val monitorResultsNotificationEnabled: Boolean = true,
    val conversationReminderSuggestionsEnabled: Boolean = true,
    val unreadConversationIndicatorsEnabled: Boolean = true,
)

interface NotificationReminderSettingsRepository {
    fun load(): NotificationReminderSettings
    fun save(settings: NotificationReminderSettings): NotificationReminderSettings
}

class LoadNotificationReminderSettingsUseCase(private val repository: NotificationReminderSettingsRepository) {
    fun execute(): NotificationReminderSettings = repository.load()
}

class SaveNotificationReminderSettingsUseCase(private val repository: NotificationReminderSettingsRepository) {
    fun execute(settings: NotificationReminderSettings): NotificationReminderSettings = repository.save(settings)
}
