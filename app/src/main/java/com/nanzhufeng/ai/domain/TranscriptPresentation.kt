package com.nanzhufeng.ai.domain

import java.time.Instant

/**
 * P6-F's read-only transcript truth projection.  It deliberately joins persisted MessageNodes
 * only with the existing, append-only local action lineage.  Missing facts stay missing: this
 * type must never infer a Provider, model, cache hit, or elapsed duration from current settings.
 */
enum class TranscriptOrigin(val label: String) {
    LOCAL_RECORD("本地记录"),
    LOCAL_FIXTURE("本地 fixture"),
    IMPORTED("已导入"),
    CACHE("历史缓存"),
    PROVIDER("Provider"),
}

data class TranscriptMessageMetadata(
    val messageId: MessageNodeId,
    val createdAt: Instant,
    val origin: TranscriptOrigin,
    val modelSnapshotLabel: String?,
    /** Request-level price, only when response-owned accounting has a real or marked estimate. */
    val costLabel: String?,
    /**
     * A message-owned duration, if and only if the persisted run/import owner has a reliable
     * positive elapsed value.  It intentionally stays nullable: an absent ledger is not a UI
     * placeholder opportunity.
     */
    val workDurationLabel: String?,
    /** Persisted, content-free Provider/runtime failure code for an incomplete assistant node. */
    val safeErrorCode: String? = null,
    /** Local-only progress copy for an empty, persisted PARTIAL assistant node. */
    val waitingPreview: AssistantWaitingPreview? = null,
) {
    fun accessibilityLabel(roleLabel: String): String = buildString {
        append(roleLabel)
        append("；")
        append(origin.label)
        append("；时间 ")
        append(createdAt)
        append("；")
        append(modelSnapshotLabel ?: "模型未知")
        costLabel?.let {
            append("；费用 ")
            append(it)
        }
        workDurationLabel?.let {
            append("；")
            append(it)
        }
        waitingPreview?.let {
            append("；")
            append(it.phase)
        }
    }
}

/**
 * Explicitly transient copy for the empty interval before the first provider chunk. It is
 * calculated from already-rendered local input and is never saved as an assistant conclusion.
 */
data class AssistantWaitingPreview(
    val preface: String,
    val phase: String,
)

data class PresentedTranscriptMessage(
    val message: PresentedMessage,
    val metadata: TranscriptMessageMetadata,
)

