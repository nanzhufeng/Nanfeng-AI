package com.nanzhufeng.ai.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P7FSelectedConversationSyncContractsTest {
    private val owner = File("src/main/java/com/nanzhufeng/ai/data/P7FManualConversationSync.kt").readText()
    private val scheduler = File("src/main/java/com/nanzhufeng/ai/data/P7FSelectedConversationSyncScheduling.kt").readText()
    private val accountUi = File("src/main/java/com/nanzhufeng/ai/ui/P7DAccountSyncUi.kt").readText()

    @Test fun `conversation action directly invokes one explicit conversation`() {
        assertTrue(owner.contains("fun sync(conversationId: ConversationId)"))
        assertTrue(accountUi.contains("manualSync.sync(conversation.id)"))
        assertFalse(accountUi.contains("DirectSyncDialog"))
    }

    @Test fun `periodic owner only revisits successful manual receipts`() {
        assertTrue(owner.contains("manualConversationSyncStateDao().listForAccount(accountRef)"))
        assertTrue(owner.contains("sync(ConversationId(state.conversationId))"))
        assertFalse(owner.contains("listActive("))
        assertFalse(owner.contains("snapshotsForSearch("))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<P7FSelectedConversationSyncWorker>(12, TimeUnit.HOURS)"))
        assertTrue(scheduler.contains("syncPreviouslySelected()"))
    }
}
