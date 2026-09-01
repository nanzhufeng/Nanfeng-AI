package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID

const val CONVERSATION_EXPORT_FORMAT = "nanfeng-ai.conversation-export"
const val CONVERSATION_EXPORT_PROTOCOL_VERSION = 1
const val CONVERSATION_EXPORT_PAYLOAD_SCHEMA = "nanfeng-ai.conversation-payload"

data class ConversationExportAttachment(val id: String, val mimeType: String, val byteCount: Long, val sha256: String)
data class ConversationExportBlock(val kind: String, val text: String? = null, val attachment: ConversationExportAttachment? = null, val schemaVersion: Int)
data class ConversationExportMessage(
    val id: String, val parentMessageId: String?, val siblingPosition: Int, val role: String,
    val deliveryState: String, val createdAt: Instant, val revision: Int, val revisesMessageId: String?,
    val invocationId: String?, val schemaVersion: Int, val content: List<ConversationExportBlock>,
)
data class ConversationExportConversation(
    val id: String, val title: String, val createdAt: Instant, val updatedAt: Instant, val archivedAt: Instant?,
    val pinnedAt: Instant?, val currentLeafMessageId: String?, val schemaVersion: Int,
)
data class ConversationExportPayload(
    val schema: String = CONVERSATION_EXPORT_PAYLOAD_SCHEMA,
    val schemaVersion: Int = 1,
    val scope: String = "current-path-only",
    val conversation: ConversationExportConversation,
    val messages: List<ConversationExportMessage>,
)
data class ConversationExportFileEntry(val path: String, val byteCount: Long, val sha256: String)
data class ConversationExportManifest(
    val format: String = CONVERSATION_EXPORT_FORMAT, val protocolVersion: Int = CONVERSATION_EXPORT_PROTOCOL_VERSION,
    val exportId: String, val exportedAt: Instant, val conversationId: String, val scope: String,
    val excludedFields: List<String>, val files: List<ConversationExportFileEntry>,
)
data class VerifiedConversationExport(
    val manifest: ConversationExportManifest, val payload: ConversationExportPayload, val fileName: String,
    val relativeLocation: String, val byteCount: Long, val sha256: String,
)
sealed interface ConversationExportResult {
    data class Success(val export: VerifiedConversationExport) : ConversationExportResult
    data object MissingConversation : ConversationExportResult
    data object Cancelled : ConversationExportResult
    data class Failed(val reason: ConversationExportFailure) : ConversationExportResult
}
enum class ConversationExportFailure { OUTPUT_UNAVAILABLE, WRITE_FAILED, READ_BACK_FAILED, INTEGRITY_MISMATCH, INVALID_PACKAGE }

interface ConversationExportStore {
    fun write(payload: ConversationExportPayload, exportedAt: Instant, cancelled: () -> Boolean = { false }): ConversationExportResult
    fun latestVerified(): ConversationExportResult?
}

object ConversationExportMapper {
    val excludedFields = listOf(
        "apiKey", "authorization", "systemCredentials", "internalPrompt", "providerRawResponse", "providerChunk",
        "internalPath", "presentationCache", "unconfirmedKnowledge", "attachmentBytes", "attachmentDisplayName",
        "hiddenSiblingBranches", "invocationLedgerContent", "usage", "cost",
    )

    fun payload(snapshot: ConversationSnapshot): ConversationExportPayload {
        val conversation = snapshot.conversation
        val path = MessageTree(conversation, snapshot.nodes).contextPath()
        return ConversationExportPayload(
            conversation = ConversationExportConversation(
                conversation.id.value, conversation.title, conversation.createdAt, conversation.updatedAt,
                conversation.archivedAt, conversation.pinnedAt, conversation.currentLeafMessageId?.value, conversation.schemaVersion,
            ),
            messages = path.filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }.map(::message),
        )
    }

    private fun message(node: MessageNode) = ConversationExportMessage(
        node.id.value, node.parentMessageId?.value, node.siblingPosition, node.role.name, node.deliveryState.name,
        node.createdAt, node.revision.revision, node.revision.revisesMessageId?.value, node.invocation?.invocationId?.value,
        node.schemaVersion, node.content.map(::block),
    )

    private fun block(block: ContentBlock): ConversationExportBlock = when (block) {
        is ContentBlock.Text -> ConversationExportBlock("TEXT", text = block.text, schemaVersion = block.schemaVersion)
        is ContentBlock.Reasoning -> ConversationExportBlock("REASONING", text = block.text, schemaVersion = block.schemaVersion)
        is ContentBlock.ProviderToolCall -> error("Provider Tool Call 仅用于同模型续接，不能进入通用会话导出。")
        is ContentBlock.Attachment -> {
            val value = block.attachment
            ConversationExportBlock("ATTACHMENT", attachment = ConversationExportAttachment(value.id.value, value.mimeType, value.byteCount, value.sha256), schemaVersion = block.schemaVersion)
        }
        is ContentBlock.ToolResult -> error("当前路径的 user/assistant 消息不允许 Tool 内容块。")
    }

    fun newExportId(): String = "conversation-export:${UUID.randomUUID()}"
}

class ExportConversationPackageUseCase(
    private val repository: ConversationRepository,
    private val store: ConversationExportStore,
    private val clock: Clock,
) {
    fun execute(id: ConversationId, cancelled: () -> Boolean = { false }): ConversationExportResult {
        if (cancelled()) return ConversationExportResult.Cancelled
        val snapshot = repository.findById(id) ?: return ConversationExportResult.MissingConversation
        return runCatching { store.write(ConversationExportMapper.payload(snapshot), clock.instant(), cancelled) }
            .getOrElse { ConversationExportResult.Failed(ConversationExportFailure.WRITE_FAILED) }
    }
    fun latestVerified(): ConversationExportResult? = store.latestVerified()
}
