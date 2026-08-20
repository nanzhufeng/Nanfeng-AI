package com.nanzhufeng.ai.domain

import java.time.Instant

/** P3-H's safe, read-only model. It never becomes a retry or Provider execution command. */
data class ConversationAttemptHistoryItem(
    val actionKind: ConversationActionKind,
    val modelDisplayName: String,
    val modelId: String,
    val harnessLabel: String,
    val registrySnapshotId: ModelRegistrySnapshotId,
    val deliveryState: MessageDeliveryState,
    val createdAt: Instant,
    val inputTokens: Long?,
    val outputTokens: Long?,
)

/**
 * Makes P3-C's append-only local action facts visible without treating the one current runtime
 * state as truth for historical siblings. Any malformed or non-local fixture row is excluded.
 */
class ConversationAttemptHistoryProjection {
    fun project(
        snapshot: ConversationSnapshot,
        lineages: List<ConversationAttemptLineage>,
        activeRuntime: ConversationRuntimeState?,
    ): List<ConversationAttemptHistoryItem> {
        val models = P3CLocalFixtureRegistry.snapshot.models.associateBy { it.id }
        return lineages.asSequence()
            .filter { it.conversationId == snapshot.conversation.id }
            .filter { lineage ->
                val node = snapshot.nodes.firstOrNull { it.id == lineage.createdMessageId }
                node?.conversationId == snapshot.conversation.id &&
                    node.role == MessageRole.ASSISTANT &&
                    node.invocation?.invocationId == lineage.invocationId
            }
            .filter { lineage ->
                lineage.selection.providerId == ProviderId.MOCK &&
                    lineage.selection.registrySnapshotId == P3CLocalFixtureRegistry.snapshot.id &&
                    models.containsKey(lineage.selection.modelId) &&
                    lineage.selection.harnessId.startsWith("p3c-local-") &&
                    lineage.selection.harnessId.endsWith("-fixture")
            }
            .sortedWith(compareByDescending<ConversationAttemptLineage> { it.createdAt }.thenBy { it.invocationId.value })
            .map { lineage ->
                val node = snapshot.nodes.first { it.id == lineage.createdMessageId }
                val runtime = activeRuntime?.takeIf {
                    it.conversationId == snapshot.conversation.id &&
                        it.invocationId == lineage.invocationId && it.messageId == lineage.createdMessageId
                }
                ConversationAttemptHistoryItem(
                    actionKind = lineage.actionKind,
                    modelDisplayName = requireNotNull(models[lineage.selection.modelId]).displayName,
                    modelId = lineage.selection.modelId,
                    harnessLabel = "${lineage.selection.harnessId} v${lineage.selection.harnessVersion}",
                    registrySnapshotId = lineage.selection.registrySnapshotId,
                    deliveryState = node.deliveryState,
                    createdAt = lineage.createdAt,
                    inputTokens = runtime?.inputTokens,
                    outputTokens = runtime?.outputTokens,
                )
            }
            .toList()
    }
}

class ReadConversationAttemptHistoryUseCase(
    private val actions: ConversationActionRepository,
    private val runtime: ConversationRuntimeRepository,
    private val projection: ConversationAttemptHistoryProjection = ConversationAttemptHistoryProjection(),
) {
    fun execute(snapshot: ConversationSnapshot): List<ConversationAttemptHistoryItem> = projection.project(
        snapshot = snapshot,
        lineages = actions.lineagesForConversation(snapshot.conversation.id),
        activeRuntime = runtime.stateFor(snapshot.conversation.id),
    )
}
