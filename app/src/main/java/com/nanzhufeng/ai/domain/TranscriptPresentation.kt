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
    /**
     * A message-owned duration, if and only if the persisted run/import owner has a reliable
     * positive elapsed value.  It intentionally stays nullable: an absent ledger is not a UI
     * placeholder opportunity.
     */
    val workDurationLabel: String?,
) {
    fun accessibilityLabel(roleLabel: String): String = buildString {
        append(roleLabel)
        append("；")
        append(origin.label)
        append("；时间 ")
        append(createdAt)
        append("；")
        append(modelSnapshotLabel ?: "模型未知")
        workDurationLabel?.let {
            append("；")
            append(it)
        }
    }
}

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
                ),
            )
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
