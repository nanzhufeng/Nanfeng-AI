package com.nanzhufeng.ai.domain

/**
 * P3 provider transport boundary. It owns only ephemeral text streaming normalization and a
 * secret-free terminal projection. It has no HTTP client, endpoint, credential, attachment,
 * persistence, AppContainer, UI, runtime, or Usage Ledger registration.
 */
const val PROVIDER_TRANSPORT_BOUNDARY_PROTOCOL = "provider-transport-boundary-v1"

@JvmInline
value class ProviderTransportProviderHandle(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9._:-]{1,160}"))) { "服务商标识不合法。" } }
}

@JvmInline
value class ProviderTransportModelId(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9._:/-]{1,200}"))) { "模型标识不合法。" } }
}

/** A configurable, safe route identifier. It deliberately does not carry an endpoint or Key. */
data class ProviderTransportRoute(
    val providerHandle: ProviderTransportProviderHandle,
    val modelId: ProviderTransportModelId,
)

/**
 * Text is transient request material only. It is absent from P3-I receipts, runtime metadata,
 * logs, and all result types in this boundary.
 */
data class ProviderTransportEphemeralTextInput(val text: String) {
    init {
        require(text.isNotBlank()) { "文本请求不能为空。" }
        require(text.length <= 128_000) { "文本请求超过本地边界。" }
    }
}

/**
 * A future explicitly-authorized owner may construct this request in memory. This contract has
 * no production caller, and the input must never be retained after [ProviderTransport.execute].
 */
data class ProviderTransportRequest(
    val execution: ConversationRealTextExecutionTransportHandle,
    val route: ProviderTransportRoute,
    val input: ProviderTransportEphemeralTextInput,
) {
    init {
        require(execution.requestFingerprint.matches(Regex("[0-9a-f]{64}"))) { "请求指纹必须是小写 SHA-256。" }
    }
}

/** These are sanitized upstream classifications, never raw provider exception messages or payloads. */
enum class ProviderTransportFailureKind {
    AUTHENTICATION,
    AUTHORIZATION,
    QUOTA,
    RATE_LIMIT,
    TIMEOUT,
    NETWORK,
    SERVICE,
    INVALID_REQUEST,
    MALFORMED_RESPONSE,
    UNKNOWN,
}

/** An optional status is classification input only; it is not retained in the terminal projection. */
data class ProviderTransportFailureSignal(
    val kind: ProviderTransportFailureKind,
    val statusCode: Int? = null,
) {
    init { require(statusCode == null || statusCode in 100..599) { "状态码不合法。" } }
}

enum class ProviderTransportSafeErrorCode {
    PROVIDER_AUTHENTICATION_FAILED,
    PROVIDER_AUTHORIZATION_FAILED,
    PROVIDER_QUOTA_EXHAUSTED,
    PROVIDER_RATE_LIMITED,
    PROVIDER_TIMEOUT,
    PROVIDER_NETWORK_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    PROVIDER_INVALID_REQUEST,
    PROVIDER_RESPONSE_MALFORMED,
    PROVIDER_UNKNOWN_FAILURE,
    TRANSPORT_CANCELLED,
    TRANSPORT_PROTOCOL_VIOLATION,
    TRANSPORT_DISABLED_NO_NETWORK,
}

object ProviderTransportFailureClassifier {
    fun classify(signal: ProviderTransportFailureSignal): ProviderTransportSafeErrorCode = when (signal.statusCode) {
        401 -> ProviderTransportSafeErrorCode.PROVIDER_AUTHENTICATION_FAILED
        403 -> ProviderTransportSafeErrorCode.PROVIDER_AUTHORIZATION_FAILED
        402 -> ProviderTransportSafeErrorCode.PROVIDER_QUOTA_EXHAUSTED
        408, 504 -> ProviderTransportSafeErrorCode.PROVIDER_TIMEOUT
        413, 400, 422 -> ProviderTransportSafeErrorCode.PROVIDER_INVALID_REQUEST
        429 -> ProviderTransportSafeErrorCode.PROVIDER_RATE_LIMITED
        in 500..599 -> ProviderTransportSafeErrorCode.PROVIDER_UNAVAILABLE
        else -> when (signal.kind) {
            ProviderTransportFailureKind.AUTHENTICATION -> ProviderTransportSafeErrorCode.PROVIDER_AUTHENTICATION_FAILED
            ProviderTransportFailureKind.AUTHORIZATION -> ProviderTransportSafeErrorCode.PROVIDER_AUTHORIZATION_FAILED
            ProviderTransportFailureKind.QUOTA -> ProviderTransportSafeErrorCode.PROVIDER_QUOTA_EXHAUSTED
            ProviderTransportFailureKind.RATE_LIMIT -> ProviderTransportSafeErrorCode.PROVIDER_RATE_LIMITED
            ProviderTransportFailureKind.TIMEOUT -> ProviderTransportSafeErrorCode.PROVIDER_TIMEOUT
            ProviderTransportFailureKind.NETWORK -> ProviderTransportSafeErrorCode.PROVIDER_NETWORK_UNAVAILABLE
            ProviderTransportFailureKind.SERVICE -> ProviderTransportSafeErrorCode.PROVIDER_UNAVAILABLE
            ProviderTransportFailureKind.INVALID_REQUEST -> ProviderTransportSafeErrorCode.PROVIDER_INVALID_REQUEST
            ProviderTransportFailureKind.MALFORMED_RESPONSE -> ProviderTransportSafeErrorCode.PROVIDER_RESPONSE_MALFORMED
            ProviderTransportFailureKind.UNKNOWN -> ProviderTransportSafeErrorCode.PROVIDER_UNKNOWN_FAILURE
        }
    }
}

