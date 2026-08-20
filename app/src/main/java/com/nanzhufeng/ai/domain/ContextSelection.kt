package com.nanzhufeng.ai.domain

/**
 * P4-B's local-only context-selection IR. It freezes source ownership without assembling a
 * Prompt, calculating tokens, reading attachments, or starting a RunSpec.
 */
enum class ContextSourceKind {
    PROJECT_INSTRUCTION,
    CURRENT_CONVERSATION_PATH,
    CONVERSATION_DRAFT,
    SIBLING_BRANCHES,
    ATTACHMENTS,
    TOOL_RESULTS,
    KNOWLEDGE,
    MEMORY,
    RETRIEVAL,
    SUMMARY_AND_COMPRESSION,
    CACHE_PREFIX,
}

enum class ContextSourceDisposition { METADATA_ONLY, EXCLUDED, NOT_IMPLEMENTED }

data class ContextSourceBoundary(
    val kind: ContextSourceKind,
    val disposition: ContextSourceDisposition,
    val reason: String,
    val schemaVersion: Int = 1,
)

/** Safe identity and integrity metadata only; text, bytes and URI/path data are deliberately absent. */
data class ContextMessageReference(
    val messageId: MessageNodeId,
    val role: MessageRole,
    val deliveryState: MessageDeliveryState,
    val contentBlockCount: Int,
    val contentHash: String,
    val schemaVersion: Int = 1,
) {
    init {
        require(contentBlockCount >= 0) { "上下文消息内容块数量不能为负数。" }
        require(contentHash.matches(Regex("[0-9a-f]{64}"))) { "上下文消息必须有完整性摘要。" }
    }
}

data class ContextSelectionSnapshot(
    val conversationId: ConversationId,
    val currentBranchId: BranchId?,
    val projectContext: ProjectContextSnapshot,
    val messages: List<ContextMessageReference>,
    val sourceBoundaries: List<ContextSourceBoundary>,
    val policyVersion: Int = 1,
    val schemaVersion: Int = 1,
) {
    init {
        require(policyVersion > 0) { "上下文选择策略版本必须为正数。" }
        require(messages.map(ContextMessageReference::messageId).distinct().size == messages.size) {
            "上下文选择不能重复同一消息。"
        }
        require(sourceBoundaries.map(ContextSourceBoundary::kind).distinct().size == sourceBoundaries.size) {
            "每类上下文来源必须只有一个边界。"
        }
    }
}

sealed interface ContextSelectionResult {
    data class Selected(val snapshot: ContextSelectionSnapshot) : ContextSelectionResult
    data class Rejected(val reason: ContextSelectionRejection) : ContextSelectionResult
}

enum class ContextSelectionRejection { MISSING_CONVERSATION, INCONSISTENT_PROJECT_REFERENCE }

/** The sole owner of P4-B source selection semantics; it cannot produce a provider payload. */
class ContextSelectionDomain {
    fun select(conversationSnapshot: ConversationSnapshot, projectSnapshot: ProjectSnapshot?): ContextSelectionSnapshot {
        val conversation = conversationSnapshot.conversation
        val referencedProjectId = conversation.projectId
        require(referencedProjectId == null || projectSnapshot?.project?.id?.value == referencedProjectId) {
            "会话引用的项目不存在或不一致。"
        }
        val tree = MessageTree(conversation, conversationSnapshot.nodes)
        val currentBranch = tree.currentBranch()
        return ContextSelectionSnapshot(
            conversationId = conversation.id,
            currentBranchId = currentBranch?.id,
            projectContext = ProjectInstructionResolution.resolve(projectSnapshot).projectContext,
            messages = tree.contextPath().map { it.toContextMessageReference() },
            sourceBoundaries = sourceBoundaries(),
        )
    }

    private fun sourceBoundaries(): List<ContextSourceBoundary> = listOf(
        ContextSourceBoundary(ContextSourceKind.PROJECT_INSTRUCTION, ContextSourceDisposition.METADATA_ONLY, "仅固定项目指令 revision/hash；不复制正文。"),
        ContextSourceBoundary(ContextSourceKind.CURRENT_CONVERSATION_PATH, ContextSourceDisposition.METADATA_ONLY, "仅固定当前根到叶路径的安全元数据。"),
        ContextSourceBoundary(ContextSourceKind.CONVERSATION_DRAFT, ContextSourceDisposition.EXCLUDED, "草稿不是已提交的当前路径事实。"),
        ContextSourceBoundary(ContextSourceKind.SIBLING_BRANCHES, ContextSourceDisposition.EXCLUDED, "非当前分支不得混入。"),
        ContextSourceBoundary(ContextSourceKind.ATTACHMENTS, ContextSourceDisposition.EXCLUDED, "不读取附件内容或启动外发。"),
        ContextSourceBoundary(ContextSourceKind.TOOL_RESULTS, ContextSourceDisposition.EXCLUDED, "Tool 结果是不可信输入，P4-B 不处理。"),
        ContextSourceBoundary(ContextSourceKind.KNOWLEDGE, ContextSourceDisposition.NOT_IMPLEMENTED, "未开启 Knowledge 查询或项目范围检索。"),
        ContextSourceBoundary(ContextSourceKind.MEMORY, ContextSourceDisposition.NOT_IMPLEMENTED, "未读取或自动写入任何 Memory。"),
        ContextSourceBoundary(ContextSourceKind.RETRIEVAL, ContextSourceDisposition.NOT_IMPLEMENTED, "检索尚无合同。"),
        ContextSourceBoundary(ContextSourceKind.SUMMARY_AND_COMPRESSION, ContextSourceDisposition.NOT_IMPLEMENTED, "摘要与压缩尚无合同。"),
        ContextSourceBoundary(ContextSourceKind.CACHE_PREFIX, ContextSourceDisposition.NOT_IMPLEMENTED, "稳定缓存前缀尚无合同。"),
    )
}

class ReadContextSelectionUseCase(
    private val conversations: ConversationRepository,
    private val projects: ProjectRepository,
    private val domain: ContextSelectionDomain = ContextSelectionDomain(),
) {
    fun execute(conversationId: ConversationId): ContextSelectionResult {
        val conversationSnapshot = conversations.findById(conversationId)
            ?: return ContextSelectionResult.Rejected(ContextSelectionRejection.MISSING_CONVERSATION)
        val projectId = conversationSnapshot.conversation.projectId
        val projectSnapshot = projectId?.let { projects.findById(ProjectId(it)) }
        if (projectId != null && projectSnapshot == null) {
            return ContextSelectionResult.Rejected(ContextSelectionRejection.INCONSISTENT_PROJECT_REFERENCE)
        }
        return ContextSelectionResult.Selected(domain.select(conversationSnapshot, projectSnapshot))
    }
}

private fun MessageNode.toContextMessageReference(): ContextMessageReference = ContextMessageReference(
    messageId = id,
    role = role,
    deliveryState = deliveryState,
    contentBlockCount = content.size,
    contentHash = ProjectDomain.sha256(content.joinToString("|") { it.contextCanonicalValue() }),
)

private fun ContentBlock.contextCanonicalValue(): String = when (this) {
    is ContentBlock.Text -> "TEXT|$schemaVersion|$text"
    is ContentBlock.Attachment -> "ATTACHMENT|$schemaVersion|${attachment.id.value}|${attachment.mimeType}|${attachment.displayName.orEmpty()}|${attachment.byteCount}|${attachment.sha256}"
    is ContentBlock.ToolResult -> "TOOL|$schemaVersion|$toolName|$safeSummary"
}
