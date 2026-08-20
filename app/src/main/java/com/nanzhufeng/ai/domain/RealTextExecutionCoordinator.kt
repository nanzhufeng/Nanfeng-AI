package com.nanzhufeng.ai.domain

import java.security.MessageDigest
import java.time.Instant

/**
 * P3 unregistered, fail-closed coordination core. It accepts only a completed preflight plan;
 * it never receives request text, credentials, attachments, endpoints, HTTP clients, or storage.
 */
const val REAL_TEXT_EXECUTION_COORDINATOR_PROTOCOL = "real-text-execution-coordinator-v1"

enum class RealTextExecutionCoordinatorSafeErrorCode {
    COORDINATOR_CANCELLED,
    COORDINATOR_USAGE_RESERVATION_REJECTED,
    COORDINATOR_RUNTIME_RECEIPT_REJECTED,
    COORDINATOR_RUNTIME_PROTOCOL_VIOLATION,
    COORDINATOR_TRANSPORT_BINDING_MISMATCH,
    COORDINATOR_TRANSPORT_RESULT_MISMATCH,
    COORDINATOR_TRANSPORT_DISABLED,
}

/** Safe receipt projection. Text deltas are transient arguments to [RealTextExecutionRuntimeReceiptPort]. */
data class RealTextExecutionRuntimeReceipt(
    val executionId: ConversationRealTextExecutionId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val requestFingerprint: String,
    val state: ConversationRealTextExecutionState,
    val safeErrorCode: String? = null,
) {
    init {
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}"))) { "请求指纹必须是小写 SHA-256。" }
        when (state) {
            ConversationRealTextExecutionState.SUCCEEDED -> require(safeErrorCode == null) { "成功 receipt 不能带错误码。" }
            ConversationRealTextExecutionState.FAILED, ConversationRealTextExecutionState.CANCELLED ->
                require(safeErrorCode != null) { "失败或取消 receipt 必须带安全错误码。" }
            ConversationRealTextExecutionState.PREPARED, ConversationRealTextExecutionState.RUNNING ->
                require(safeErrorCode == null) { "未终态 receipt 不能带错误码。" }
        }
    }
}

sealed interface RealTextExecutionRuntimeReceiptResult {
    data class Applied(val receipt: RealTextExecutionRuntimeReceipt) : RealTextExecutionRuntimeReceiptResult
    data class Replayed(val receipt: RealTextExecutionRuntimeReceipt) : RealTextExecutionRuntimeReceiptResult
    data class Rejected(val safeErrorCode: RealTextExecutionCoordinatorSafeErrorCode) : RealTextExecutionRuntimeReceiptResult
}

/**
 * Future owners may adapt this to P3-I/P3-B atomically. This increment provides no Room/SQLite
 * implementation and never registers one. [delta] is call-stack-only and must not enter a receipt.
 */
interface RealTextExecutionRuntimeReceiptPort {
    fun start(plan: RealTextExecutionReadyPlan, at: Instant): RealTextExecutionRuntimeReceiptResult
    fun appendPartial(plan: RealTextExecutionReadyPlan, delta: String, at: Instant): RealTextExecutionRuntimeReceiptResult
    fun finish(
        plan: RealTextExecutionReadyPlan,
        state: ConversationRealTextExecutionState,
        safeErrorCode: String?,
        at: Instant,
    ): RealTextExecutionRuntimeReceiptResult
}

sealed interface RealTextExecutionUsageReservationResult {
    data object Reserved : RealTextExecutionUsageReservationResult
    data object Replayed : RealTextExecutionUsageReservationResult
    data class Rejected(val safeErrorCode: RealTextExecutionCoordinatorSafeErrorCode) : RealTextExecutionUsageReservationResult
}

/** A future adapter may append P0 facts; the coordinator itself never sees a UsageLedgerRepository. */
interface RealTextExecutionUsageReservationPort {
    fun reserve(plan: RealTextUsageReservationPlan, at: Instant): RealTextExecutionUsageReservationResult
    fun release(plan: RealTextUsageReservationPlan, safeErrorCode: String, at: Instant): RealTextExecutionUsageReservationResult
}

interface RealTextExecutionCoordinatorCancellation {
    fun isCancellationRequested(): Boolean
}

object RealTextExecutionCoordinatorNotCancelled : RealTextExecutionCoordinatorCancellation {
    override fun isCancellationRequested(): Boolean = false
}

