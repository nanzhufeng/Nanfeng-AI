package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Instant
import java.util.UUID

@JvmInline
value class ConversationId(val value: String) {
    companion object { fun new(): ConversationId = ConversationId(UUID.randomUUID().toString()) }
}

@JvmInline
value class MessageNodeId(val value: String) {
    companion object { fun new(): MessageNodeId = MessageNodeId(UUID.randomUUID().toString()) }
}

@JvmInline
value class BranchId(val value: String) {
    companion object { fun forLeaf(leaf: MessageNodeId): BranchId = BranchId("leaf:${leaf.value}") }
}

/** P3-A stores these four roles now; Tool execution itself remains a later-stage concern. */
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

enum class MessageDeliveryState { COMPLETE, PARTIAL, FAILED, CANCELLED }

/**
 * A durable content boundary.  CHAT and WORK deliberately share the message tree contract and
 * renderer, but never share a selected conversation, draft, or indexed content stream.
 */
enum class ConversationSurface { CHAT, WORK }

sealed interface ContentBlock {
    val schemaVersion: Int

    data class Text(
        val text: String,
        override val schemaVersion: Int = 1,
    ) : ContentBlock {
        init { require(text.isNotBlank()) { "文本内容块不能为空。" } }
    }

    /**
     * Provider-delivered reasoning is retained separately from the user-facing answer.  It is
     * deliberately excluded from conversation context, search and title generation so a model's
     * scratch work can never become a later prompt instruction or searchable conversation prose.
     */
    data class Reasoning(
        val text: String,
        override val schemaVersion: Int = 1,
    ) : ContentBlock {
        init { require(text.isNotBlank()) { "思考过程内容块不能为空。" } }
    }

    /**
     * Parsed assistant tool-call protocol retained for same-model continuation. This is not a
     * rendered tool result and is never indexed, exported or treated as an executed action.
     */
    data class ProviderToolCall(
        val callId: String?,
        val toolName: String,
        val argumentsJson: String,
        override val schemaVersion: Int = 1,
    ) : ContentBlock {
        init {
            require(callId == null || callId.isNotBlank()) { "Provider Tool Call ID 不能为空白。" }
            require(toolName.isNotBlank()) { "Provider Tool Call 名称不能为空。" }
            require(argumentsJson.isNotBlank()) { "Provider Tool Call 参数不能为空。" }
        }
    }

    /**
     * Conversation content deliberately stores a safe asset reference only.  The Attachment
     * Domain remains the sole owner of the app-private storage key and binary reader.
     */
    data class Attachment(
        val attachment: ConversationAttachmentReference,
        override val schemaVersion: Int = 1,
    ) : ContentBlock {
        /** Compatibility for P3-A/F fixtures; the private storage key is stripped immediately. */
        constructor(attachment: AttachmentReference, schemaVersion: Int = 1) : this(
            attachment.toConversationReference(), schemaVersion,
        )
    }

    /** A future Tool message may keep only its user-readable safe result summary in the conversation. */
    data class ToolResult(
        val toolName: String,
        val safeSummary: String,
        override val schemaVersion: Int = 1,
    ) : ContentBlock {
        init {
            require(toolName.isNotBlank()) { "工具名称不能为空。" }
            require(safeSummary.isNotBlank()) { "工具结果摘要不能为空。" }
        }
    }
}

data class MessageRevision(
    val revision: Int = 1,
    /** The prior node remains immutable; an edit creates this sibling node instead. */
    val revisesMessageId: MessageNodeId? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(revision > 0) { "消息修订号必须为正数。" }
        require((revision == 1) == (revisesMessageId == null)) { "初版与修订来源不一致。" }
    }
}

/** A link only. Usage/cost truth remains owned by Invocation Ledger. */
data class MessageInvocationReference(
    val invocationId: InvocationId,
    val schemaVersion: Int = 1,
)

/** Reserved for P3 streaming recovery; P3-A performs no stream/network work. */
data class MessageCheckpoint(
    val lastPersistedSequence: Long? = null,
    val resumableFromSequence: Long? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(lastPersistedSequence == null || lastPersistedSequence >= 0) { "事件序号不能为负数。" }
        require(resumableFromSequence == null || resumableFromSequence >= 0) { "恢复序号不能为负数。" }
    }
}

