package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AiRuntimeEvent
import com.nanzhufeng.ai.domain.AiRuntimeEventId
import com.nanzhufeng.ai.domain.AiRuntimeSecurityMetadata
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionContract
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRecord
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionRequest
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionState
import com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransportHandle
import com.nanzhufeng.ai.domain.ConversationRealTextNormalizedEvent
import com.nanzhufeng.ai.domain.ConversationRealTextTestExecutionResult
import com.nanzhufeng.ai.domain.ConversationRealTextTestTransport
import com.nanzhufeng.ai.domain.ConversationRuntimePersistenceResult
import com.nanzhufeng.ai.domain.ConversationRuntimeStateMachine
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.RuntimeCancelled
import com.nanzhufeng.ai.domain.RuntimeCompleted
import com.nanzhufeng.ai.domain.RuntimeContentDelta
import com.nanzhufeng.ai.domain.RuntimeFailed
import com.nanzhufeng.ai.domain.RuntimeRunStarted
import java.time.Clock
import java.util.concurrent.Callable

/**
 * Android-only P0 test adapter. It is deliberately unregistered from AppContainer and UI.
 * The outer Room transaction binds the P3-I receipt transition and the assistant projection.
 */
class RoomConversationRealTextTestExecutionAdapter(
    private val database: NanfengAiDatabase,
    private val conversations: RoomConversationRepository,
    private val executions: RoomConversationRealTextExecutionRepository,
    private val stateMachine: ConversationRuntimeStateMachine,
    private val clock: Clock,
) {
    fun execute(
        request: ConversationRealTextExecutionRequest,
        transport: ConversationRealTextTestTransport,
    ): ConversationRealTextTestExecutionResult {
        val started = start(request)
        if (started !is ConversationRealTextTestExecutionResult.Started) return started
        var latest: ConversationRealTextTestExecutionResult = started
        transport.normalizedEvents(request.toHandle()).forEach { event ->
            latest = apply(request, event)
            if (latest is ConversationRealTextTestExecutionResult.Rejected ||
                latest is ConversationRealTextTestExecutionResult.Replayed ||
                (latest as? ConversationRealTextTestExecutionResult.Updated)?.record?.state in terminalStates
            ) return latest
        }
        return latest
    }

    /** Explicit start only. Rebuilding this adapter never resumes or retries a RUNNING receipt. */
    fun start(request: ConversationRealTextExecutionRequest): ConversationRealTextTestExecutionResult =
        runCatching {
            database.runInTransaction(Callable {
                executions.findById(request.executionId)?.let { existing ->
                    if (existing.request != request) return@Callable ConversationRealTextTestExecutionResult.Rejected("执行 ID 的事实不一致。")
                    return@Callable if (existing.state == ConversationRealTextExecutionState.PREPARED) {
                        startPrepared(request)
                    } else ConversationRealTextTestExecutionResult.Replayed(existing)
                }
                startPrepared(request)
            })
        }.getOrElse { ConversationRealTextTestExecutionResult.Rejected("测试执行未能原子启动。") }

    fun apply(
        request: ConversationRealTextExecutionRequest,
        normalized: ConversationRealTextNormalizedEvent,
    ): ConversationRealTextTestExecutionResult = runCatching {
        database.runInTransaction(Callable {
            val execution = executions.findById(request.executionId)
                ?: return@Callable ConversationRealTextTestExecutionResult.Rejected("执行事实不存在。")
            if (execution.request != request) return@Callable ConversationRealTextTestExecutionResult.Rejected("执行事实不一致。")
            if (execution.state != ConversationRealTextExecutionState.RUNNING) {
                return@Callable ConversationRealTextTestExecutionResult.Replayed(execution)
            }
            val snapshot = conversations.findById(request.conversationId)
                ?: return@Callable ConversationRealTextTestExecutionResult.Rejected("会话不存在。")
            val prior = conversations.stateFor(request.conversationId)
                ?: return@Callable ConversationRealTextTestExecutionResult.Rejected("运行状态不存在。")
            if (prior.invocationId != request.invocationId || prior.messageId != request.assistantMessageId) {
                return@Callable ConversationRealTextTestExecutionResult.Rejected("运行状态与执行事实不一致。")
            }
            val event = normalized.toRuntimeEvent(request, prior.nextExpectedSequence)
            val projection = stateMachine.apply(snapshot, prior, event)
            when (val persisted = conversations.apply(projection, event)) {
                is ConversationRuntimePersistenceResult.Rejected -> throw AtomicExecutionFailure()
                is ConversationRuntimePersistenceResult.Applied,
                is ConversationRuntimePersistenceResult.Replayed -> Unit
            }
            val next = normalized.executionState()
            val updated = if (next == null) execution else {
                val transition = executions.transition(
                    executionId = request.executionId,
                    expected = ConversationRealTextExecutionState.RUNNING,
                    next = next,
                    updatedAt = clock.instant(),
                    safeErrorCode = normalized.safeErrorCode(),
                )
                (transition as? com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransitionResult.Updated)?.record
                    ?: throw AtomicExecutionFailure()
            }
            ConversationRealTextTestExecutionResult.Updated(updated)
        })
    }.getOrElse { ConversationRealTextTestExecutionResult.Rejected("测试执行事件未能原子写入。") }

    private fun startPrepared(request: ConversationRealTextExecutionRequest): ConversationRealTextTestExecutionResult {
        val snapshot = conversations.findById(request.conversationId)
            ?: return ConversationRealTextTestExecutionResult.Rejected("会话不存在。")
        val user = snapshot.nodes.firstOrNull { it.id == request.userMessageId }
            ?: return ConversationRealTextTestExecutionResult.Rejected("用户消息不存在。")
        if (user.role != MessageRole.USER || snapshot.conversation.currentLeafMessageId != user.id) {
            return ConversationRealTextTestExecutionResult.Rejected("测试执行只能从当前用户消息启动。")
        }
        val started = RuntimeRunStarted(
            eventId = AiRuntimeEventId("p3i:${request.executionId.value}:started"),
            invocationId = request.invocationId,
            conversationId = request.conversationId,
            messageId = request.assistantMessageId,
            sequence = 0,
            emittedAt = clock.instant(),
            security = security,
            parentMessageId = request.userMessageId,
        )
        val projection = stateMachine.apply(snapshot, null, started)
        val prepared = when (val result = executions.prepare(request)) {
            is com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult.Prepared -> result.record
            is com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult.Replayed -> result.record
            is com.nanzhufeng.ai.domain.ConversationRealTextExecutionPrepareResult.Conflict -> return ConversationRealTextTestExecutionResult.Rejected(result.reason)
        }
        if (prepared.state != ConversationRealTextExecutionState.PREPARED) {
            return ConversationRealTextTestExecutionResult.Replayed(prepared)
        }
        if (conversations.apply(projection, started) is ConversationRuntimePersistenceResult.Rejected) throw AtomicExecutionFailure()
        val running = executions.transition(
            executionId = request.executionId,
            expected = ConversationRealTextExecutionState.PREPARED,
            next = ConversationRealTextExecutionState.RUNNING,
            updatedAt = clock.instant(),
        ) as? com.nanzhufeng.ai.domain.ConversationRealTextExecutionTransitionResult.Updated
            ?: throw AtomicExecutionFailure()
        return ConversationRealTextTestExecutionResult.Started(running.record)
    }

    private fun ConversationRealTextExecutionRequest.toHandle() = ConversationRealTextExecutionTransportHandle(
        executionId = executionId, invocationId = invocationId, attemptId = attemptId, requestFingerprint = requestFingerprint,
    )

    private fun ConversationRealTextNormalizedEvent.toRuntimeEvent(
        request: ConversationRealTextExecutionRequest,
        sequence: Long,
    ): AiRuntimeEvent = when (this) {
        is ConversationRealTextNormalizedEvent.TextDelta -> RuntimeContentDelta(
            AiRuntimeEventId("p3i:${request.executionId.value}:$sequence:delta"), request.invocationId,
            request.conversationId, request.assistantMessageId, sequence, clock.instant(), text, security,
        )
        ConversationRealTextNormalizedEvent.Completed -> RuntimeCompleted(
            AiRuntimeEventId("p3i:${request.executionId.value}:$sequence:completed"), request.invocationId,
            request.conversationId, request.assistantMessageId, sequence, clock.instant(), security,
        )
        is ConversationRealTextNormalizedEvent.Failed -> RuntimeFailed(
            AiRuntimeEventId("p3i:${request.executionId.value}:$sequence:failed"), request.invocationId,
            request.conversationId, request.assistantMessageId, sequence, clock.instant(), safeErrorCode, security,
        )
        is ConversationRealTextNormalizedEvent.Cancelled -> RuntimeCancelled(
            AiRuntimeEventId("p3i:${request.executionId.value}:$sequence:cancelled"), request.invocationId,
            request.conversationId, request.assistantMessageId, sequence, clock.instant(), security,
        )
    }

    private fun ConversationRealTextNormalizedEvent.executionState(): ConversationRealTextExecutionState? = when (this) {
        is ConversationRealTextNormalizedEvent.TextDelta -> null
        ConversationRealTextNormalizedEvent.Completed -> ConversationRealTextExecutionState.SUCCEEDED
        is ConversationRealTextNormalizedEvent.Failed -> ConversationRealTextExecutionState.FAILED
        is ConversationRealTextNormalizedEvent.Cancelled -> ConversationRealTextExecutionState.CANCELLED
    }

    private fun ConversationRealTextNormalizedEvent.safeErrorCode(): String? = when (this) {
        is ConversationRealTextNormalizedEvent.Failed -> safeErrorCode
        is ConversationRealTextNormalizedEvent.Cancelled -> safeErrorCode
        else -> null
    }

    private class AtomicExecutionFailure : RuntimeException()

    private companion object {
        val terminalStates = setOf(
            ConversationRealTextExecutionState.SUCCEEDED,
            ConversationRealTextExecutionState.FAILED,
            ConversationRealTextExecutionState.CANCELLED,
        )
        val security = AiRuntimeSecurityMetadata(source = "P3I_TEST_TRANSPORT_NO_NETWORK")
    }
}
