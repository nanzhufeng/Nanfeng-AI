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
import com.nanzhufeng.ai.domain.semanticContentHash
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
            val existing = dao.provenanceForSource(candidate.sourceConversationId)
            if (existing.size > 1) return@Callable conflict(dao, task, item, importedAt)
            val previous = existing.singleOrNull()
            when {
                previous == null -> {
                    val imported = candidate.toImportedSnapshot()
                    val saved = conversations.persistInExistingTransaction(imported.snapshot)
                    persistProvenance(dao, task, item, candidate, saved.conversation.id, imported.sourceMessageIds, importedAt)
                    P6KZipCommitResult.Created(saved.conversation.id)
                }
                previous.contentHash == candidate.contentHash -> {
                    val conversationId = ConversationId(previous.conversationId)
                    if (conversations.findById(conversationId) == null) return@Callable conflict(dao, task, item, importedAt)
                    persistProvenance(dao, task, item, candidate, conversationId, emptyMap(), importedAt)
                    P6KZipCommitResult.Replayed(conversationId)
                }
                else -> {
                    val merged = mergeAppendOnly(previous, candidate, dao) ?: return@Callable conflict(dao, task, item, importedAt)
                    val saved = conversations.persistInExistingTransaction(merged.snapshot)
                    persistProvenance(dao, task, item, candidate, saved.conversation.id, merged.newSourceMessageIds, importedAt)
                    P6KZipCommitResult.Merged(saved.conversation.id, merged.newSourceMessageIds.size)
                }
            }
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
                dao.deleteMessageProvenanceForConversation(provenance.conversationId)
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

    private fun conflict(dao: P6KZipImportTaskDao, task: P6KZipImportTask, item: P6KZipImportItem, at: Instant): P6KZipCommitResult {
        dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.FAILED.name, null, "CONFLICT_REIMPORT")
        dao.refreshTerminalTaskStatus(task.id.value, at.toEpochMilli())
        return P6KZipCommitResult.ConflictReimport
    }

    private fun persistProvenance(
        dao: P6KZipImportTaskDao,
        task: P6KZipImportTask,
        item: P6KZipImportItem,
        candidate: ChatGptImportCandidate,
        conversationId: ConversationId,
        newSourceMessageIds: Map<String, MessageNodeId>,
        importedAt: Instant,
    ) {
        dao.insertProvenance(P6KZipImportProvenanceEntity(conversationId.value, task.id.value, item.id.value, candidate.sourceConversationId, task.packageHash, candidate.contentHash, importedAt.toEpochMilli(), task.provider.adapterId(), 2))
        if (newSourceMessageIds.isNotEmpty()) dao.insertMessageProvenance(candidate.messages.mapNotNull { message ->
            newSourceMessageIds[message.sourceId]?.let { id -> P6KZipImportMessageProvenanceEntity(conversationId.value, message.sourceId, id.value, message.semanticContentHash()) }
        })
        dao.insertReceipt(P6KZipImportReceiptEntity(candidate.sourceConversationId, task.packageHash, task.id.value, item.id.value, conversationId.value, candidate.contentHash, importedAt.toEpochMilli()))
        check(dao.decideItem(task.id.value, item.id.value, P6KZipItemStatus.CONFIRMED.name, conversationId.value, null) == 1)
        dao.refreshTerminalTaskStatus(task.id.value, importedAt.toEpochMilli())
    }

    /** Only additive exports can extend an existing imported conversation.  Edited/deleted source
     * messages and legacy batches without exact source-node bindings stay visible as conflicts
     * instead of silently overwriting user-visible local history. */
    private fun mergeAppendOnly(previous: P6KZipImportProvenanceEntity, candidate: ChatGptImportCandidate, dao: P6KZipImportTaskDao): ImportedAppendMerge? {
        val conversationId = ConversationId(previous.conversationId)
        val existing = conversations.findById(conversationId) ?: return null
        val origins = dao.messageProvenanceForConversation(conversationId.value)
        if (origins.isEmpty()) return null
        val incoming = candidate.messages.associateBy { it.sourceId }
        val originsBySource = origins.associateBy { it.sourceMessageId }
        if (originsBySource.size != origins.size || !originsBySource.keys.all(incoming::containsKey)) return null
        val existingNodes = existing.nodes.associateBy { it.id.value }
        if (origins.any { origin -> existingNodes[origin.messageId] == null || incoming[origin.sourceMessageId]?.semanticContentHash() != origin.contentHash }) return null

        val sourceNodeIds = origins.associate { it.sourceMessageId to MessageNodeId(it.messageId) }.toMutableMap()
        val siblingPositions = existing.nodes.groupBy { it.parentMessageId?.value }.mapValuesTo(mutableMapOf()) { (_, nodes) -> nodes.maxOfOrNull { it.siblingPosition + 1 } ?: 0 }
        val appended = mutableListOf<MessageNode>()
        candidate.messages.filter { it.sourceId !in sourceNodeIds }.forEach { message ->
            val parent = message.parentSourceId?.let(sourceNodeIds::get)
            val parentKey = parent?.value
            val position = siblingPositions.getOrDefault(parentKey, 0).also { siblingPositions[parentKey] = it + 1 }
            val id = MessageNodeId.new(); sourceNodeIds[message.sourceId] = id
            val content = if (message.role == MessageRole.TOOL) listOf(ContentBlock.ToolResult("ChatGPT 导入工具结果", message.text)) else listOf(ContentBlock.Text(message.text))
            appended += MessageNode(id, conversationId, parent, position, message.role, content, message.createdAt)
        }
        if (appended.isEmpty()) return null
        val nodes = existing.nodes + appended
        val parentIds = nodes.mapNotNull { it.parentMessageId }.toSet()
        val leaf = nodes.filter { it.id !in parentIds }.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })?.id
        val updatedAt = if (candidate.updatedAt.isAfter(existing.conversation.updatedAt)) candidate.updatedAt else existing.conversation.updatedAt
        return ImportedAppendMerge(existing.copy(conversation = existing.conversation.copy(currentLeafMessageId = leaf, updatedAt = updatedAt, revision = existing.conversation.revision + 1), nodes = nodes), appended.associate { node -> candidate.messages.first { sourceNodeIds[it.sourceId] == node.id }.sourceId to node.id })
    }

    private fun ChatGptImportCandidate.toImportedSnapshot(): ImportedSnapshot {
        val conversationId = ConversationId.new(); val ids = messages.associate { it.sourceId to MessageNodeId.new() }; val siblingPositions = mutableMapOf<String?, Int>()
        val nodes = messages.map { message ->
            val parent = message.parentSourceId?.let(ids::get); val position = siblingPositions.getOrDefault(message.parentSourceId, 0).also { siblingPositions[message.parentSourceId] = it + 1 }
            val content = if (message.role == MessageRole.TOOL) listOf(ContentBlock.ToolResult("ChatGPT 导入工具结果", message.text)) else listOf(ContentBlock.Text(message.text))
            MessageNode(ids.getValue(message.sourceId), conversationId, parent, position, message.role, content, message.createdAt)
        }
        val parentIds = nodes.mapNotNull { it.parentMessageId }.toSet(); val leaf = nodes.filter { it.id !in parentIds }.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })?.id
        return ImportedSnapshot(ConversationSnapshot(Conversation(conversationId, title, null, leaf, createdAt, updatedAt), nodes, ConversationDraft(updatedAt = updatedAt)), ids)
    }

    private data class ImportedSnapshot(val snapshot: ConversationSnapshot, val sourceMessageIds: Map<String, MessageNodeId>)
    private data class ImportedAppendMerge(val snapshot: ConversationSnapshot, val newSourceMessageIds: Map<String, MessageNodeId>)

    private fun ThirdPartyZipProvider.adapterId() = when (this) {
        ThirdPartyZipProvider.CHATGPT -> "chatgpt-zip-conversations-json"
        ThirdPartyZipProvider.CLAUDE -> "claude-zip-conversations-json"
    }
}