data class MessageNode(
    val id: MessageNodeId,
    val conversationId: ConversationId,
    val parentMessageId: MessageNodeId?,
    val siblingPosition: Int,
    val role: MessageRole,
    val content: List<ContentBlock>,
    val createdAt: Instant,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.COMPLETE,
    val revision: MessageRevision = MessageRevision(),
    val invocation: MessageInvocationReference? = null,
    val checkpoint: MessageCheckpoint? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(siblingPosition >= 0) { "同级消息排序必须从 0 开始。" }
        require(content.isNotEmpty() || (role == MessageRole.ASSISTANT && deliveryState != MessageDeliveryState.COMPLETE)) {
            "只有未完成的 assistant 投影可以暂时没有内容块。"
        }
        require(id != parentMessageId) { "消息不能指向自身为父节点。" }
        require(role != MessageRole.TOOL || content.all { it is ContentBlock.ToolResult }) {
            "Tool 消息只能保存安全工具结果块。"
        }
        require(role == MessageRole.TOOL || content.none { it is ContentBlock.ToolResult }) {
            "Tool 结果块只能出现在 Tool 消息中。"
        }
        require(role == MessageRole.ASSISTANT || content.none { it is ContentBlock.ProviderToolCall }) {
            "Provider Tool Call 只能附着在 assistant 消息中。"
        }
        require(deliveryState != MessageDeliveryState.PARTIAL || role == MessageRole.ASSISTANT) {
            "只有 assistant 消息可以保存部分输出。"
        }
        require(invocation == null || role == MessageRole.ASSISTANT || role == MessageRole.TOOL) {
            "Invocation 关联只能附着在 assistant 或 tool 消息。"
        }
    }
}

data class MemorySourceReference(
    val memoryId: String,
    val sourceKind: String,
    val sourceVersion: Int = 1,
) {
    init {
        require(memoryId.isNotBlank() && sourceKind.isNotBlank()) { "记忆来源必须可追踪。" }
        require(sourceVersion > 0) { "记忆来源版本必须为正数。" }
    }
}

data class ConversationSettings(
    val defaultProviderId: ProviderId? = null,
    val defaultModelId: String? = null,
    val harnessId: String? = null,
    val harnessVersion: Int? = null,
    val memorySources: List<MemorySourceReference> = emptyList(),
    val contextPolicyVersion: Int = 1,
    val schemaVersion: Int = 1,
) {
    init {
        require(defaultModelId == null || defaultProviderId != null) { "默认模型必须同时指定 Provider。" }
        require((harnessId == null) == (harnessVersion == null)) { "Harness ID 与版本必须同时存在。" }
        require(harnessVersion == null || harnessVersion > 0) { "Harness 版本必须为正数。" }
        require(contextPolicyVersion > 0) { "上下文策略版本必须为正数。" }
        require(memorySources.map(MemorySourceReference::memoryId).distinct().size == memorySources.size) {
            "同一会话不能重复引用同一记忆来源。"
        }
    }
}

/** A stable, safe Conversation reference. It never contains a URI, storage key, EXIF or bytes. */
data class ConversationAttachmentReference(
    val id: AttachmentId,
    val mimeType: String,
    val displayName: String? = null,
    val byteCount: Long,
    val sha256: String,
    val schemaVersion: Int = 1,
) {
    init {
        require(mimeType in CONVERSATION_PERSISTED_MIME_TYPES) { "对话附件类型不受支持。" }
        require(byteCount in 1..CONVERSATION_PERSISTED_ATTACHMENT_MAX_BYTES) { "对话附件大小不受支持。" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "对话附件缺少完整性摘要。" }
        require(displayName?.contains("content://") != true && displayName?.startsWith('/') != true) { "附件显示名不能包含路径。" }
    }
}

fun AttachmentReference.toConversationReference(): ConversationAttachmentReference = ConversationAttachmentReference(
    id = id,
    mimeType = mimeType,
    displayName = displayName,
    byteCount = requireNotNull(byteCount) { "私有附件缺少大小。" },
    sha256 = requireNotNull(sha256) { "私有附件缺少摘要。" },
)