/**
 * This deliberately differs from [ProviderTransport]: a Ready plan cannot carry the ephemeral
 * input required by that lower boundary. A future visible-confirmation owner must bridge them in
 * a separately authorized increment. The default implementation sends nothing.
 */
interface RealTextExecutionCoordinatorTransport {
    fun execute(
        plan: RealTextExecutionReadyPlan,
        cancellation: RealTextExecutionCoordinatorCancellation,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult
}

object DisabledRealTextExecutionCoordinatorTransport : RealTextExecutionCoordinatorTransport {
    override fun execute(
        plan: RealTextExecutionReadyPlan,
        cancellation: RealTextExecutionCoordinatorCancellation,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult = ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
        plan.cancellation.executionId,
        plan.cancellation.invocationId,
        plan.cancellation.attemptId,
        plan.cancellation.requestFingerprint,
        plan.providerHandle,
        plan.modelId,
        null,
        ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK,
        ProviderTransportSafeErrorCode.TRANSPORT_DISABLED_NO_NETWORK,
        0,
    ))
}

object DisabledRealTextExecutionUsageReservationPort : RealTextExecutionUsageReservationPort {
    override fun reserve(plan: RealTextUsageReservationPlan, at: Instant) =
        RealTextExecutionUsageReservationResult.Rejected(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_USAGE_RESERVATION_REJECTED)
    override fun release(plan: RealTextUsageReservationPlan, safeErrorCode: String, at: Instant) =
        RealTextExecutionUsageReservationResult.Rejected(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_USAGE_RESERVATION_REJECTED)
}

object DisabledRealTextExecutionRuntimeReceiptPort : RealTextExecutionRuntimeReceiptPort {
    override fun start(plan: RealTextExecutionReadyPlan, at: Instant) = rejected()
    override fun appendPartial(plan: RealTextExecutionReadyPlan, delta: String, at: Instant) = rejected()
    override fun finish(plan: RealTextExecutionReadyPlan, state: ConversationRealTextExecutionState, safeErrorCode: String?, at: Instant) = rejected()
    private fun rejected() = RealTextExecutionRuntimeReceiptResult.Rejected(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED)
}

sealed interface RealTextExecutionCoordinatorTerminalResult {
    data class Succeeded(
        val receipt: RealTextExecutionRuntimeReceipt,
        val metadata: ProviderTransportSafeRunMetadata,
    ) : RealTextExecutionCoordinatorTerminalResult
    data class Failed(
        val receipt: RealTextExecutionRuntimeReceipt?,
        val safeErrorCode: String,
    ) : RealTextExecutionCoordinatorTerminalResult
    data class Cancelled(
        val receipt: RealTextExecutionRuntimeReceipt?,
        val safeErrorCode: String,
    ) : RealTextExecutionCoordinatorTerminalResult
}

sealed interface RealTextExecutionCoordinatorResult {
    data class Executed(val terminal: RealTextExecutionCoordinatorTerminalResult) : RealTextExecutionCoordinatorResult
    data class Replayed(val terminal: RealTextExecutionCoordinatorTerminalResult) : RealTextExecutionCoordinatorResult
    data class Conflict(val safeErrorCode: RealTextExecutionCoordinatorSafeErrorCode) : RealTextExecutionCoordinatorResult
}

/**
 * Pure in-memory ordering owner. There is intentionally no production singleton or AppContainer
 * registration. Replays are process-local; restart must be handled by a future durable owner.
 */
