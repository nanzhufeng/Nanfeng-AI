package com.nanzhufeng.ai.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

@JvmInline
value class AiRuntimeEventId(val value: String) {
    companion object { fun new(): AiRuntimeEventId = AiRuntimeEventId(UUID.randomUUID().toString()) }
}

/** Only normalized, versioned events cross an adapter boundary. Provider chunks/JSON never do. */
enum class AiRuntimeEventKind { RUN_STARTED, CONTENT_DELTA, USAGE_UPDATED, CHECKPOINT, COMPLETED, FAILED, CANCELLED }

data class AiRuntimeSecurityMetadata(
    val source: String,
    val providerPayloadRetained: Boolean = false,
    val credentialBytesRead: Boolean = false,
    val networkRequestConstructed: Boolean = false,
    val schemaVersion: Int = 1,
) {
    init {
        require(source.isNotBlank()) { "事件来源不能为空。" }
        require(!providerPayloadRetained) { "运行事件不得保留 Provider 原始负载。" }
        require(!credentialBytesRead && !networkRequestConstructed) { "P3-B 本地事件不得读取凭据或构造网络请求。" }
    }
}

sealed interface AiRuntimeEvent {
    val eventId: AiRuntimeEventId
    val invocationId: InvocationId
    val conversationId: ConversationId
    val messageId: MessageNodeId
    val sequence: Long
    val emittedAt: Instant
    val security: AiRuntimeSecurityMetadata
    val schemaVersion: Int
    val kind: AiRuntimeEventKind
    fun canonicalPayload(): String
}

data class RuntimeRunStarted(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId,
    override val conversationId: ConversationId, override val messageId: MessageNodeId,
    override val sequence: Long, override val emittedAt: Instant,
    override val security: AiRuntimeSecurityMetadata,
    /** P3-C may attach a new run to a selected historical parent without changing old nodes. */
    val parentMessageId: MessageNodeId? = null,
    override val schemaVersion: Int = 1,
) : AiRuntimeEvent {
    override val kind = AiRuntimeEventKind.RUN_STARTED
    override fun canonicalPayload() = "started"
}

data class RuntimeContentDelta(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId,
    override val conversationId: ConversationId, override val messageId: MessageNodeId,
    override val sequence: Long, override val emittedAt: Instant, val delta: String,
    override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
) : AiRuntimeEvent {
    init { require(delta.isNotEmpty()) { "内容增量不能为空。" } }
    override val kind = AiRuntimeEventKind.CONTENT_DELTA
    override fun canonicalPayload() = "delta:$delta"
}

data class RuntimeUsageUpdated(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId,
    override val conversationId: ConversationId, override val messageId: MessageNodeId,
    override val sequence: Long, override val emittedAt: Instant, val inputTokens: Long?, val outputTokens: Long?,
    override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
) : AiRuntimeEvent {
    init { require((inputTokens == null || inputTokens >= 0) && (outputTokens == null || outputTokens >= 0)) }
    override val kind = AiRuntimeEventKind.USAGE_UPDATED
    override fun canonicalPayload() = "usage:$inputTokens:$outputTokens"
}

data class RuntimeCheckpoint(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId,
    override val conversationId: ConversationId, override val messageId: MessageNodeId,
    override val sequence: Long, override val emittedAt: Instant, val resumableFromSequence: Long,
    override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
) : AiRuntimeEvent {
    init { require(resumableFromSequence == sequence + 1) { "checkpoint 必须恢复到下一确定序号。" } }
    override val kind = AiRuntimeEventKind.CHECKPOINT
    override fun canonicalPayload() = "checkpoint:$resumableFromSequence"
}

sealed interface RuntimeTerminalEvent : AiRuntimeEvent

data class RuntimeCompleted(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId, override val conversationId: ConversationId,
    override val messageId: MessageNodeId, override val sequence: Long, override val emittedAt: Instant,
    override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
    /** Canonical visible answer after Provider reasoning/text separation and presentation guards. */
    val finalVisibleText: String? = null,
) : RuntimeTerminalEvent {
    init { require(finalVisibleText == null || finalVisibleText.isNotBlank()) { "最终可见正文不能为空。" } }
    override val kind = AiRuntimeEventKind.COMPLETED
    override fun canonicalPayload() = "completed:${finalVisibleText.orEmpty()}"
}

