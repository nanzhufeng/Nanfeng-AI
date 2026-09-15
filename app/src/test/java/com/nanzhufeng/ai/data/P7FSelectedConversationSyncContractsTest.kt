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
        assertTrue(owner.contains("syncInternal(ConversationId(state.conversationId), requireExistingSelection = true)"))
        assertFalse(owner.contains("listActive("))
        assertFalse(owner.contains("snapshotsForSearch("))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<P7FSelectedConversationSyncWorker>(12, TimeUnit.HOURS)"))
        assertTrue(scheduler.contains("syncPreviouslySelected()"))
    }

    @Test fun `selected conversation mutations enqueue a coalesced short sync without observing receipts`() {
        assertTrue(scheduler.contains("fun scheduleAfterLocalConversationMutation()"))
        assertTrue(scheduler.contains("setInitialDelay(30, TimeUnit.SECONDS)"))
        assertTrue(scheduler.contains("ExistingWorkPolicy.APPEND_OR_REPLACE"))
        assertTrue(scheduler.contains("\"conversations\", \"message_nodes\", \"message_content_blocks\""))
        assertFalse(scheduler.contains("manual_conversation_sync_state\""))
    }

    @Test fun `portable cloud copy keeps the completed connected tree only`() {
        assertTrue(owner.contains("node.deliveryState != com.nanzhufeng.ai.domain.MessageDeliveryState.COMPLETE"))
        assertTrue(owner.contains("textOnlyContent = node.content.filterIsInstance<ContentBlock.Text>()"))
        assertTrue(owner.contains("没有可同步的已完成消息。"))
        assertTrue(owner.contains("local source snapshot"))
        assertTrue(owner.contains("remains untouched for retry and diagnostics"))
    }

    @Test fun `local attachments and tool blocks never block the portable text tree`() {
        assertFalse(owner.contains("node.content.any { it !is ContentBlock.Text }"))
        assertTrue(owner.contains("portableCompletedTextNodes(snapshot.nodes)"))
    }

    @Test fun `cancel sync deletes only cloud copy and local receipt`() {
        assertTrue(owner.contains("fun cancelSync(conversationId: ConversationId)"))
        assertTrue(owner.contains("gateway.delete(receipt.documentId, receipt.remoteRevision)"))
        assertTrue(owner.contains("manualConversationSyncStateDao().delete(accountRef, conversationId.value)"))
        assertTrue(owner.contains("local conversation is deliberately never deleted here"))
    }

    @Test fun `local deletion cleans its selected cloud copy before it can be restored again`() {
        assertTrue(owner.contains("if (local == null || local.conversation.deletedAt != null)"))
        assertTrue(owner.contains("cancelDeletedConversationCloudCopy(accountRef, state)"))
        assertTrue(owner.contains("normal local deletion must not leave an orphan"))
        assertTrue(owner.contains("manualConversationSyncStateDao().delete(accountRef, receipt.conversationId)"))
    }

    @Test fun `background rejection is retried instead of being silently acknowledged`() {
        assertTrue(scheduler.contains("results.any { it is P7FManualConversationSyncResult.Rejected }"))
        assertTrue(scheduler.contains("Result.retry()"))
    }
}