class RealTextExecutionCoordinator(
    private val transport: RealTextExecutionCoordinatorTransport = DisabledRealTextExecutionCoordinatorTransport,
    private val runtimeReceipts: RealTextExecutionRuntimeReceiptPort = DisabledRealTextExecutionRuntimeReceiptPort,
    private val usageReservations: RealTextExecutionUsageReservationPort = DisabledRealTextExecutionUsageReservationPort,
    private val now: () -> Instant,
) {
    private data class StoredTerminal(val planFingerprint: String, val terminal: RealTextExecutionCoordinatorTerminalResult)
    private val terminalsByExecution = mutableMapOf<ConversationRealTextExecutionId, StoredTerminal>()

    @Synchronized
    fun coordinate(
        plan: RealTextExecutionReadyPlan,
        cancellation: RealTextExecutionCoordinatorCancellation = RealTextExecutionCoordinatorNotCancelled,
    ): RealTextExecutionCoordinatorResult {
        val fingerprint = planFingerprint(plan)
        terminalsByExecution[plan.cancellation.executionId]?.let { prior ->
            return if (prior.planFingerprint == fingerprint) RealTextExecutionCoordinatorResult.Replayed(prior.terminal)
            else RealTextExecutionCoordinatorResult.Conflict(RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_TRANSPORT_BINDING_MISMATCH)
        }
        if (cancellation.isCancellationRequested()) return store(plan, fingerprint, cancelled(null, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_CANCELLED.name))

        when (usageReservations.reserve(plan.usageReservation, now())) {
            RealTextExecutionUsageReservationResult.Reserved, RealTextExecutionUsageReservationResult.Replayed -> Unit
            is RealTextExecutionUsageReservationResult.Rejected -> return store(plan, fingerprint, failed(null, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_USAGE_RESERVATION_REJECTED.name))
        }

        var started = false
        var terminal: RealTextExecutionCoordinatorTerminalResult? = null
        val internalCancellation = object : RealTextExecutionCoordinatorCancellation {
            override fun isCancellationRequested() = terminal != null || cancellation.isCancellationRequested()
        }
        val sink = ProviderTransportEventSink { event ->
            if (terminal != null) return@ProviderTransportEventSink
            when (event) {
                is ProviderTransportNormalizedEvent.Started -> {
                    if (started) terminal = close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_PROTOCOL_VIOLATION.name)
                    else {
                        started = true
                        when (val result = runtimeReceipts.start(plan, now())) {
                            is RealTextExecutionRuntimeReceiptResult.Applied, is RealTextExecutionRuntimeReceiptResult.Replayed -> Unit
                            is RealTextExecutionRuntimeReceiptResult.Rejected -> terminal = close(plan, ConversationRealTextExecutionState.FAILED, result.safeErrorCode.name)
                        }
                    }
                }
                is ProviderTransportNormalizedEvent.TextDelta -> {
                    if (!started) terminal = close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_PROTOCOL_VIOLATION.name)
                    else when (val result = runtimeReceipts.appendPartial(plan, event.text, now())) {
                        is RealTextExecutionRuntimeReceiptResult.Applied, is RealTextExecutionRuntimeReceiptResult.Replayed -> Unit
                        is RealTextExecutionRuntimeReceiptResult.Rejected -> terminal = close(plan, ConversationRealTextExecutionState.FAILED, result.safeErrorCode.name)
                    }
                }
                ProviderTransportNormalizedEvent.Completed -> {
                    terminal = if (started) close(plan, ConversationRealTextExecutionState.SUCCEEDED, null)
                    else close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_PROTOCOL_VIOLATION.name)
                }
                is ProviderTransportNormalizedEvent.Failed -> terminal = close(plan, ConversationRealTextExecutionState.FAILED, event.safeErrorCode.name)
                is ProviderTransportNormalizedEvent.Cancelled -> terminal = close(plan, ConversationRealTextExecutionState.CANCELLED, event.safeErrorCode.name)
            }
        }
        val transportResult = transport.execute(plan, internalCancellation, sink)
        val finalTerminal = terminal ?: terminalFromResult(plan, transportResult)
        val coherent = if (metadataMatches(plan, transportResult.metadata, finalTerminal)) finalTerminal
        else close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_TRANSPORT_RESULT_MISMATCH.name)
        val resultWithTransportMetadata = if (coherent is RealTextExecutionCoordinatorTerminalResult.Succeeded) {
            coherent.copy(metadata = transportResult.metadata)
        } else coherent
        return store(plan, fingerprint, resultWithTransportMetadata)
    }

    private fun terminalFromResult(
        plan: RealTextExecutionReadyPlan,
        result: ProviderTransportSafeResult,
    ): RealTextExecutionCoordinatorTerminalResult = when (result.metadata.terminalOutcome) {
        ProviderTransportTerminalOutcome.COMPLETED -> close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_PROTOCOL_VIOLATION.name)
        ProviderTransportTerminalOutcome.CANCELLED -> close(plan, ConversationRealTextExecutionState.CANCELLED, result.metadata.safeErrorCode!!.name)
        ProviderTransportTerminalOutcome.FAILED -> close(plan, ConversationRealTextExecutionState.FAILED, result.metadata.safeErrorCode!!.name)
        ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK -> close(plan, ConversationRealTextExecutionState.FAILED, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_TRANSPORT_DISABLED.name)
    }

    private fun close(
        plan: RealTextExecutionReadyPlan,
        state: ConversationRealTextExecutionState,
        safeErrorCode: String?,
    ): RealTextExecutionCoordinatorTerminalResult {
        val receipt = when (val result = runtimeReceipts.finish(plan, state, safeErrorCode, now())) {
            is RealTextExecutionRuntimeReceiptResult.Applied -> result.receipt
            is RealTextExecutionRuntimeReceiptResult.Replayed -> result.receipt
            is RealTextExecutionRuntimeReceiptResult.Rejected -> null
        }
        if (state != ConversationRealTextExecutionState.SUCCEEDED) {
            usageReservations.release(plan.usageReservation, safeErrorCode ?: RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED.name, now())
        }
        return when (state) {
            ConversationRealTextExecutionState.SUCCEEDED -> if (receipt != null) RealTextExecutionCoordinatorTerminalResult.Succeeded(
                receipt, ProviderTransportSafeRunMetadata(plan.cancellation.executionId, plan.cancellation.invocationId, plan.cancellation.attemptId,
                    plan.cancellation.requestFingerprint, plan.providerHandle, plan.modelId, null, ProviderTransportTerminalOutcome.COMPLETED, null, 0),
            ) else failed(null, RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED.name)
            ConversationRealTextExecutionState.CANCELLED -> cancelled(receipt, safeErrorCode ?: RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_CANCELLED.name)
            else -> failed(receipt, safeErrorCode ?: RealTextExecutionCoordinatorSafeErrorCode.COORDINATOR_RUNTIME_RECEIPT_REJECTED.name)
        }
    }

    private fun metadataMatches(
        plan: RealTextExecutionReadyPlan,
        metadata: ProviderTransportSafeRunMetadata,
        terminal: RealTextExecutionCoordinatorTerminalResult,
    ): Boolean {
        val idsMatch = metadata.executionId == plan.cancellation.executionId && metadata.invocationId == plan.cancellation.invocationId &&
            metadata.attemptId == plan.cancellation.attemptId && metadata.requestFingerprint == plan.cancellation.requestFingerprint &&
            metadata.providerHandle == plan.providerHandle && metadata.requestedModelId == plan.modelId
        if (!idsMatch) return false
        return when (terminal) {
            is RealTextExecutionCoordinatorTerminalResult.Succeeded -> metadata.terminalOutcome == ProviderTransportTerminalOutcome.COMPLETED
            is RealTextExecutionCoordinatorTerminalResult.Failed -> metadata.terminalOutcome != ProviderTransportTerminalOutcome.COMPLETED
            is RealTextExecutionCoordinatorTerminalResult.Cancelled -> metadata.terminalOutcome == ProviderTransportTerminalOutcome.CANCELLED
        }
    }

    private fun store(
        plan: RealTextExecutionReadyPlan,
        fingerprint: String,
        terminal: RealTextExecutionCoordinatorTerminalResult,
    ): RealTextExecutionCoordinatorResult {
        terminalsByExecution[plan.cancellation.executionId] = StoredTerminal(fingerprint, terminal)
        return RealTextExecutionCoordinatorResult.Executed(terminal)
    }

    private fun failed(receipt: RealTextExecutionRuntimeReceipt?, safeErrorCode: String) =
        RealTextExecutionCoordinatorTerminalResult.Failed(receipt, safeErrorCode)
    private fun cancelled(receipt: RealTextExecutionRuntimeReceipt?, safeErrorCode: String) =
        RealTextExecutionCoordinatorTerminalResult.Cancelled(receipt, safeErrorCode)
}

private fun planFingerprint(plan: RealTextExecutionReadyPlan): String = MessageDigest.getInstance("SHA-256").digest(
    listOf(
        plan.preflightId.value, plan.cancellation.executionId.value, plan.cancellation.invocationId.value, plan.cancellation.attemptId.value,
        plan.cancellation.requestFingerprint, plan.providerHandle.value, plan.modelId.value,
        plan.presetId?.name ?: "compare:${plan.verifiedCompareDeployment!!.deploymentId.value}:${plan.verifiedCompareDeployment.catalogVersion}:${plan.verifiedCompareDeployment.priceVersion}",
        plan.feeConfirmationFingerprint,
        plan.usageReservation.replayToken, plan.usageReservation.reconciliationFingerprint,
        plan.attachments.joinToString(",") { "${it.attachmentId.value}:${it.attachmentSha256}:${it.authorizationId.value}" },
    ).joinToString("|").toByteArray(Charsets.UTF_8),
).joinToString("") { "%02x".format(it) }
