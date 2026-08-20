package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.NanfengKnowledgeConversationCommitStore
import com.nanzhufeng.ai.domain.NanfengKnowledgeExportCandidate
import com.nanzhufeng.ai.domain.NanfengKnowledgeExportMessage
import com.nanzhufeng.ai.domain.NanfengKnowledgeExportParseFailure
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportAsset
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportCommitResult
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportItem
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportItemId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportItemStatus
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTask
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTaskId
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTaskRepository
import com.nanzhufeng.ai.domain.NanfengKnowledgeImportTaskStatus
import java.time.Instant
import java.util.concurrent.Callable

/** P6-J persists only the bounded inert candidate representation, never a URI, path or source record. */
class RoomNanfengKnowledgeImportTaskRepository(private val database: NanfengAiDatabase) : NanfengKnowledgeImportTaskRepository {
    override fun save(task: NanfengKnowledgeImportTask): NanfengKnowledgeImportTask = database.runInTransaction(Callable {
        val dao = database.nanfengKnowledgeExportImportTaskDao(); val asset = task.asset
        dao.upsertTask(NanfengKnowledgeExportImportTaskEntity(task.id.value, task.status.name, asset?.storageKey, asset?.mimeType, asset?.displayName, asset?.byteCount, asset?.packageHash, task.failure?.name, task.retryCount, task.createdAt.toEpochMilli(), task.updatedAt.toEpochMilli()))
        dao.clearMessages(task.id.value); dao.clearItems(task.id.value)
        dao.upsertItems(task.items.map { item -> val c = item.candidate; NanfengKnowledgeExportImportItemEntity(task.id.value, item.id.value, item.ordinal, c?.sourceConversationId, c?.title, c?.createdAt?.toEpochMilli(), c?.updatedAt?.toEpochMilli(), c?.contentHash, item.status.name, item.failure?.name, item.conversationId?.value) })
        dao.upsertMessages(task.items.flatMap { item -> item.candidate?.messages.orEmpty().map { message -> NanfengKnowledgeExportImportMessageEntity(task.id.value, item.id.value, message.sourceId, message.parentSourceId, message.siblingPosition, message.role.name, message.text, message.createdAt.toEpochMilli()) } })
        task
    })
    override fun find(id: NanfengKnowledgeImportTaskId): NanfengKnowledgeImportTask? = database.nanfengKnowledgeExportImportTaskDao().task(id.value)?.toP6jDomain(database.nanfengKnowledgeExportImportTaskDao())
    override fun list(): List<NanfengKnowledgeImportTask> = database.nanfengKnowledgeExportImportTaskDao().all().map { it.toP6jDomain(database.nanfengKnowledgeExportImportTaskDao()) }
}

private fun NanfengKnowledgeExportImportTaskEntity.toP6jDomain(dao: NanfengKnowledgeExportImportTaskDao): NanfengKnowledgeImportTask {
    val taskId = NanfengKnowledgeImportTaskId(id)
    return NanfengKnowledgeImportTask(taskId, NanfengKnowledgeImportTaskStatus.valueOf(status), storageKey?.let { NanfengKnowledgeImportAsset(it, requireNotNull(mimeType), requireNotNull(displayName), requireNotNull(byteCount), requireNotNull(packageHash)) }, failure?.let(NanfengKnowledgeExportParseFailure::valueOf), retryCount, Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), dao.items(id).map { item ->
        val itemId = NanfengKnowledgeImportItemId(item.id)
        val candidate = item.sourceConversationId?.let { source -> NanfengKnowledgeExportCandidate(source, requireNotNull(item.title), Instant.ofEpochMilli(requireNotNull(item.createdAtEpochMs)), Instant.ofEpochMilli(requireNotNull(item.updatedAtEpochMs)), dao.messages(id, item.id).map { message -> NanfengKnowledgeExportMessage(message.sourceMessageId, message.parentSourceMessageId, message.siblingPosition, MessageRole.valueOf(message.role), message.text, Instant.ofEpochMilli(message.createdAtEpochMs)) }, requireNotNull(item.contentHash)) }
        NanfengKnowledgeImportItem(itemId, taskId, item.ordinal, candidate, NanfengKnowledgeImportItemStatus.valueOf(item.status), item.failure?.let(NanfengKnowledgeExportParseFailure::valueOf), item.conversationId?.let(::ConversationId))
    })
}

/** One Room transaction writes the normal conversation and P6-J-only receipt/provenance together. */
class RoomNanfengKnowledgeImportCommitStore(private val database: NanfengAiDatabase, private val conversations: RoomConversationRepository) : NanfengKnowledgeConversationCommitStore {
    override fun commit(candidate: NanfengKnowledgeExportCandidate, packageHash: String, importedAt: Instant): NanfengKnowledgeImportCommitResult = runCatching {
        database.runInTransaction(Callable<NanfengKnowledgeImportCommitResult> {
            val dao = database.nanfengKnowledgeExportImportTaskDao()
            dao.receipt(candidate.sourceConversationId, packageHash)?.let { receipt ->
                return@Callable if (receipt.contentHash == candidate.contentHash) NanfengKnowledgeImportCommitResult.Replayed(ConversationId(receipt.conversationId)) else NanfengKnowledgeImportCommitResult.ConflictReimport
            }
            val saved = conversations.persistInExistingTransaction(candidate.toSnapshot())
            dao.insertProvenance(NanfengKnowledgeImportProvenanceEntity(saved.conversation.id.value, candidate.sourceConversationId, packageHash, candidate.contentHash, importedAt.toEpochMilli(), "nanfeng-knowledge-export-json", 1, null))
            dao.insertReceipt(NanfengKnowledgeImportReceiptEntity(candidate.sourceConversationId, packageHash, saved.conversation.id.value, candidate.contentHash, importedAt.toEpochMilli()))
            NanfengKnowledgeImportCommitResult.Created(saved.conversation.id)
        })
    }.getOrElse { NanfengKnowledgeImportCommitResult.Failed }

    private fun NanfengKnowledgeExportCandidate.toSnapshot(): ConversationSnapshot {
        val conversationId = ConversationId.new(); val ids = messages.associate { it.sourceId to MessageNodeId.new() }
        val nodes = messages.map { message ->
            val blocks = if (message.role == MessageRole.TOOL) listOf(ContentBlock.ToolResult("南枫知识库导入工具结果", message.text)) else listOf(ContentBlock.Text(message.text))
            MessageNode(ids.getValue(message.sourceId), conversationId, message.parentSourceId?.let(ids::get), message.siblingPosition, message.role, blocks, message.createdAt)
        }
        val parents = nodes.mapNotNull { it.parentMessageId }.toSet(); val leaf = nodes.filter { it.id !in parents }.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })?.id
        return ConversationSnapshot(Conversation(conversationId, title, null, leaf, createdAt, updatedAt), nodes, ConversationDraft(updatedAt = updatedAt))
    }
}
