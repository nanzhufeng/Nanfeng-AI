package com.nanzhufeng.ai.domain

import java.time.Instant
import java.util.UUID

const val CONVERSATION_REAL_TEXT_EXECUTION_PROTOCOL = "conversation-real-text-execution-v1"

@JvmInline
value class ConversationRealTextExecutionId(val value: String) {
    companion object { fun new(): ConversationRealTextExecutionId = ConversationRealTextExecutionId(UUID.randomUUID().toString()) }
}

enum class ConversationRealTextExecutionState { PREPARED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * Content-free hand-off into the future real-text adapter. The actual text must remain ephemeral
 * inside that future adapter and is deliberately absent from this durable P0 contract.
 */
data class ConversationRealTextExecutionRequest(
    val executionId: ConversationRealTextExecutionId,
    val idempotencyKey: String,
    val requestFingerprint: String,
    val conversationId: ConversationId,
    val userMessageId: MessageNodeId,
    val assistantMessageId: MessageNodeId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val createdAt: Instant,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "幂等键不能为空。" }
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}"))) { "请求指纹必须是小写 SHA-256。" }
        require(listOf(
            executionId.value, conversationId.value, userMessageId.value, assistantMessageId.value,
            invocationId.value, attemptId.value,
        ).distinct().size == 6) { "执行、会话、消息、调用和 Attempt ID 不能复用。" }
    }
}

data class ConversationRealTextExecutionRecord(
    val request: ConversationRealTextExecutionRequest,
    val state: ConversationRealTextExecutionState,
    val updatedAt: Instant,
    val terminalAt: Instant? = null,
    val safeErrorCode: String? = null,
) {
    init {
        require(!updatedAt.isBefore(request.createdAt)) { "执行更新时间不能早于创建时间。" }
        require(terminalAt == null || !terminalAt.isBefore(request.createdAt)) { "执行终态时间不能早于创建时间。" }
        require(safeErrorCode == null || safeErrorCode.matches(Regex("[A-Z][A-Z0-9_]*"))) { "错误码必须是安全大写代码。" }
        when (state) {
            ConversationRealTextExecutionState.PREPARED, ConversationRealTextExecutionState.RUNNING -> {
                require(terminalAt == null && safeErrorCode == null) { "未终态执行不能携带终态或错误码。" }
            }
            ConversationRealTextExecutionState.SUCCEEDED -> require(terminalAt != null && safeErrorCode == null) {
                "成功执行必须有终态且没有错误码。"
            }
            ConversationRealTextExecutionState.FAILED, ConversationRealTextExecutionState.CANCELLED -> require(terminalAt != null && safeErrorCode != null) {
                "失败或取消执行必须有终态与安全错误码。"
            }
        }
    }
}

sealed interface ConversationRealTextExecutionPrepareResult {
    data class Prepared(val record: ConversationRealTextExecutionRecord) : ConversationRealTextExecutionPrepareResult
    data class Replayed(val record: ConversationRealTextExecutionRecord) : ConversationRealTextExecutionPrepareResult
    data class Conflict(val reason: String) : ConversationRealTextExecutionPrepareResult
}

sealed interface ConversationRealTextExecutionTransitionResult {
    data class Updated(val record: ConversationRealTextExecutionRecord) : ConversationRealTextExecutionTransitionResult
    data class Rejected(val reason: String) : ConversationRealTextExecutionTransitionResult
}

/** Durable boundary only. It has no content, credential, transport, UI or egress capability. */
interface ConversationRealTextExecutionRepository {
    fun prepare(request: ConversationRealTextExecutionRequest): ConversationRealTextExecutionPrepareResult
    fun findById(id: ConversationRealTextExecutionId): ConversationRealTextExecutionRecord?
    fun transition(
        executionId: ConversationRealTextExecutionId,
        expected: ConversationRealTextExecutionState,
        next: ConversationRealTextExecutionState,
        updatedAt: Instant,
        safeErrorCode: String? = null,
    ): ConversationRealTextExecutionTransitionResult
}

/** Future adapters may call this only with ephemeral request material; P0 provides no such material. */
interface ConversationRealTextTransport {
    fun execute(handle: ConversationRealTextExecutionTransportHandle): ConversationRealTextTransportResult
}

data class ConversationRealTextExecutionTransportHandle(
    val executionId: ConversationRealTextExecutionId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val requestFingerprint: String,
)

sealed interface ConversationRealTextTransportResult {
    data object DisabledNoNetwork : ConversationRealTextTransportResult
}

object DisabledConversationRealTextTransport : ConversationRealTextTransport {
    override fun execute(handle: ConversationRealTextExecutionTransportHandle): ConversationRealTextTransportResult =
        ConversationRealTextTransportResult.DisabledNoNetwork
}

object ConversationRealTextExecutionContract {
    fun prepared(request: ConversationRealTextExecutionRequest) = ConversationRealTextExecutionRecord(
        request = request,
        state = ConversationRealTextExecutionState.PREPARED,
        updatedAt = request.createdAt,
    )

    fun transition(
        current: ConversationRealTextExecutionRecord,
        next: ConversationRealTextExecutionState,
        updatedAt: Instant,
        safeErrorCode: String? = null,
    ): ConversationRealTextExecutionTransitionResult {
        if (current.state in setOf(
                ConversationRealTextExecutionState.SUCCEEDED,
                ConversationRealTextExecutionState.FAILED,
                ConversationRealTextExecutionState.CANCELLED,
            )
        ) return ConversationRealTextExecutionTransitionResult.Rejected("执行已终态，不能改写或重试。")
        if (next == ConversationRealTextExecutionState.PREPARED ||
            current.state == ConversationRealTextExecutionState.PREPARED && next !in setOf(
                ConversationRealTextExecutionState.RUNNING,
                ConversationRealTextExecutionState.FAILED,
                ConversationRealTextExecutionState.CANCELLED,
            ) ||
            current.state == ConversationRealTextExecutionState.RUNNING && next !in setOf(
                ConversationRealTextExecutionState.SUCCEEDED,
                ConversationRealTextExecutionState.FAILED,
                ConversationRealTextExecutionState.CANCELLED,
            )
        ) return ConversationRealTextExecutionTransitionResult.Rejected("执行状态转换不合法。")
        if (updatedAt.isBefore(current.updatedAt)) return ConversationRealTextExecutionTransitionResult.Rejected("执行时间不能回退。")
        return ConversationRealTextExecutionTransitionResult.Updated(
            ConversationRealTextExecutionRecord(
                request = current.request,
                state = next,
                updatedAt = updatedAt,
                terminalAt = if (next in setOf(ConversationRealTextExecutionState.SUCCEEDED, ConversationRealTextExecutionState.FAILED, ConversationRealTextExecutionState.CANCELLED)) updatedAt else null,
                safeErrorCode = safeErrorCode,
            ),
        )
    }
}
