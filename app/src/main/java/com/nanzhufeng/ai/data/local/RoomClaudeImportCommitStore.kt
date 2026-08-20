package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ClaudeConversationCommitStore
import com.nanzhufeng.ai.domain.ClaudeExportCandidate
import com.nanzhufeng.ai.domain.ClaudeImportCommitResult
import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRole
import java.time.Instant
import java.util.concurrent.Callable

/** One transaction writes remapped conversation facts plus Claude-only receipt and provenance. */
class RoomClaudeImportCommitStore(private val database: NanfengAiDatabase, private val conversations: RoomConversationRepository) : ClaudeConversationCommitStore {
    override fun commit(candidate: ClaudeExportCandidate, packageHash: String, importedAt: Instant): ClaudeImportCommitResult = runCatching {
        database.runInTransaction(Callable<ClaudeImportCommitResult> {
            val dao = database.claudeExportImportTaskDao()
            dao.receipt(candidate.sourceConversationId, packageHash)?.let { receipt ->
                return@Callable if (receipt.contentHash == candidate.contentHash) ClaudeImportCommitResult.Replayed(ConversationId(receipt.conversationId)) else ClaudeImportCommitResult.ConflictReimport
            }
            val saved = conversations.persistInExistingTransaction(candidate.toSnapshot())
            dao.insertProvenance(ClaudeImportProvenanceEntity(saved.conversation.id.value, candidate.sourceConversationId, packageHash, candidate.contentHash, importedAt.toEpochMilli(), "claude-export-json", 1, null))
            dao.insertReceipt(ClaudeImportReceiptEntity(candidate.sourceConversationId, packageHash, saved.conversation.id.value, candidate.contentHash, importedAt.toEpochMilli()))
            ClaudeImportCommitResult.Created(saved.conversation.id)
        })
    }.getOrElse { ClaudeImportCommitResult.Failed }

    private fun ClaudeExportCandidate.toSnapshot(): ConversationSnapshot {
        val conversationId = ConversationId.new()
        val ids = messages.associate { it.sourceId to MessageNodeId.new() }
        val nodes = messages.map { message ->
            val content = if (message.role == MessageRole.TOOL) listOf(ContentBlock.ToolResult("Claude 导入工具结果", message.text)) else listOf(ContentBlock.Text(message.text))
            MessageNode(ids.getValue(message.sourceId), conversationId, message.parentSourceId?.let(ids::get), message.siblingPosition, message.role, content, message.createdAt)
        }
        val parentIds = nodes.mapNotNull { it.parentMessageId }.toSet()
        val leaf = nodes.filter { it.id !in parentIds }.maxWithOrNull(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value })?.id
        return ConversationSnapshot(Conversation(conversationId, title, null, leaf, createdAt, updatedAt), nodes, ConversationDraft(updatedAt = updatedAt))
    }
}