const val CONVERSATION_ATTACHMENT_MAX_COUNT = 4
const val CONVERSATION_ATTACHMENT_MAX_BYTES = 20L * 1024L * 1024L
const val CONVERSATION_ATTACHMENT_MAX_TOTAL_BYTES = 40L * 1024L * 1024L
const val CONVERSATION_PERSISTED_ATTACHMENT_MAX_BYTES = 256L * 1024L * 1024L
const val CONVERSATION_ATTACHMENT_MAX_SOURCE_PIXELS = 40_000_000L
val CONVERSATION_ALLOWED_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
/** P6-F2-D is deliberately MP4-only: the platform decoders and magic gate stay small and auditable. */
val CONVERSATION_ALLOWED_VIDEO_MIME_TYPES = setOf("video/mp4")
/** P6-F2-E keeps audio small and locally decodable; no streaming or arbitrary codec fallback. */
val CONVERSATION_ALLOWED_AUDIO_MIME_TYPES = setOf("audio/mpeg", "audio/wav", "audio/mp4")
/** Local viewers stay inert: OOXML is text-extracted, ZIP is browsed entry-by-entry, and markup is
 * displayed as source rather than executed. */
val CONVERSATION_ALLOWED_DOCUMENT_MIME_TYPES = setOf(
    "application/pdf",
    "text/plain", "text/markdown", "application/json", "text/csv",
    "application/xml", "text/xml", "application/x-yaml", "text/yaml", "text/html",
    DOCX_MIME_TYPE, XLSX_MIME_TYPE, PPTX_MIME_TYPE,
    "application/zip",
)
val CONVERSATION_ALLOWED_MIME_TYPES = CONVERSATION_ALLOWED_IMAGE_MIME_TYPES + CONVERSATION_ALLOWED_VIDEO_MIME_TYPES + CONVERSATION_ALLOWED_AUDIO_MIME_TYPES + CONVERSATION_ALLOWED_DOCUMENT_MIME_TYPES
val CONVERSATION_PERSISTED_MIME_TYPES = CONVERSATION_ALLOWED_MIME_TYPES + setOf(
    "application/octet-stream",
)

data class ConversationDraft(
    val text: String = "",
    val attachments: List<ConversationAttachmentReference> = emptyList(),
    val updatedAt: Instant,
    val schemaVersion: Int = 1,
)

data class Conversation(
    val id: ConversationId,
    val title: String,
    val projectId: String? = null,
    val currentLeafMessageId: MessageNodeId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val settings: ConversationSettings = ConversationSettings(),
    /** Reserved soft-state only: P3-A exposes no archive/pin/delete mutation UI. */
    val archivedAt: Instant? = null,
    val pinnedAt: Instant? = null,
    /** Local collection marker; it never changes the ordinary conversation ordering. */
    val favoritedAt: Instant? = null,
    val deletedAt: Instant? = null,
    /** Monotonic local management revision; stale pin/archive commands must not overwrite. */
    val revision: Long = 1L,
    val schemaVersion: Int = 1,
    /** Only a newly-created empty conversation may consume its first user message as a title. */
    val autoTitlePending: Boolean = false,
    val surface: ConversationSurface = ConversationSurface.CHAT,
    /** Title-only logical revision. Null marks legacy history without title ordering evidence. */
    val titleRevision: Long? = null,
) {
    init { require(title.isNotBlank()) { "会话标题不能为空。" }; require(revision > 0) { "会话 revision 必须为正数。" }; require(titleRevision == null || titleRevision > 0) { "标题版本必须为正数。" } }
}

data class Branch(
    val id: BranchId,
    val conversationId: ConversationId,
    val leafMessageId: MessageNodeId,
    val path: List<MessageNodeId>,
    val schemaVersion: Int = 1,
)

/** A stable, local-only projection for branch selection. It never owns or mutates tree truth. */
data class ConversationBranchLeaf(
    val leafId: MessageNodeId,
    val role: MessageRole,
    val preview: String,
    val revision: Int,
    val isCurrent: Boolean,
)

