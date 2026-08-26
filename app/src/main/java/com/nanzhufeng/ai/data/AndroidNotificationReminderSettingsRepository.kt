package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.NotificationReminderSettings
import com.nanzhufeng.ai.domain.NotificationReminderSettingsRepository

/** App-private durable owner for notification and in-app reminder preferences. */
class AndroidNotificationReminderSettingsRepository(context: Context) : NotificationReminderSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): NotificationReminderSettings = NotificationReminderSettings(
        monitorResultsNotificationEnabled = preferences.getBoolean(MONITOR_RESULTS_NOTIFICATION_ENABLED, true),
        conversationReminderSuggestionsEnabled = preferences.getBoolean(CONVERSATION_REMINDER_SUGGESTIONS_ENABLED, true),
        unreadConversationIndicatorsEnabled = preferences.getBoolean(UNREAD_CONVERSATION_INDICATORS_ENABLED, true),
    )

    override fun save(settings: NotificationReminderSettings): NotificationReminderSettings {
        check(
            preferences.edit()
                .putBoolean(MONITOR_RESULTS_NOTIFICATION_ENABLED, settings.monitorResultsNotificationEnabled)
                .putBoolean(CONVERSATION_REMINDER_SUGGESTIONS_ENABLED, settings.conversationReminderSuggestionsEnabled)
                .putBoolean(UNREAD_CONVERSATION_INDICATORS_ENABLED, settings.unreadConversationIndicatorsEnabled)
                .commit(),
        ) { "通知与提醒设置无法写入本机。" }
        return load()
    }

    private companion object {
        const val FILE = "notification_reminder_settings_v1"
        const val MONITOR_RESULTS_NOTIFICATION_ENABLED = "monitor_results_notification_enabled"
        const val CONVERSATION_REMINDER_SUGGESTIONS_ENABLED = "conversation_reminder_suggestions_enabled"
        const val UNREAD_CONVERSATION_INDICATORS_ENABLED = "unread_conversation_indicators_enabled"
    }
}