class ConversationTranscriptPresentation(
    private val renderer: MessagePresentationRenderer,
) {
    fun render(
        path: List<MessageNode>,
        lineages: List<ConversationAttemptLineage>,
        invocations: Map<InvocationId, InvocationRecord> = emptyMap(),
        runtimeByMessage: Map<MessageNodeId, ConversationRuntimeState> = emptyMap(),
        responseModelAttributions: Map<MessageNodeId, List<AssistantResponseModelAttribution>> = emptyMap(),
    ): List<PresentedTranscriptMessage> {
        val localLineageByInvocation = lineages.associateBy { it.invocationId }
        return renderer.render(path).map { presented ->
            val node = path.first { it.id == presented.messageId }
            val lineage = node.invocation?.invocationId?.let(localLineageByInvocation::get)
            PresentedTranscriptMessage(
                message = presented,
                metadata = metadataFor(
                    node,
                    lineage,
                    node.invocation?.invocationId?.let(invocations::get),
                    runtimeByMessage[node.id],
                    responseModelAttributions[node.id].orEmpty(),
                ).copy(waitingPreview = waitingPreviewFor(path, node)),
            )
        }
    }

    private fun waitingPreviewFor(path: List<MessageNode>, assistant: MessageNode): AssistantWaitingPreview? {
        if (assistant.role != MessageRole.ASSISTANT || assistant.deliveryState != MessageDeliveryState.PARTIAL ||
            assistant.content.filterIsInstance<ContentBlock.Text>().any { it.text.isNotBlank() }
        ) return null
        val user = assistant.parentMessageId?.let { parentId -> path.firstOrNull { it.id == parentId && it.role == MessageRole.USER } }
            ?: return AssistantWaitingPreview("我先梳理你的问题和重点，再给你清晰答复。", "正在准备回答")
        val attachments = user.content.filterIsInstance<ContentBlock.Attachment>().map { it.attachment }
        attachments.firstOrNull()?.let { attachment ->
            return when {
                attachment.mimeType == "application/pdf" -> AssistantWaitingPreview("我先梳理这份 PDF 的内容与问题重点，再给你整理答复。", "正在准备文档分析")
                attachment.mimeType.startsWith("image/") -> AssistantWaitingPreview("我先查看图片中的关键信息，再整理成清晰答复。", "正在准备图片分析")
                attachment.mimeType.startsWith("video/") -> AssistantWaitingPreview("我先梳理视频相关的问题和重点，再给你整理答复。", "正在准备视频分析")
                attachment.mimeType.startsWith("audio/") -> AssistantWaitingPreview("我先梳理音频相关的问题和重点，再给你整理答复。", "正在准备音频分析")
                else -> AssistantWaitingPreview("我先整理这份文件与你的问题重点，再给你清晰答复。", "正在准备文件分析")
            }
        }
        val text = user.content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
        return when {
            Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(text) -> AssistantWaitingPreview("我先梳理链接与提问重点，再给你清晰答复。", "正在准备资料梳理")
            Regex("代码|开发|报错|bug|程序|安卓|Android", RegexOption.IGNORE_CASE).containsMatchIn(text) -> AssistantWaitingPreview("我先梳理实现目标和现有约束，再给你可执行的答复。", "正在准备问题分析")
            Regex("投资|估值|股票|财报|市场|经济", RegexOption.IGNORE_CASE).containsMatchIn(text) -> AssistantWaitingPreview("我先梳理分析目标和关键条件，再给你明确答复。", "正在准备分析框架")
            else -> AssistantWaitingPreview("我先梳理你的问题和重点，再给你清晰答复。", "正在准备回答")
        }
    }

    private fun metadataFor(
        node: MessageNode,
        lineage: ConversationAttemptLineage?,
        invocation: InvocationRecord?,
        runtime: ConversationRuntimeState?,
        responseAttributions: List<AssistantResponseModelAttribution>,
    ): TranscriptMessageMetadata {
        // Ordinary Provider results use their assistant-message owned Attempt attribution.  A
        // later Composer change, model-directory refresh or retry may not overwrite that fact.
        // P3-C fixture lineage remains the legacy local-only source for fixture answers.
        val isLocalFixture = lineage?.selection?.providerId == ProviderId.MOCK
        val responseModel = responseAttributions
            .distinctBy { it.attemptId }
            .joinToString(" / ") { it.footerLabel() }
            .takeIf { it.isNotBlank() }
        val responseCost = responseAttributions
            .mapNotNull(AssistantResponseModelAttribution::footerCostLabel)
            .distinct()
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }
        val fixtureModel = lineage?.selection?.takeIf { isLocalFixture }?.let { selection ->
            val display = P3CLocalFixtureRegistry.snapshot.models
                .firstOrNull { it.id == selection.modelId }
                ?.displayName
                ?: selection.modelId
            "$display · ${selection.modelId} · ${selection.providerId.name} · ${selection.registrySnapshotId.value}"
        }
        return TranscriptMessageMetadata(
            messageId = node.id,
            createdAt = node.createdAt,
            origin = if (isLocalFixture) TranscriptOrigin.LOCAL_FIXTURE else TranscriptOrigin.LOCAL_RECORD,
            modelSnapshotLabel = responseModel ?: fixtureModel,
            costLabel = responseCost,
            workDurationLabel = invocation
                ?.takeIf { node.role == MessageRole.ASSISTANT && it.status != InvocationStatus.BLOCKED }
                ?.taskRun
                ?.let { run -> formatPersistedWorkDuration(run.startedAt, run.completedAt) }
                ?: runtime
                    ?.takeIf {
                        node.role == MessageRole.ASSISTANT &&
                            node.invocation?.invocationId == it.invocationId &&
                            it.messageId == node.id
                    }
                    ?.let { runtime -> formatPersistedWorkDuration(runtime.startedAt, runtime.updatedAt) },
            safeErrorCode = runtime
                ?.takeIf {
                    node.role == MessageRole.ASSISTANT &&
                        node.deliveryState == MessageDeliveryState.FAILED &&
                        it.messageId == node.id
                }
                ?.safeErrorCode,
        )
    }
}

/** The duration is derived exclusively from a persisted run boundary, never message time. */
private fun formatPersistedWorkDuration(startedAt: Instant, completedAt: Instant): String? {
    val millis = completedAt.toEpochMilli() - startedAt.toEpochMilli()
    if (millis <= 0L) return null
    val seconds = millis / 1_000.0
    return when {
        seconds < 60 -> "用时 ${if (seconds % 1.0 == 0.0) seconds.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, seconds)} 秒"
        else -> "用时 ${millis / 60_000} 分 ${(millis % 60_000) / 1_000} 秒"
    }
}