/** A provider adapter converts its own response protocol to these payload-free control events. */
sealed interface ProviderTransportUpstreamEvent {
    data class StreamStarted(val actualModelId: ProviderTransportModelId? = null) : ProviderTransportUpstreamEvent
    data class TextDelta(val text: String) : ProviderTransportUpstreamEvent {
        init { require(text.isNotEmpty()) { "文本增量不能为空。" } }
    }
    data object Completed : ProviderTransportUpstreamEvent
    data class Failed(val signal: ProviderTransportFailureSignal) : ProviderTransportUpstreamEvent
    data object Cancelled : ProviderTransportUpstreamEvent
}

/** The only event form a future Conversation owner may consume from this boundary. */
sealed interface ProviderTransportNormalizedEvent {
    data class Started(val actualModelId: ProviderTransportModelId?) : ProviderTransportNormalizedEvent
    data class TextDelta(val text: String) : ProviderTransportNormalizedEvent
    data object Completed : ProviderTransportNormalizedEvent
    data class Failed(val safeErrorCode: ProviderTransportSafeErrorCode) : ProviderTransportNormalizedEvent
    data class Cancelled(val safeErrorCode: ProviderTransportSafeErrorCode) : ProviderTransportNormalizedEvent
}

enum class ProviderTransportTerminalOutcome { COMPLETED, FAILED, CANCELLED, DISABLED_NO_NETWORK }

/**
 * Safe runtime projection only. It has no input text, output text, credential, endpoint, raw
 * error, attachment, token, cost, or provider payload field.
 */
data class ProviderTransportSafeRunMetadata(
    val executionId: ConversationRealTextExecutionId,
    val invocationId: InvocationId,
    val attemptId: ProviderAttemptId,
    val requestFingerprint: String,
    val providerHandle: ProviderTransportProviderHandle,
    val requestedModelId: ProviderTransportModelId,
    val actualModelId: ProviderTransportModelId?,
    val terminalOutcome: ProviderTransportTerminalOutcome,
    val safeErrorCode: ProviderTransportSafeErrorCode?,
    val normalizedEventCount: Int,
) {
    init {
        require(requestFingerprint.matches(Regex("[0-9a-f]{64}"))) { "请求指纹必须是小写 SHA-256。" }
        require(normalizedEventCount >= 0) { "规范化事件数量不能为负。" }
        require((terminalOutcome == ProviderTransportTerminalOutcome.COMPLETED) == (safeErrorCode == null)) {
            "成功结果不能有错误码，非成功结果必须有安全错误码。"
        }
        require(terminalOutcome != ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK ||
            safeErrorCode == ProviderTransportSafeErrorCode.TRANSPORT_DISABLED_NO_NETWORK) { "禁用传输必须是无网络安全结果。" }
    }
}

/** A terminal result intentionally exposes metadata only; text deltas never escape through it. */
data class ProviderTransportSafeResult(val metadata: ProviderTransportSafeRunMetadata)

sealed interface ProviderTransportNormalizationResult {
    data class Event(val event: ProviderTransportNormalizedEvent) : ProviderTransportNormalizationResult
    data class Terminal(
        val event: ProviderTransportNormalizedEvent,
        val result: ProviderTransportSafeResult,
    ) : ProviderTransportNormalizationResult
    data class Replayed(val result: ProviderTransportSafeResult) : ProviderTransportNormalizationResult
}

/**
 * A stateful normalizer that rejects out-of-order provider control events as a safe terminal
 * failure. It is pure in-memory logic and makes no provider, credential, or network call.
 */
class ProviderTransportEventNormalizer(private val request: ProviderTransportRequest) {
    private var started = false
    private var actualModelId: ProviderTransportModelId? = null
    private var eventCount = 0
    private var terminal: ProviderTransportSafeResult? = null

