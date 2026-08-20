package com.nanzhufeng.ai.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

@JvmInline
value class ConversationActionIntentId(val value: String) {
    companion object { fun new(): ConversationActionIntentId = ConversationActionIntentId(UUID.randomUUID().toString()) }
}

enum class ConversationActionKind { CONTINUE, RETRY, CHANGE_MODEL }

data class ConversationModelSelection(
    val providerId: ProviderId,
    val modelId: String,
    val harnessId: String,
    val harnessVersion: Int,
    val registrySnapshotId: ModelRegistrySnapshotId,
    val pricing: ModelPricing,
)

/** Content-free, append-only Invocation Ledger extension for a conversation attempt. */
data class ConversationAttemptLineage(
    val invocationId: InvocationId,
    val intentId: ConversationActionIntentId,
    val conversationId: ConversationId,
    val actionKind: ConversationActionKind,
    val originMessageId: MessageNodeId,
    val createdMessageId: MessageNodeId,
    val previousInvocationId: InvocationId,
    val selection: ConversationModelSelection,
    val createdAt: Instant,
    val schemaVersion: Int = 1,
) {
    fun fingerprint(): String = MessageDigest.getInstance("SHA-256").digest(
        listOf(
            intentId.value, conversationId.value, actionKind.name, originMessageId.value, createdMessageId.value,
            invocationId.value, previousInvocationId.value, selection.providerId.name, selection.modelId,
            selection.harnessId, selection.harnessVersion, selection.registrySnapshotId.value,
            selection.pricing.priceVersion, selection.pricing.currencyCode, selection.pricing.inputMicrosPerToken,
            selection.pricing.outputMicrosPerToken, selection.pricing.cachedInputMicrosPerToken,
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8),
    ).joinToString("") { "%02x".format(it) }
}

data class ConversationActionRequest(
    val intentId: ConversationActionIntentId,
    val conversationId: ConversationId,
    val sourceMessageId: MessageNodeId,
    val actionKind: ConversationActionKind,
    val targetPreset: ModelPresetId? = null,
)

sealed interface ConversationActionResult {
    data class Started(val projection: ConversationRuntimeProjection, val lineage: ConversationAttemptLineage) : ConversationActionResult
    data class Replayed(val projection: ConversationRuntimeProjection, val lineage: ConversationAttemptLineage) : ConversationActionResult
    data class Rejected(val reason: String) : ConversationActionResult
}

sealed interface ConversationActionPersistenceResult {
    data class Applied(val projection: ConversationRuntimeProjection, val lineage: ConversationAttemptLineage) : ConversationActionPersistenceResult
    data class Replayed(val projection: ConversationRuntimeProjection, val lineage: ConversationAttemptLineage) : ConversationActionPersistenceResult
    data class Rejected(val reason: String) : ConversationActionPersistenceResult
}

/** A verified, in-process snapshot. It has no catalog client, credential access or network capability. */
object P3CLocalFixtureRegistry {
    private fun model(id: String, label: String) = ModelDescriptor(
        id = id,
        displayName = label,
        capabilities = ModelCapabilities(supportsText = true, supportsVision = false, supportsStreaming = true),
        pricing = ModelPricing(
            priceVersion = "p3c-local-fixture-v1",
            currencyCode = "CNY",
            inputMicrosPerToken = 0,
            outputMicrosPerToken = 0,
        ),
    )

    val snapshot = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("p3c-local-fixture-registry-v1"),
        schemaVersion = 1,
        providerId = ProviderId.MOCK,
        catalogVersion = "p3c-local-fixture-v1",
        source = RegistrySnapshotSource.LOCAL_FIXTURE,
        capturedAt = Instant.parse("2026-08-12T00:00:00Z"),
        verificationStatus = RegistryVerificationStatus.VERIFIED,
        lastVerifiedAt = Instant.parse("2026-08-12T00:00:00Z"),
        models = listOf(
            model("fixture-local-flagship-v1", "本地旗舰 fixture"),
            model("fixture-local-balanced-v1", "本地均衡 fixture"),
            model("fixture-local-fast-v1", "本地快速 fixture"),
        ),
        presetMappings = listOf(
            ModelPresetMapping(ModelPresetId.CLAUDE_FABLE_5, "fixture-local-flagship-v1"),
            ModelPresetMapping(ModelPresetId.CLAUDE_OPUS_5, "fixture-local-flagship-v1"),
            ModelPresetMapping(ModelPresetId.CLAUDE_SONNET_5, "fixture-local-balanced-v1"),
            ModelPresetMapping(ModelPresetId.CLAUDE_HAIKU_4_5, "fixture-local-fast-v1"),
            ModelPresetMapping(ModelPresetId.GPT_5_6_SOL, "fixture-local-flagship-v1"),
            ModelPresetMapping(ModelPresetId.GPT_5_6_TERRA, "fixture-local-balanced-v1"),
            ModelPresetMapping(ModelPresetId.GPT_5_6_LUNA, "fixture-local-fast-v1"),
        ),
    )
}

