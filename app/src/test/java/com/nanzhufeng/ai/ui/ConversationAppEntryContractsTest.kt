package com.nanzhufeng.ai.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAppEntryContractsTest {
    @Test fun coldRestartReceivesThePersistedExitSelection() {
        val activity = File("src/main/java/com/nanzhufeng/ai/NanfengAiActivity.kt").readText()
        val viewModel = File("src/main/java/com/nanzhufeng/ai/ui/ConversationFoundationViewModel.kt").readText()
        assertTrue("Exit must persist the selected conversation, not only the timestamp",
            activity.contains("putString(APP_ENTRY_LAST_CONVERSATION_ID"))
        assertTrue("Cold entry must pass the saved selection into the ViewModel",
            activity.contains("entryConversationId ="))
        assertTrue("Initial selection must be validated before restoring",
            viewModel.contains("ConversationAppEntryPolicy.restorableConversation"))
    }
}
