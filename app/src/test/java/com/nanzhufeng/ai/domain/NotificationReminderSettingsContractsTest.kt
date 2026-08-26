package com.nanzhufeng.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReminderSettingsContractsTest {
    @Test
    fun defaultsPreserveExistingReminderBehaviourUntilTheUserChangesIt() {
        val settings = NotificationReminderSettings()

        assertTrue(settings.monitorResultsNotificationEnabled)
        assertTrue(settings.conversationReminderSuggestionsEnabled)
        assertTrue(settings.unreadConversationIndicatorsEnabled)
    }

    @Test
    fun eachControlCanBeDisabledWithoutChangingTheOtherOwners() {
        val settings = NotificationReminderSettings().copy(
            monitorResultsNotificationEnabled = false,
            conversationReminderSuggestionsEnabled = false,
        )

        assertFalse(settings.monitorResultsNotificationEnabled)
        assertFalse(settings.conversationReminderSuggestionsEnabled)
        assertTrue(settings.unreadConversationIndicatorsEnabled)
    }
}