/** A text-only user message that can be safely revised without dropping attachment references. */
data class EditableConversationUserMessage(
    val messageId: MessageNodeId,
    val text: String,
)

data class ConversationSnapshot(
    val conversation: Conversation,
    val nodes: List<MessageNode>,
    val draft: ConversationDraft,
) {
    init { MessageTree(conversation, nodes) }
}

/**
 * P3-F's read-only branch projection. The Conversation Tree still owns validity and mutations;
 * this object prevents UI code from inventing a second definition of editable messages or leaves.
 */
object ConversationBranchHistory {
    fun project(snapshot: ConversationSnapshot): ConversationBranchProjection {
        val tree = MessageTree(snapshot.conversation, snapshot.nodes)
        val path = tree.contextPath()
        val leaves = snapshot.nodes
            .filter { node -> tree.isLeaf(node.id) }
            .sortedWith(compareBy<MessageNode>({ it.createdAt }, { it.id.value }))
            .map { node ->
                ConversationBranchLeaf(
                    leafId = node.id,
                    role = node.role,
                    preview = node.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }.ifBlank { "等待本地输出" },
                    revision = node.revision.revision,
                    isCurrent = node.id == snapshot.conversation.currentLeafMessageId,
                )
            }
        val editableUserMessages = path
            .filter { node -> node.role == MessageRole.USER && node.content.all { it is ContentBlock.Text } }
            .map { node ->
                EditableConversationUserMessage(
                    messageId = node.id,
                    text = node.content.filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text },
                )
            }
        return ConversationBranchProjection(path, leaves, editableUserMessages)
    }

    fun leaves(snapshot: ConversationSnapshot): List<ConversationBranchLeaf> = project(snapshot).leaves

    fun editableUserMessages(snapshot: ConversationSnapshot): List<EditableConversationUserMessage> =
        project(snapshot).editableUserMessages
}

data class ConversationBranchProjection(
    val path: List<MessageNode>,
    val leaves: List<ConversationBranchLeaf>,
    val editableUserMessages: List<EditableConversationUserMessage>,
)

/** Conversation Domain owns tree validity, selected branch, edit branching, and context-path reads. */
class MessageTree(
    val conversation: Conversation,
    val nodes: List<MessageNode>,
) {
    private val byId = nodes.associateBy(MessageNode::id)
    private val childrenByParentId = nodes.groupBy(MessageNode::parentMessageId)

    init {
        require(byId.size == nodes.size) { "消息 ID 必须稳定且唯一。" }
        require(nodes.all { it.conversationId == conversation.id }) { "消息不能跨会话归属。" }
        require(nodes.all { it.parentMessageId == null || byId.containsKey(it.parentMessageId) }) { "消息父节点不存在。" }
        require(childrenByParentId.values.all { siblings ->
            siblings.map(MessageNode::siblingPosition).distinct().size == siblings.size
        }) { "同一父节点下的消息排序不能重复。" }
        require(!hasAncestorCycle()) { "消息树不能形成环。" }
        val leaf = conversation.currentLeafMessageId
        require((nodes.isEmpty() && leaf == null) || (nodes.isNotEmpty() && leaf != null && byId.containsKey(leaf))) {
            "当前分支指针与消息树不一致。"
        }
        require(leaf == null || childrenOf(leaf).isEmpty()) { "当前分支必须指向叶消息。" }
        nodes.filter { it.revision.revisesMessageId != null }.forEach { revised ->
            val original = byId.getValue(requireNotNull(revised.revision.revisesMessageId))
            require(original.parentMessageId == revised.parentMessageId && original.role == revised.role) {
                "修订消息必须作为原消息的同角色同父节点版本。"
            }
            require(revised.revision.revision == original.revision.revision + 1) { "修订号必须递增。" }
        }
    }

    fun currentBranch(): Branch? = conversation.currentLeafMessageId?.let { leaf ->
        Branch(BranchId.forLeaf(leaf), conversation.id, leaf, pathTo(leaf))
    }

    fun contextPath(): List<MessageNode> = currentBranch()?.path?.map(byId::getValue).orEmpty()

    fun node(id: MessageNodeId): MessageNode = requireNotNull(byId[id]) { "消息不存在：${id.value}" }

    fun isLeaf(id: MessageNodeId): Boolean = childrenOf(id).isEmpty()

    fun nextSiblingPosition(parentId: MessageNodeId?): Int = childrenByParentId[parentId]
        .orEmpty()
        .asSequence()
        .map(MessageNode::siblingPosition)
        .maxOrNull()
        ?.plus(1)
        ?: 0

    private fun pathTo(leaf: MessageNodeId): List<MessageNodeId> {
        val reversed = generateSequence(leaf) { byId.getValue(it).parentMessageId }.toList()
        return reversed.asReversed()
    }

    private fun hasAncestorCycle(): Boolean {
        val resolved = mutableSetOf<MessageNodeId>()
        nodes.forEach { node ->
            val visiting = mutableSetOf<MessageNodeId>()
            var cursor: MessageNodeId? = node.id
            while (cursor != null && cursor !in resolved) {
                if (!visiting.add(cursor)) return true
                cursor = byId.getValue(cursor).parentMessageId
            }
            resolved += visiting
        }
        return false
    }

    private fun childrenOf(id: MessageNodeId): List<MessageNode> = childrenByParentId[id].orEmpty()
}