class ConversationActionOrchestrator(
    private val conversations: ConversationRepository,
    private val runtime: ConversationRuntimeRepository,
    private val actions: ConversationActionRepository,
    private val registry: VersionedModelRegistry,
    private val stateMachine: ConversationRuntimeStateMachine,
    private val clock: Clock,
) {
    fun execute(request: ConversationActionRequest): ConversationActionResult {
        actions.lineageForIntent(request.intentId)?.let { existing ->
            val selection = resolveSelection(request.targetPreset ?: ModelPresetId.CLAUDE_SONNET_5)
            val sameRequest = existing.conversationId == request.conversationId &&
                existing.originMessageId == request.sourceMessageId &&
                existing.actionKind == request.actionKind &&
                selection == existing.selection
            if (!sameRequest) return ConversationActionResult.Rejected("重复 intent 的动作内容不一致。")
            val snapshot = conversations.findById(request.conversationId)
                ?: return ConversationActionResult.Rejected("重放 intent 缺少会话投影。")
            val state = runtime.stateFor(request.conversationId)
                ?: return ConversationActionResult.Rejected("重放 intent 缺少运行状态。")
            return ConversationActionResult.Replayed(ConversationRuntimeProjection(snapshot, state), existing)
        }
        val prior = runtime.stateFor(request.conversationId)
        if (prior != null && !prior.isTerminal) return ConversationActionResult.Rejected("当前本地流仍在运行，不能创建新尝试。")
        val snapshot = conversations.findById(request.conversationId) ?: return ConversationActionResult.Rejected("会话不存在。")
        val tree = runCatching { MessageTree(snapshot.conversation, snapshot.nodes) }
            .getOrElse { return ConversationActionResult.Rejected("会话树不可恢复。") }
        if (snapshot.conversation.currentLeafMessageId != request.sourceMessageId) {
            return ConversationActionResult.Rejected("只能从当前分支叶创建继续或重答。")
        }
        val source = runCatching { tree.node(request.sourceMessageId) }
            .getOrElse { return ConversationActionResult.Rejected("来源消息不存在。") }
        if (source.role != MessageRole.ASSISTANT || source.invocation == null) {
            return ConversationActionResult.Rejected("来源必须是带 Invocation 关联的 assistant 消息。")
        }
        if (source.deliveryState !in setOf(MessageDeliveryState.COMPLETE, MessageDeliveryState.FAILED, MessageDeliveryState.CANCELLED)) {
            return ConversationActionResult.Rejected("来源回答尚未终态，不能创建新尝试。")
        }
        val selection = resolveSelection(request.targetPreset ?: ModelPresetId.CLAUDE_SONNET_5)
            ?: return ConversationActionResult.Rejected("本地模型目录未验证或能力不满足；未创建网络请求。")
        val parent = when (request.actionKind) {
            ConversationActionKind.CONTINUE -> {
                val text = source.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
                if (source.deliveryState !in setOf(MessageDeliveryState.FAILED, MessageDeliveryState.CANCELLED) || text.isBlank()) {
                    return ConversationActionResult.Rejected("继续只接受含部分输出的失败或已停止 assistant。")
                }
                source.id
            }
            ConversationActionKind.RETRY, ConversationActionKind.CHANGE_MODEL -> {
                val parentId = source.parentMessageId ?: return ConversationActionResult.Rejected("重答来源缺少 user 父消息。")
                if (tree.node(parentId).role != MessageRole.USER) return ConversationActionResult.Rejected("重答只能在同一 user 父消息下创建版本。")
                if (request.actionKind == ConversationActionKind.CHANGE_MODEL) {
                    actions.lineageForInvocation(source.invocation.invocationId)?.let { existing ->
                        if (existing.selection.modelId == selection.modelId &&
                            existing.selection.harnessId == selection.harnessId &&
                            existing.selection.harnessVersion == selection.harnessVersion
                        ) return ConversationActionResult.Rejected("换模型重答必须选择不同的本地模型或 Harness。")
                    }
                }
                parentId
            }
        }
        val invocationId = InvocationId("p3c:${request.intentId.value}:invocation")
        val messageId = MessageNodeId("p3c:${request.intentId.value}:message")
        val lineage = ConversationAttemptLineage(
            invocationId = invocationId,
            intentId = request.intentId,
            conversationId = request.conversationId,
            actionKind = request.actionKind,
            originMessageId = source.id,
            createdMessageId = messageId,
            previousInvocationId = source.invocation.invocationId,
            selection = selection,
            createdAt = clock.instant(),
        )
        val started = RuntimeRunStarted(
            eventId = AiRuntimeEventId("p3c:${request.intentId.value}:started"),
            invocationId = invocationId,
            conversationId = request.conversationId,
            messageId = messageId,
            sequence = 0,
            emittedAt = clock.instant(),
            security = AiRuntimeSecurityMetadata("LOCAL_DETERMINISTIC_FIXTURE"),
            parentMessageId = parent,
        )
        val projection = runCatching { stateMachine.apply(snapshot, null, started) }
            .getOrElse { return ConversationActionResult.Rejected(it.message ?: "新尝试未能开始。") }
        return when (val persisted = actions.applyAction(projection, started, lineage)) {
            is ConversationActionPersistenceResult.Applied -> ConversationActionResult.Started(persisted.projection, persisted.lineage)
            is ConversationActionPersistenceResult.Replayed -> ConversationActionResult.Replayed(persisted.projection, persisted.lineage)
            is ConversationActionPersistenceResult.Rejected -> ConversationActionResult.Rejected(persisted.reason)
        }
    }

    private fun resolveSelection(preset: ModelPresetId): ConversationModelSelection? {
        val resolved = registry.resolve(ProviderId.MOCK, preset) as? ModelRegistryResolution.Resolved ?: return null
        if (resolved.snapshot.source != RegistrySnapshotSource.LOCAL_FIXTURE ||
            !resolved.model.capabilities.supportsText || !resolved.model.capabilities.supportsStreaming
        ) return null
        return ConversationModelSelection(
            providerId = ProviderId.MOCK,
            modelId = resolved.model.id,
            harnessId = "p3c-local-${preset.name.lowercase()}-fixture",
            harnessVersion = 1,
            registrySnapshotId = resolved.snapshot.id,
            pricing = resolved.model.pricing,
        )
    }
}