    fun accept(upstream: ProviderTransportUpstreamEvent): ProviderTransportNormalizationResult {
        terminal?.let { return ProviderTransportNormalizationResult.Replayed(it) }
        if (!started && upstream !is ProviderTransportUpstreamEvent.StreamStarted) return protocolViolation()
        return when (upstream) {
            is ProviderTransportUpstreamEvent.StreamStarted -> {
                if (started) protocolViolation() else {
                    started = true
                    actualModelId = upstream.actualModelId
                    eventCount += 1
                    ProviderTransportNormalizationResult.Event(ProviderTransportNormalizedEvent.Started(actualModelId))
                }
            }
            is ProviderTransportUpstreamEvent.TextDelta -> event(ProviderTransportNormalizedEvent.TextDelta(upstream.text))
            ProviderTransportUpstreamEvent.Completed -> terminal(ProviderTransportNormalizedEvent.Completed, ProviderTransportTerminalOutcome.COMPLETED, null)
            is ProviderTransportUpstreamEvent.Failed -> terminal(
                ProviderTransportNormalizedEvent.Failed(ProviderTransportFailureClassifier.classify(upstream.signal)),
                ProviderTransportTerminalOutcome.FAILED,
                ProviderTransportFailureClassifier.classify(upstream.signal),
            )
            ProviderTransportUpstreamEvent.Cancelled -> terminal(
                ProviderTransportNormalizedEvent.Cancelled(ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED),
                ProviderTransportTerminalOutcome.CANCELLED,
                ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED,
            )
        }
    }

    /** Explicit cancellation can stop a pending or open stream without synthesizing text. */
    fun cancel(): ProviderTransportNormalizationResult {
        terminal?.let { return ProviderTransportNormalizationResult.Replayed(it) }
        return terminal(
            ProviderTransportNormalizedEvent.Cancelled(ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED),
            ProviderTransportTerminalOutcome.CANCELLED,
            ProviderTransportSafeErrorCode.TRANSPORT_CANCELLED,
        )
    }

    /** A transport can fail before a provider stream exists (for example HTTP status or parsing). */
    fun failBeforeStream(signal: ProviderTransportFailureSignal): ProviderTransportNormalizationResult {
        terminal?.let { return ProviderTransportNormalizationResult.Replayed(it) }
        val error = ProviderTransportFailureClassifier.classify(signal)
        return terminal(
            ProviderTransportNormalizedEvent.Failed(error),
            ProviderTransportTerminalOutcome.FAILED,
            error,
        )
    }

    private fun event(event: ProviderTransportNormalizedEvent): ProviderTransportNormalizationResult {
        eventCount += 1
        return ProviderTransportNormalizationResult.Event(event)
    }

    private fun protocolViolation(): ProviderTransportNormalizationResult = terminal(
        ProviderTransportNormalizedEvent.Failed(ProviderTransportSafeErrorCode.TRANSPORT_PROTOCOL_VIOLATION),
        ProviderTransportTerminalOutcome.FAILED,
        ProviderTransportSafeErrorCode.TRANSPORT_PROTOCOL_VIOLATION,
    )

    private fun terminal(
        event: ProviderTransportNormalizedEvent,
        outcome: ProviderTransportTerminalOutcome,
        error: ProviderTransportSafeErrorCode?,
    ): ProviderTransportNormalizationResult {
        eventCount += 1
        val handle = request.execution
        val result = ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
            executionId = handle.executionId,
            invocationId = handle.invocationId,
            attemptId = handle.attemptId,
            requestFingerprint = handle.requestFingerprint,
            providerHandle = request.route.providerHandle,
            requestedModelId = request.route.modelId,
            actualModelId = actualModelId,
            terminalOutcome = outcome,
            safeErrorCode = error,
            normalizedEventCount = eventCount,
        ))
        terminal = result
        return ProviderTransportNormalizationResult.Terminal(event, result)
    }
}

fun interface ProviderTransportEventSink {
    fun onEvent(event: ProviderTransportNormalizedEvent)
}

interface ProviderTransportCancellationToken {
    fun isCancellationRequested(): Boolean
}

object ProviderTransportNotCancelled : ProviderTransportCancellationToken {
    override fun isCancellationRequested(): Boolean = false
}

/** Future real adapters must implement this explicit boundary; no implementation is registered. */
interface ProviderTransport {
    fun execute(
        request: ProviderTransportRequest,
        cancellation: ProviderTransportCancellationToken,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult
}

/** Default fail-closed transport: no HTTP, no credential access, no event, and no fabricated reply. */
object DisabledNoNetworkProviderTransport : ProviderTransport {
    override fun execute(
        request: ProviderTransportRequest,
        cancellation: ProviderTransportCancellationToken,
        sink: ProviderTransportEventSink,
    ): ProviderTransportSafeResult {
        if (cancellation.isCancellationRequested()) {
            val cancelled = ProviderTransportEventNormalizer(request).cancel()
            if (cancelled is ProviderTransportNormalizationResult.Terminal) sink.onEvent(cancelled.event)
            return (cancelled as ProviderTransportNormalizationResult.Terminal).result
        }
        val handle = request.execution
        return ProviderTransportSafeResult(ProviderTransportSafeRunMetadata(
            executionId = handle.executionId,
            invocationId = handle.invocationId,
            attemptId = handle.attemptId,
            requestFingerprint = handle.requestFingerprint,
            providerHandle = request.route.providerHandle,
            requestedModelId = request.route.modelId,
            actualModelId = null,
            terminalOutcome = ProviderTransportTerminalOutcome.DISABLED_NO_NETWORK,
            safeErrorCode = ProviderTransportSafeErrorCode.TRANSPORT_DISABLED_NO_NETWORK,
            normalizedEventCount = 0,
        ))
    }
}