data class AppendMessageRequest(
    val role: MessageRole,
    val content: List<ContentBlock>,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.COMPLETE,
    val invocation: MessageInvocationReference? = null,
    val checkpoint: MessageCheckpoint? = null,
    val messageId: MessageNodeId = MessageNodeId.new(),
)

class ConversationTreeService(private val clock: Clock) {
    fun create(
        title: String = ConversationAutoTitle.NEW_CONVERSATION_TITLE,
        projectId: String? = null,
        settings: ConversationSettings = ConversationSettings(),
        autoTitlePending: Boolean = false,
        surface: ConversationSurface = ConversationSurface.CHAT,
    ): ConversationSnapshot {
        val now = clock.instant()
        return ConversationSnapshot(
            conversation = Conversation(
                id = ConversationId.new(), title = title.trim(), autoTitlePending = autoTitlePending,
                projectId = projectId, currentLeafMessageId = null, createdAt = now, updatedAt = now, settings = settings,
                surface = surface,
            ),
            nodes = emptyList(),
            draft = ConversationDraft(updatedAt = now),
        )
    }

    fun append(snapshot: ConversationSnapshot, request: AppendMessageRequest): ConversationSnapshot {
        val tree = MessageTree(snapshot.conversation, snapshot.nodes)
        val parentId = snapshot.conversation.currentLeafMessageId
        val node = MessageNode(
            id = request.messageId, conversationId = snapshot.conversation.id, parentMessageId = parentId,
            siblingPosition = tree.nextSiblingPosition(parentId), role = request.role, content = request.content,
            createdAt = clock.instant(), deliveryState = request.deliveryState, invocation = request.invocation,
            checkpoint = request.checkpoint,
        )
        val appended = snapshot.copy(
            conversation = snapshot.conversation.copy(
                currentLeafMessageId = node.id,
                // An app-entry placeholder is not an actual conversation until its first send.
                createdAt = if (snapshot.nodes.isEmpty() && snapshot.conversation.autoTitlePending &&
                    snapshot.conversation.surface == ConversationSurface.CHAT && request.role == MessageRole.USER
                ) node.createdAt else snapshot.conversation.createdAt,
                updatedAt = clock.instant(),
            ),
            nodes = snapshot.nodes + node,
        )
        val title = ConversationAutoTitle.titleForFirstCompletedAssistantReply(appended, node.id) ?: return appended
        return appended.copy(conversation = appended.conversation.copy(
            title = title,
            titleRevision = Math.addExact(appended.conversation.titleRevision ?: 0L, 1L),
            autoTitlePending = false,
            revision = appended.conversation.revision + 1,
        ))
    }