data class RuntimeFailed(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId, override val conversationId: ConversationId,
    override val messageId: MessageNodeId, override val sequence: Long, override val emittedAt: Instant,
    val safeErrorCode: String, override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
) : RuntimeTerminalEvent {
    init { require(safeErrorCode.matches(Regex("[A-Z0-9_]{1,64}"))) }
    override val kind = AiRuntimeEventKind.FAILED; override fun canonicalPayload() = "failed:$safeErrorCode"
}

data class RuntimeCancelled(
    override val eventId: AiRuntimeEventId, override val invocationId: InvocationId, override val conversationId: ConversationId,
    override val messageId: MessageNodeId, override val sequence: Long, override val emittedAt: Instant,
    override val security: AiRuntimeSecurityMetadata, override val schemaVersion: Int = 1,
) : RuntimeTerminalEvent { override val kind = AiRuntimeEventKind.CANCELLED; override fun canonicalPayload() = "cancelled" }

fun AiRuntimeEvent.payloadFingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest("${kind.name}|${invocationId.value}|${conversationId.value}|${messageId.value}|$sequence|${canonicalPayload()}".toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

enum class ConversationRuntimeStatus { STREAMING, COMPLETED, FAILED, CANCELLED }

data class ConversationRuntimeState(
    val invocationId: InvocationId,
    val conversationId: ConversationId,
    val messageId: MessageNodeId,
    val nextExpectedSequence: Long,
    val status: ConversationRuntimeStatus,
    val startedAt: Instant,
    val updatedAt: Instant,
    val lastCheckpointSequence: Long? = null,
    val resumableFromSequence: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val safeErrorCode: String? = null,
    val schemaVersion: Int = 1,
) { val isTerminal get() = status != ConversationRuntimeStatus.STREAMING }

data class ConversationRuntimeProjection(val snapshot: ConversationSnapshot, val state: ConversationRuntimeState)

sealed interface ConversationRuntimePersistenceResult {
    data class Applied(val projection: ConversationRuntimeProjection) : ConversationRuntimePersistenceResult
    data class Replayed(val projection: ConversationRuntimeProjection) : ConversationRuntimePersistenceResult
    data class Rejected(val reason: String) : ConversationRuntimePersistenceResult
}

/** Deterministic transition owner. Repository only atomically records its projection. */
class ConversationRuntimeStateMachine(private val clock: Clock) {
    fun apply(snapshot: ConversationSnapshot, prior: ConversationRuntimeState?, event: AiRuntimeEvent): ConversationRuntimeProjection {
        require(event.sequence >= 0) { "事件序号不能为负数。" }
        return if (event is RuntimeRunStarted) start(snapshot, prior, event) else continueRun(snapshot, requireNotNull(prior) { "运行尚未开始。" }, event)
    }

    private fun start(snapshot: ConversationSnapshot, prior: ConversationRuntimeState?, event: RuntimeRunStarted): ConversationRuntimeProjection {
        require(prior == null) { "同一会话已有运行状态。" }
        require(event.sequence == 0L) { "运行必须从序号 0 开始。" }
        val tree = MessageTree(snapshot.conversation, snapshot.nodes)
        val parentId = event.parentMessageId ?: snapshot.conversation.currentLeafMessageId
        require(parentId == null || snapshot.nodes.any { it.id == parentId }) { "运行父消息不存在。" }
        val assistant = MessageNode(event.messageId, snapshot.conversation.id, parentId,
            tree.nextSiblingPosition(parentId), MessageRole.ASSISTANT, emptyList(), event.emittedAt,
            MessageDeliveryState.PARTIAL, invocation = MessageInvocationReference(event.invocationId), checkpoint = MessageCheckpoint(0, 1))
        val nextSnapshot = snapshot.copy(conversation = snapshot.conversation.copy(currentLeafMessageId = assistant.id, updatedAt = clock.instant()), nodes = snapshot.nodes + assistant)
        return ConversationRuntimeProjection(nextSnapshot, ConversationRuntimeState(event.invocationId, event.conversationId, event.messageId, 1,
            ConversationRuntimeStatus.STREAMING, event.emittedAt, event.emittedAt, 0, 1))
    }

    private fun continueRun(snapshot: ConversationSnapshot, prior: ConversationRuntimeState, event: AiRuntimeEvent): ConversationRuntimeProjection {
        require(event.invocationId == prior.invocationId && event.conversationId == prior.conversationId && event.messageId == prior.messageId) { "事件不属于当前运行。" }
        // The repository compares the durable fingerprint before treating this as a replay.
        if (event.sequence < prior.nextExpectedSequence) return ConversationRuntimeProjection(snapshot, prior)
        require(!prior.isTerminal) { "终态后拒绝任何事件。" }
        require(event.sequence == prior.nextExpectedSequence) { "事件序号断档或乱序。" }
        require(snapshot.conversation.currentLeafMessageId == prior.messageId) { "非当前分支运行不得污染当前路径。" }
        val node = MessageTree(snapshot.conversation, snapshot.nodes).node(prior.messageId)
        val text = node.content.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        val (changedNode, nextState) = when (event) {
            is RuntimeContentDelta -> node.copy(content = listOf(ContentBlock.Text(text + event.delta)), checkpoint = MessageCheckpoint(event.sequence, event.sequence + 1)) to prior.copy(nextExpectedSequence = event.sequence + 1, updatedAt = event.emittedAt)
            is RuntimeUsageUpdated -> node to prior.copy(nextExpectedSequence = event.sequence + 1, updatedAt = event.emittedAt, inputTokens = event.inputTokens, outputTokens = event.outputTokens)
            is RuntimeCheckpoint -> node.copy(checkpoint = MessageCheckpoint(event.sequence, event.resumableFromSequence)) to prior.copy(nextExpectedSequence = event.sequence + 1, updatedAt = event.emittedAt, lastCheckpointSequence = event.sequence, resumableFromSequence = event.resumableFromSequence)
            is RuntimeCompleted -> {
                val completedText = event.finalVisibleText ?: text
                require(completedText.isNotBlank()) { "未完成输出不得伪装为成功。" }
                val completedContent = event.finalVisibleText?.let { listOf(ContentBlock.Text(it)) } ?: node.content
                node.copy(content = completedContent, deliveryState = MessageDeliveryState.COMPLETE, checkpoint = MessageCheckpoint(event.sequence, null)) to prior.copy(nextExpectedSequence = event.sequence + 1, status = ConversationRuntimeStatus.COMPLETED, updatedAt = event.emittedAt, lastCheckpointSequence = event.sequence, resumableFromSequence = null)
            }
            is RuntimeFailed -> node.copy(deliveryState = MessageDeliveryState.FAILED, checkpoint = MessageCheckpoint(event.sequence, event.sequence + 1)) to prior.copy(nextExpectedSequence = event.sequence + 1, status = ConversationRuntimeStatus.FAILED, updatedAt = event.emittedAt, safeErrorCode = event.safeErrorCode, lastCheckpointSequence = event.sequence, resumableFromSequence = event.sequence + 1)
            is RuntimeCancelled -> node.copy(deliveryState = MessageDeliveryState.CANCELLED, checkpoint = MessageCheckpoint(event.sequence, event.sequence + 1)) to prior.copy(nextExpectedSequence = event.sequence + 1, status = ConversationRuntimeStatus.CANCELLED, updatedAt = event.emittedAt, lastCheckpointSequence = event.sequence, resumableFromSequence = event.sequence + 1)
            is RuntimeRunStarted -> error("运行开始事件不能重复进入状态机。")
        }
        val updatedSnapshot = snapshot.copy(
            conversation = snapshot.conversation.copy(updatedAt = clock.instant()),
            nodes = snapshot.nodes.map { if (it.id == changedNode.id) changedNode else it },
        )
        val title = if (event is RuntimeCompleted) {
            ConversationAutoTitle.titleForFirstCompletedAssistantReply(updatedSnapshot, changedNode.id)
        } else null
        val titledSnapshot = title?.let {
            updatedSnapshot.copy(conversation = updatedSnapshot.conversation.copy(
                title = it,
                autoTitlePending = false,
                revision = updatedSnapshot.conversation.revision + 1,
            ))
        } ?: updatedSnapshot
        return ConversationRuntimeProjection(titledSnapshot, nextState)
    }
}

/** Test-only deterministic local source; it has no transport, credential or network dependency. */
class DeterministicFixtureStreamingAdapter(private val clock: Clock) {
    fun events(state: ConversationRuntimeState, fail: Boolean = false): List<AiRuntimeEvent> {
        val safe = AiRuntimeSecurityMetadata(source = "LOCAL_DETERMINISTIC_FIXTURE")
        fun id(suffix: String) = AiRuntimeEventId("fixture-${state.invocationId.value}-$suffix")
        val t = clock.instant()
        if (fail) return listOf(
            RuntimeContentDelta(id("1"), state.invocationId, state.conversationId, state.messageId, 1, t, "这是已保留的本地部分输出。", safe),
            RuntimeFailed(id("2"), state.invocationId, state.conversationId, state.messageId, 2, t, "LOCAL_FIXTURE_FAILURE", safe),
        )
        return listOf(
            RuntimeContentDelta(id("1"), state.invocationId, state.conversationId, state.messageId, 1, t, "## 本地结论\n\n这是带有阅读层级的本地 Markdown。\n\n- 重点一：信息可快速扫读\n- 重点二：结构化内容可单独复制\n\n", safe),
            RuntimeContentDelta(id("2"), state.invocationId, state.conversationId, state.messageId, 2, t, "> 安全引用\n\n| 项目 | 当前状态 |\n| --- | --- |\n| 信息层级 | 已启用 |\n| 单独复制 | 已启用 |\n\n原始来源 https://example.test/report。\n\n```kotlin\nval local = true\n```", safe),
            RuntimeUsageUpdated(id("3"), state.invocationId, state.conversationId, state.messageId, 3, t, 4, 6, safe),
            RuntimeCheckpoint(id("4"), state.invocationId, state.conversationId, state.messageId, 4, t, 5, safe),
            RuntimeCompleted(id("5"), state.invocationId, state.conversationId, state.messageId, 5, t, safe),
        )
    }
}

class ApplyConversationRuntimeEventUseCase(
    private val conversations: ConversationRepository,
    private val runtime: ConversationRuntimeRepository,
    private val stateMachine: ConversationRuntimeStateMachine,
) {
    fun execute(event: AiRuntimeEvent): ConversationRuntimePersistenceResult {
        val replayState = runtime.stateFor(event.conversationId)
        val snapshot = conversations.findById(event.conversationId)
            ?: return ConversationRuntimePersistenceResult.Rejected("会话不存在。")
        return runCatching { runtime.apply(stateMachine.apply(snapshot, replayState, event), event) }
            .getOrElse { ConversationRuntimePersistenceResult.Rejected(it.message ?: "运行事件被拒绝。") }
    }
}

/** Starts a fresh local-only attempt. A retry after terminal state always gets a new InvocationId. */
class StartLocalConversationRuntimeUseCase(
    private val conversations: ConversationRepository,
    private val runtime: ConversationRuntimeRepository,
    private val stateMachine: ConversationRuntimeStateMachine,
    private val clock: Clock,
) {
    fun execute(conversationId: ConversationId): ConversationRuntimePersistenceResult {
        val prior = runtime.stateFor(conversationId)
        if (prior != null && !prior.isTerminal) return ConversationRuntimePersistenceResult.Rejected("当前本地流仍在运行。")
        val snapshot = conversations.findById(conversationId) ?: return ConversationRuntimePersistenceResult.Rejected("会话不存在。")
        val event = RuntimeRunStarted(
            AiRuntimeEventId.new(), InvocationId.new(), conversationId, MessageNodeId.new(), 0, clock.instant(),
            AiRuntimeSecurityMetadata(source = "LOCAL_DETERMINISTIC_FIXTURE"),
        )
        return runCatching { runtime.apply(stateMachine.apply(snapshot, null, event), event) }
            .getOrElse { ConversationRuntimePersistenceResult.Rejected(it.message ?: "本地流未能开始。") }
    }
}

sealed interface ProviderRuntimeDraftSubmissionResult {
    data class Started(
        val submittedSnapshot: ConversationSnapshot,
        val runtime: ConversationRuntimeState,
    ) : ProviderRuntimeDraftSubmissionResult
    data class Rejected(val reason: String) : ProviderRuntimeDraftSubmissionResult
}

/** A retry keeps the original user message but owns a new assistant runtime and placeholder. */
sealed interface ProviderRuntimeRetryResult {
    data class Started(
        val snapshot: ConversationSnapshot,
        val runtime: ConversationRuntimeState,
    ) : ProviderRuntimeRetryResult

    data class Rejected(val reason: String) : ProviderRuntimeRetryResult
}

/**
 * Creates the same durable assistant placeholder used by a fresh send, but beneath an already
 * committed user message. This makes an explicit retry visible before the first provider chunk
 * and keeps the failed sibling off the active transcript path.
 */
class StartProviderRuntimeForExistingUserUseCase(
    private val conversations: ConversationRepository,
    private val runtime: ConversationRuntimeRepository,
    private val stateMachine: ConversationRuntimeStateMachine,
    private val clock: Clock,
) {
    fun execute(conversationId: ConversationId, userMessageId: MessageNodeId): ProviderRuntimeRetryResult {
        val prior = runtime.stateFor(conversationId)
        if (prior != null && !prior.isTerminal) return ProviderRuntimeRetryResult.Rejected("当前回复仍在生成。")
        val snapshot = conversations.findById(conversationId)
            ?: return ProviderRuntimeRetryResult.Rejected("会话不存在。")
        val user = snapshot.nodes.firstOrNull { it.id == userMessageId && it.role == MessageRole.USER }
            ?: return ProviderRuntimeRetryResult.Rejected("原发送消息不存在。")
        val started = RuntimeRunStarted(
            eventId = AiRuntimeEventId.new(),
            invocationId = InvocationId.new(),
            conversationId = conversationId,
            messageId = MessageNodeId.new(),
            sequence = 0,
            emittedAt = clock.instant(),
            security = AiRuntimeSecurityMetadata(source = "DIRECT_PROVIDER_RETRY"),
            parentMessageId = user.id,
        )
        val projection = runCatching { stateMachine.apply(snapshot, null, started) }
            .getOrElse { return ProviderRuntimeRetryResult.Rejected(it.message ?: "助手占位消息未能创建。") }
        return when (val persisted = runtime.apply(projection, started)) {
            is ConversationRuntimePersistenceResult.Applied -> ProviderRuntimeRetryResult.Started(persisted.projection.snapshot, persisted.projection.state)
            is ConversationRuntimePersistenceResult.Replayed -> ProviderRuntimeRetryResult.Started(persisted.projection.snapshot, persisted.projection.state)
            is ConversationRuntimePersistenceResult.Rejected -> ProviderRuntimeRetryResult.Rejected(persisted.reason)
        }
    }
}

/** Starts one ordinary Provider stream without a crash window between user and assistant facts. */
class SubmitConversationDraftAndStartProviderRuntimeUseCase(
    private val conversations: ConversationRepository,
    private val drafts: ConversationDraftRepository,
    private val runtime: ConversationRuntimeRepository,
    private val stateMachine: ConversationRuntimeStateMachine,
    private val tree: ConversationTreeService,
    private val clock: Clock,
) {
    fun execute(conversationId: ConversationId): ProviderRuntimeDraftSubmissionResult {
        val prior = runtime.stateFor(conversationId)
        if (prior != null && !prior.isTerminal) return ProviderRuntimeDraftSubmissionResult.Rejected("当前回复仍在生成。")
        val snapshot = conversations.findById(conversationId) ?: return ProviderRuntimeDraftSubmissionResult.Rejected("会话不存在。")
        val draft = drafts.loadDraft(conversationId) ?: return ProviderRuntimeDraftSubmissionResult.Rejected("草稿未能从本机回读。")
        if (!ConversationDraftPolicy.isSendable(draft)) return ProviderRuntimeDraftSubmissionResult.Rejected("请输入文字或保留附件后再发送。")
        val content = buildList {
            if (draft.text.isNotBlank()) add(ContentBlock.Text(draft.text))
            draft.attachments.forEach { add(ContentBlock.Attachment(it)) }
        }
        val appended = tree.append(snapshot, AppendMessageRequest(MessageRole.USER, content))
        val cleared = tree.saveDraft(appended, "", emptyList())
        val event = RuntimeRunStarted(
            AiRuntimeEventId.new(), InvocationId.new(), conversationId, MessageNodeId.new(), 0, clock.instant(),
            AiRuntimeSecurityMetadata(source = "DIRECT_PROVIDER_SSE"),
        )
        val projection = runCatching { stateMachine.apply(cleared, null, event) }
            .getOrElse { return ProviderRuntimeDraftSubmissionResult.Rejected(it.message ?: "助手占位消息未能创建。") }
        return when (val persisted = runtime.submitDraftAndStart(cleared, draft, projection, event)) {
            is ConversationRuntimePersistenceResult.Applied -> ProviderRuntimeDraftSubmissionResult.Started(cleared, persisted.projection.state)
            is ConversationRuntimePersistenceResult.Replayed -> ProviderRuntimeDraftSubmissionResult.Started(cleared, persisted.projection.state)
            is ConversationRuntimePersistenceResult.Rejected -> ProviderRuntimeDraftSubmissionResult.Rejected(persisted.reason)
        }
    }
}
