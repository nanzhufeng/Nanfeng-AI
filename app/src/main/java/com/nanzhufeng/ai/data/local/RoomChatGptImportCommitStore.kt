package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ChatGptConversationCommitStore
import com.nanzhufeng.ai.domain.ChatGptImportCandidate
import com.nanzhufeng.ai.domain.ChatGptImportCommitResult
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

/** One Room transaction writes the remapped Conversation tree, provenance, and replay receipt. */
class RoomChatGptImportCommitStore(private val database: NanfengAiDatabase, private val conversations: RoomConversationRepository) : ChatGptConversationCommitStore {
    override fun commit(candidate: ChatGptImportCandidate, packageHash: String, importedAt: Instant): ChatGptImportCommitResult = runCatching {
        database.runInTransaction(Callable<ChatGptImportCommitResult> {
            val dao = database.chatGptExportImportTaskDao()
            dao.receipt(candidate.sourceConversationId, packageHash)?.let { receipt ->
                return@Callable if (receipt.contentHash == candidate.contentHash) ChatGptImportCommitResult.Replayed(ConversationId(receipt.conversationId)) else ChatGptImportCommitResult.ConflictReimport
            }
            val snapshot = candidate.toSnapshot()
            val saved = conversations.persistInExistingTransaction(snapshot)
            dao.insertProvenance(ChatGptImportProvenanceEntity(saved.conversation.id.value, candidate.sourceConversationId, packageHash, candidate.contentHash, importedAt.toEpochMilli(), "chatgpt-export-json", 1, null))
            dao.insertReceipt(ChatGptImportReceiptEntity(candidate.sourceConversationId, packageHash, saved.conversation.id.value, candidate.contentHash, importedAt.toEpochMilli()))
            ChatGptImportCommitResult.Created(saved.conversation.id)
        })
    }.getOrElse { ChatGptImportCommitResult.Failed("导入会话未写入，本地数据保持不变。") }

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
}