    /** Editing a historical user message forks a sibling and makes it the selected leaf. */
    fun editUserMessage(snapshot: ConversationSnapshot, originalId: MessageNodeId, content: List<ContentBlock>): ConversationSnapshot {
        val tree = MessageTree(snapshot.conversation, snapshot.nodes)
        val original = tree.node(originalId)
        require(original.role == MessageRole.USER) { "P3-A 仅允许编辑历史用户消息；助手重答留给后续用例。" }
        val replacement = original.copy(
            id = MessageNodeId.new(),
            siblingPosition = tree.nextSiblingPosition(original.parentMessageId),
            content = content,
            createdAt = clock.instant(),
            deliveryState = MessageDeliveryState.COMPLETE,
            revision = MessageRevision(original.revision.revision + 1, original.id),
            invocation = null,
            checkpoint = null,
        )
        return snapshot.copy(
            conversation = snapshot.conversation.copy(currentLeafMessageId = replacement.id, updatedAt = clock.instant()),
            nodes = snapshot.nodes + replacement,
        )
    }

    fun switchBranch(snapshot: ConversationSnapshot, leafMessageId: MessageNodeId): ConversationSnapshot {
        val tree = MessageTree(snapshot.conversation, snapshot.nodes)
        require(tree.isLeaf(leafMessageId)) { "只能切换到可复现的叶分支。" }
        return snapshot.copy(conversation = snapshot.conversation.copy(currentLeafMessageId = leafMessageId, updatedAt = clock.instant()))
    }

    fun saveDraft(snapshot: ConversationSnapshot, text: String, attachments: List<ConversationAttachmentReference>): ConversationSnapshot =
        snapshot.copy(draft = ConversationDraft(text = text, attachments = attachments, updatedAt = clock.instant()))
}

sealed interface ConversationMutationResult {
    data class Saved(val snapshot: ConversationSnapshot) : ConversationMutationResult
    data class Rejected(val reason: String) : ConversationMutationResult
}

class CreateConversationUseCase(
    private val treeService: ConversationTreeService,
    private val repository: ConversationRepository,
) {
    fun execute(
        title: String = ConversationAutoTitle.NEW_CONVERSATION_TITLE,
        projectId: String? = null,
        surface: ConversationSurface = ConversationSurface.CHAT,
    ): ConversationMutationResult = runCatching {
        ConversationMutationResult.Saved(repository.save(treeService.create(title, projectId, autoTitlePending = surface == ConversationSurface.CHAT, surface = surface)))
    }.getOrElse { ConversationMutationResult.Rejected("会话创建未完成，本地数据没有被报告为成功。") }
}

class AppendConversationMessageUseCase(
    private val treeService: ConversationTreeService,
    private val repository: ConversationRepository,
) {
    fun execute(snapshot: ConversationSnapshot, request: AppendMessageRequest): ConversationMutationResult = runCatching {
        ConversationMutationResult.Saved(repository.save(treeService.append(snapshot, request)))
    }.getOrElse { ConversationMutationResult.Rejected("消息追加未完成，本地数据没有被报告为成功。") }
}

class EditConversationUserMessageUseCase(
    private val treeService: ConversationTreeService,
    private val repository: ConversationRepository,
) {
    fun execute(snapshot: ConversationSnapshot, originalId: MessageNodeId, content: List<ContentBlock>): ConversationMutationResult = runCatching {
        ConversationMutationResult.Saved(repository.save(treeService.editUserMessage(snapshot, originalId, content)))
    }.getOrElse { ConversationMutationResult.Rejected("编辑未完成，原分支保持不变。") }
}

class SwitchConversationBranchUseCase(
    private val treeService: ConversationTreeService,
    private val repository: ConversationRepository,
) {
    fun execute(snapshot: ConversationSnapshot, leafMessageId: MessageNodeId): ConversationMutationResult = runCatching {
        ConversationMutationResult.Saved(repository.save(treeService.switchBranch(snapshot, leafMessageId)))
    }.getOrElse { ConversationMutationResult.Rejected("分支切换未完成，当前分支保持不变。") }
}

class ReadConversationContextPathUseCase(private val repository: ConversationRepository) {
    fun execute(id: ConversationId): List<MessageNode> = repository.findById(id)?.let { MessageTree(it.conversation, it.nodes).contextPath() }.orEmpty()
}
