package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ChatGptImportCandidate
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationManagementAction
import com.nanzhufeng.ai.domain.ConversationManagementDomain
import com.nanzhufeng.ai.domain.ConversationManagementIntent
import com.nanzhufeng.ai.domain.ConversationManagementIntentId
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.P6KZipCommitResult
import com.nanzhufeng.ai.domain.P6KZipImportCommitStore
import com.nanzhufeng.ai.domain.P6KZipImportItem
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipItemStatus
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.time.Instant
import java.util.concurrent.Callable

/**
 * K2 reuses RoomConversationRepository as the only Conversation/Message Tree writer.
 * This owner adds only ZIP-specific receipt/provenance and the candidate decision in that same transaction.
 */
class RoomP6KZipImportCommitStore(
    private val database: NanfengAiDatabase,
    private val conversations: RoomConversationRepository,
) : P6KZipImportCommitStore {
    override fun confirm(task: P6KZipImportTask, item: P6KZipImportItem, importedAt: Instant): P6KZipCommitResult = runCatching {
        requireNotNull(item.candidate)
        database.runInTransaction(Callable<P6KZipCommitResult> {
            val dao = database.p6kZipImportTaskDao(); val candidate = requireNotNull(item.candidate)
            dao.receipt(candidate.sourceConversationId, task.packageHash)?.let { receipt ->
                if (receipt.contentHash != candidate.contentHash) {
                    dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.FAILED.name, null, "CONFLICT_REIMPORT")
                    dao.refreshTerminalTaskStatus(task.id.value, importedAt.toEpochMilli())
                    return@Callable P6KZipCommitResult.ConflictReimport
                }
                dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.CONFIRMED.name, receipt.conversationId, null)
                dao.refreshTerminalTaskStatus(task.id.value, importedAt.toEpochMilli())
                return@Callable P6KZipCommitResult.Replayed(ConversationId(receipt.conversationId))
            }
            val saved = conversations.persistInExistingTransaction(candidate.toSnapshot())
            dao.insertProvenance(P6KZipImportProvenanceEntity(saved.conversation.id.value, task.id.value, item.id.value, candidate.sourceConversationId, task.packageHash, candidate.contentHash, importedAt.toEpochMilli(), task.provider.adapterId(), 1))
            dao.insertReceipt(P6KZipImportReceiptEntity(candidate.sourceConversationId, task.packageHash, task.id.value, item.id.value, saved.conversation.id.value, candidate.contentHash, importedAt.toEpochMilli()))
            check(dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.CONFIRMED.name, saved.conversation.id.value, null) == 1)
            dao.refreshTerminalTaskStatus(task.id.value, importedAt.toEpochMilli())
            P6KZipCommitResult.Created(saved.conversation.id)
        })
    }.getOrElse {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao(); dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.FAILED.name, null, "COMMIT_FAILED"); dao.refreshTerminalTaskStatus(task.id.value, importedAt.toEpochMilli())
        })
        P6KZipCommitResult.Failed
    }

    override fun skip(task: P6KZipImportTask, item: P6KZipImportItem, at: Instant): Boolean = runCatching {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao()
            val changed = dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.SKIPPED.name, null, null) == 1
            dao.refreshTerminalTaskStatus(task.id.value, at.toEpochMilli()); changed
        })
    }.getOrDefault(false)

    /** Batch deletion is reversible at the Conversation layer: imported conversations are soft-deleted,
     * then receipts/provenance are removed so a later explicit ZIP selection is a fresh import. */
    override fun deleteBatch(task: P6KZipImportTask, at: Instant): Boolean = runCatching {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao()
            dao.provenanceForTask(task.id.value).forEach { provenance ->
                val snapshot = conversations.findById(ConversationId(provenance.conversationId)) ?: return@forEach
                val intent = ConversationManagementIntent(
                    ConversationManagementIntentId.new(), snapshot.conversation.id,
                    ConversationManagementAction.SOFT_DELETE, snapshot.conversation.revision,
                )
                conversations.persistInExistingTransaction(ConversationManagementDomain(java.time.Clock.fixed(at, java.time.ZoneOffset.UTC)).apply(snapshot, intent))
            }
            dao.deleteReceiptsForTask(task.id.value)
            dao.deleteProvenanceForTask(task.id.value)
            true
        })
    }.getOrDefault(false)

    private fun P6KZipImportTaskDao.refreshTerminalTaskStatus(taskId: String, at: Long) {
        val statuses = items(taskId).map { it.status }
        val status = when {
            statuses.all { it in setOf(P6KZipItemStatus.CONFIRMED.name, P6KZipItemStatus.SKIPPED.name, P6KZipItemStatus.FAILED.name) } -> P6KZipTaskStatus.COMPLETED
            statuses.any { it != P6KZipItemStatus.PENDING_CONFIRMATION.name } -> P6KZipTaskStatus.PARTIALLY_COMPLETED
            else -> P6KZipTaskStatus.AWAITING_CONFIRMATION
        }
        updateTaskStatus(taskId, status.name, at)
    }

    private fun ChatGptImportCandidate.toSnapshot(): ConversationSnapshot {
        val conversationId = ConversationId.new(); val ids = messages.associate { it.sourceId to MessageNodeId.new() }; val siblingPositions = mutableMapOf<String?, Int>()
        val nodes = messages.map { message ->
            val parent = message.parentSourceId?.let(ids::get); val position = siblingPositions.getOrDefault(message.parentSourceId, 0).also { siblingPositions[message.parentSourceId] = it + 1 }
            val content = if (message.role == MessageRole.TOOL) listOf(ContentBlock.ToolResult("ChatGPT 导入工具结果", message.text)) else listOf(ContentBlock.Text(message.text))
            MessageNode(ids.getValue(message.sourceId), conversationId, parent, position, message.role, content, message.createdAt)
        }
        val parentIds = nodes.mapNotNull { it.parentMessageId }.toSet(); val leaf = nodes.filter { it.id !in parentIds }.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })?.id
        return ConversationSnapshot(Conversation(conversationId, title, null, leaf, createdAt, updatedAt), nodes, ConversationDraft(updatedAt = updatedAt))
    }

    private fun ThirdPartyZipProvider.adapterId() = when (this) {
        ThirdPartyZipProvider.CHATGPT -> "chatgpt-zip-conversations-json"
        ThirdPartyZipProvider.CLAUDE -> "claude-zip-conversations-json"
    }
}
