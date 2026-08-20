package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.AiTaskRunner
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.CostDisclosure
import com.nanzhufeng.ai.domain.EgressConsent
import com.nanzhufeng.ai.domain.GeneratedCandidate
import com.nanzhufeng.ai.domain.GenerationId
import com.nanzhufeng.ai.domain.GenerationRecord
import com.nanzhufeng.ai.domain.GenerationStatus
import com.nanzhufeng.ai.domain.GenerationValidation
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ProviderAttempt
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderAttemptStatus
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.TaskRun
import com.nanzhufeng.ai.domain.TaskRunId
import com.nanzhufeng.ai.domain.TaskRunStatus
import com.nanzhufeng.ai.domain.ValidationId
import com.nanzhufeng.ai.domain.ValidationStatus
import com.nanzhufeng.ai.domain.VersionedModelRegistry
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * P2-K's production inference path is deliberately disabled unless a later, user-authorized
 * phase supplies an enabled policy. The application container always uses [Disabled].
 */
sealed interface OpenRouterEgressPolicy {
    data object Disabled : OpenRouterEgressPolicy
    data object ExplicitlyAuthorizedForFutureUse : OpenRouterEgressPolicy
    /**
     * P2-M is not a setting and cannot be reused as one.  This policy is created only by the
     * single-run acceptance executor after its exact RunSpec nonce has been consumed.
     */
    data class ExactSingleUseRun(val runSpecFingerprint: String) : OpenRouterEgressPolicy {
        init { require(runSpecFingerprint.matches(Regex("[0-9a-f]{64}"))) }
    }
}

object OpenRouterInferenceRequestPolicy {
    const val URL = "https://openrouter.ai/api/v1/chat/completions"
    const val METHOD = "POST"
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 30_000
    const val MAX_RESPONSE_BYTES = 1 * 1024 * 1024
    const val MAX_ATTEMPTS = 2
}

/** Contains sensitive payload only while an Adapter actively invokes a transport. Never persist or log it. */
data class OpenRouterTransportRequest(
    val jsonBody: String,
    val idempotencyKey: String,
)

sealed interface OpenRouterTransportOutcome {
    data class HttpResponse(val statusCode: Int, val responseBody: String) : OpenRouterTransportOutcome
    data object Cancelled : OpenRouterTransportOutcome
    data object TimedOut : OpenRouterTransportOutcome
    data object NetworkFailure : OpenRouterTransportOutcome
    data object ResponseTooLarge : OpenRouterTransportOutcome
}

fun interface OpenRouterCancellationSignal {
    fun isCancelled(): Boolean
}

/** The credential is supplied as a short-lived CharArray and is never part of [OpenRouterTransportRequest]. */
interface OpenRouterInferenceTransport {
    fun execute(
        request: OpenRouterTransportRequest,
        credential: CharArray,
        cancellation: OpenRouterCancellationSignal,
    ): OpenRouterTransportOutcome
}

/** Fixed official HTTPS transport. Tests use a fake/loopback implementation, never this class. */
class OfficialOpenRouterInferenceTransport : OpenRouterInferenceTransport {
    override fun execute(
        request: OpenRouterTransportRequest,
        credential: CharArray,
        cancellation: OpenRouterCancellationSignal,
    ): OpenRouterTransportOutcome {
        if (cancellation.isCancelled()) return OpenRouterTransportOutcome.Cancelled
        return runCatching {
            val connection = (java.net.URL(OpenRouterInferenceRequestPolicy.URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = OpenRouterInferenceRequestPolicy.METHOD
                instanceFollowRedirects = false
                doInput = true
                doOutput = true
                useCaches = false
                connectTimeout = OpenRouterInferenceRequestPolicy.CONNECT_TIMEOUT_MS
                readTimeout = OpenRouterInferenceRequestPolicy.READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Idempotency-Key", request.idempotencyKey)
            }
            try {
                // This String only exists while setting the in-memory request header; it is never logged or stored.
                connection.setRequestProperty("Authorization", "Bearer ${credential.concatToString()}")
                connection.outputStream.use { it.write(request.jsonBody.toByteArray(Charsets.UTF_8)) }
                if (cancellation.isCancelled()) return OpenRouterTransportOutcome.Cancelled
                val status = connection.responseCode
                if (status !in 200..299) return OpenRouterTransportOutcome.HttpResponse(status, "")
                val body = connection.inputStream.use { it.readAtMost(OpenRouterInferenceRequestPolicy.MAX_RESPONSE_BYTES) }
                    ?: return OpenRouterTransportOutcome.ResponseTooLarge
                if (cancellation.isCancelled()) OpenRouterTransportOutcome.Cancelled
                else OpenRouterTransportOutcome.HttpResponse(status, body.toString(Charsets.UTF_8))
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            when (error) {
                is SocketTimeoutException -> OpenRouterTransportOutcome.TimedOut
                else -> OpenRouterTransportOutcome.NetworkFailure
            }
        }
    }
}

/**
 * Explicitly local-only request cleansing. It rejects common credential and direct-identifier
 * shapes instead of silently forwarding them. The returned text is held in memory for one call.
 */
class OpenRouterRequestSanitizer {
    fun sanitize(draft: CaptureDraft): String? {
        if (draft.attachments.isNotEmpty()) return null // P2-K contracts text transport only; image encoding is a later gate.
        val cleaned = draft.text.orEmpty()
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            .trim()
        if (cleaned.isEmpty() || cleaned.length > MAX_TEXT_LENGTH) return null
        if (SENSITIVE_MARKERS.any { it.containsMatchIn(cleaned) }) return null
        return cleaned
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 12_000
        val SENSITIVE_MARKERS = listOf(
            Regex("(?i)\\b(authorization|api[ _-]?key|bearer)\\b"),
            Regex("(?i)\\bsk-[a-z0-9_-]{12,}"),
            Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        )
    }
}

/**
 * The only P2-K Adapter capable of constructing an inference transport request. It owns
 * provider-specific HTTP/error/retry details; callers only see domain results and safe metadata.
 */
class OpenRouterInferenceAdapter(
    private val registry: VersionedModelRegistry,
    private val credentialStore: ProviderCredentialStore,
    private val transport: OpenRouterInferenceTransport,
    private val egressPolicy: OpenRouterEgressPolicy,
    private val codec: OpenRouterJsonCodec = OpenRouterJsonCodec(),
    private val sanitizer: OpenRouterRequestSanitizer = OpenRouterRequestSanitizer(),
    private val clock: Clock,
    private val cancellation: OpenRouterCancellationSignal = OpenRouterCancellationSignal { false },
    private val maxAttempts: Int = OpenRouterInferenceRequestPolicy.MAX_ATTEMPTS,
    private val credentialValidator: (CharArray) -> Boolean = { true },
) : AiTaskRunner {
    private val consumedTaskIds = ConcurrentHashMap.newKeySet<String>()

    init { require(maxAttempts in 1..OpenRouterInferenceRequestPolicy.MAX_ATTEMPTS) }

    override fun preflight(task: AiTask, draft: CaptureDraft): AiTaskError? {
        if (task.providerId != ProviderId.OPENROUTER) return AiTaskError.ProviderConfigurationInvalid
        if (egressPolicy !is OpenRouterEgressPolicy.ExplicitlyAuthorizedForFutureUse &&
            egressPolicy !is OpenRouterEgressPolicy.ExactSingleUseRun
        ) {
            return AiTaskError.ProviderEgressNotAuthorized
        }
        if (!hasCurrentConsent(task.consent, task, draft)) return AiTaskError.ConsentRequired
        val snapshot = registry.currentSnapshot(ProviderId.OPENROUTER)
            ?: return AiTaskError.ModelRegistrySnapshotUnverified
        val verifiedModel = snapshot.models.firstOrNull { it.id == task.model.id }
            ?: return AiTaskError.ModelRegistryModelUnavailable
        if (!task.model.capabilities.supportsText ||
            !task.model.capabilities.supportsStructuredOutput
        ) return AiTaskError.ModelRegistryModelUnavailable
        if (!matchesCostDisclosure(task.consent!!.costDisclosure, verifiedModel.pricing.priceVersion, verifiedModel.pricing.currencyCode)) {
            return AiTaskError.ProviderBudgetDisclosureRequired
        }
        if (sanitizer.sanitize(draft) == null) return AiTaskError.ProviderRequestContentUnsafe
        if (!credentialStore.hasCredential(ProviderId.OPENROUTER)) return AiTaskError.ProviderCredentialMissing
        if (consumedTaskIds.contains(task.id.value)) return AiTaskError.ConsentRequired
        return null
    }

    override fun run(task: AiTask, draft: CaptureDraft): AiTaskRunResult {
        // RunAiTaskUseCase normally evaluates this first; repeating it protects direct callers too.
        preflight(task, draft)?.let { return failedWithoutTransport(task, it) }
        // This final guard makes duplicate taps safe.
        if (!consumedTaskIds.add(task.id.value)) return failedWithoutTransport(task, AiTaskError.ConsentRequired)
        val snapshot = registry.currentSnapshot(ProviderId.OPENROUTER)
            ?: return failedWithoutTransport(task, AiTaskError.ModelRegistrySnapshotUnverified)
        if (snapshot.models.none { it.id == task.model.id }) {
            return failedWithoutTransport(task, AiTaskError.ModelRegistryModelUnavailable)
        }
        val text = sanitizer.sanitize(draft) ?: return failedWithoutTransport(task, AiTaskError.ProviderRequestContentUnsafe)
        val credential = credentialStore.loadCredential(ProviderId.OPENROUTER)
            ?: return failedWithoutTransport(task, AiTaskError.ProviderCredentialMissing)
        if (!credentialValidator(credential)) {
            credential.fill('\u0000')
            return failedWithoutTransport(task, AiTaskError.ProviderCredentialInvalid)
        }
        val encoded = codec.encodeRequest(
            OpenRouterChatCompletionRequest(
                modelId = task.model.id,
                messages = listOf(OpenRouterMessage(OpenRouterMessageRole.USER, text)),
                outputContractVersion = task.harness.version,
            ),
        )
        try {
            return executeWithRetry(task, snapshot, encoded.jsonBody, credential)
        } finally {
            credential.fill('\u0000')
        }
    }

    private fun executeWithRetry(
        task: AiTask,
        snapshot: ModelRegistrySnapshot,
        jsonBody: String,
        credential: CharArray,
    ): AiTaskRunResult {
        val attempts = mutableListOf<ProviderAttempt>()
        repeat(maxAttempts) { index ->
            val startedAt = clock.instant()
            val outcome = transport.execute(
                OpenRouterTransportRequest(jsonBody = jsonBody, idempotencyKey = task.id.value),
                credential,
                cancellation,
            )
            val completedAt = clock.instant()
            when (outcome) {
                is OpenRouterTransportOutcome.HttpResponse -> if (outcome.statusCode in 200..299) {
                    val decoded = codec.decodeResponse(outcome.responseBody) as? OpenRouterAdapterDecodeResult.Decoded
                    val candidate = decoded?.response?.let { codec.sanitizeCandidate(it.structuredContent) }
                    if (decoded == null || candidate == null) {
                        attempts += failedAttempt(task, snapshot, index + 1, startedAt, completedAt, AiTaskError.ProviderResponseFormatInvalid)
                        return failure(task, snapshot, attempts, AiTaskError.ProviderResponseFormatInvalid, completedAt)
                    }
                    val generation = GenerationRecord(
                        id = GenerationId("${task.id.value}:generation:${index + 1}"),
                        createdAt = completedAt,
                        status = GenerationStatus.COMPLETED,
                        validation = GenerationValidation(
                            id = ValidationId("${task.id.value}:validation:${index + 1}"),
                            completedAt = completedAt,
                            status = ValidationStatus.PASSED,
                            outputContractVersion = task.harness.version,
                        ),
                    )
                    attempts += ProviderAttempt(
                        id = ProviderAttemptId("${task.id.value}:attempt:${index + 1}"),
                        providerId = ProviderId.OPENROUTER,
                        modelId = task.model.id,
                        registrySnapshotId = snapshot.id,
                        startedAt = startedAt,
                        completedAt = completedAt,
                        status = ProviderAttemptStatus.SUCCEEDED,
                        usage = decoded.response.usage,
                        cost = decoded.response.cost,
                        generation = generation,
                    )
                    return success(task, snapshot, attempts, candidate, decoded.response.usage, decoded.response.cost, completedAt)
                } else {
                    val error = OpenRouterErrorMapper.fromHttpStatus(outcome.statusCode)
                    attempts += failedAttempt(task, snapshot, index + 1, startedAt, completedAt, error)
                    if (isRetryable(outcome.statusCode) && index + 1 < maxAttempts) return@repeat
                    return failure(task, snapshot, attempts, error, completedAt)
                }
                OpenRouterTransportOutcome.Cancelled -> {
                    attempts += cancelledAttempt(task, snapshot, index + 1, startedAt, completedAt)
                    return cancelled(task, snapshot, attempts, completedAt)
                }
                OpenRouterTransportOutcome.TimedOut -> {
                    attempts += failedAttempt(task, snapshot, index + 1, startedAt, completedAt, AiTaskError.ProviderTimedOut)
                    if (index + 1 < maxAttempts) return@repeat
                    return failure(task, snapshot, attempts, AiTaskError.ProviderTimedOut, completedAt)
                }
                OpenRouterTransportOutcome.NetworkFailure -> {
                    attempts += failedAttempt(task, snapshot, index + 1, startedAt, completedAt, AiTaskError.ProviderNetworkUnavailable)
                    return failure(task, snapshot, attempts, AiTaskError.ProviderNetworkUnavailable, completedAt)
                }
                OpenRouterTransportOutcome.ResponseTooLarge -> {
                    attempts += failedAttempt(task, snapshot, index + 1, startedAt, completedAt, AiTaskError.ProviderResponseFormatInvalid)
                    return failure(task, snapshot, attempts, AiTaskError.ProviderResponseFormatInvalid, completedAt)
                }
            }
        }
        return failure(task, snapshot, attempts, AiTaskError.ProviderFailure, clock.instant())
    }

    private fun success(
        task: AiTask,
        snapshot: ModelRegistrySnapshot,
        attempts: List<ProviderAttempt>,
        candidate: SanitizedOpenRouterCandidate,
        usage: ProviderUsage,
        cost: ProviderCost,
        completedAt: Instant,
    ): AiTaskRunResult.Success {
        val invocation = invocation(task, snapshot, attempts, InvocationStatus.SUCCEEDED, null, usage, cost, completedAt)
        return AiTaskRunResult.Success(
            candidate = GeneratedCandidate(CandidateId.new(), task.id, candidate.title, candidate.body, completedAt),
            invocation = invocation,
        )
    }

    private fun failure(
        task: AiTask,
        snapshot: ModelRegistrySnapshot,
        attempts: List<ProviderAttempt>,
        error: AiTaskError,
        completedAt: Instant,
    ): AiTaskRunResult.Failure = AiTaskRunResult.Failure(
        invocation(task, snapshot, attempts, InvocationStatus.FAILED, error, ProviderUsage(), ProviderCost(), completedAt), error,
    )

    private fun cancelled(
        task: AiTask,
        snapshot: ModelRegistrySnapshot,
        attempts: List<ProviderAttempt>,
        completedAt: Instant,
    ): AiTaskRunResult.Failure = AiTaskRunResult.Failure(
        invocation(task, snapshot, attempts, InvocationStatus.CANCELLED, AiTaskError.ProviderRequestCancelled, ProviderUsage(), ProviderCost(), completedAt),
        AiTaskError.ProviderRequestCancelled,
    )

    private fun invocation(
        task: AiTask,
        snapshot: ModelRegistrySnapshot,
        attempts: List<ProviderAttempt>,
        status: InvocationStatus,
        error: AiTaskError?,
        usage: ProviderUsage,
        cost: ProviderCost,
        completedAt: Instant,
    ) = InvocationRecord(
        id = InvocationId.new(),
        taskId = task.id,
        providerId = ProviderId.OPENROUTER,
        modelId = task.model.id,
        harnessVersion = task.harness.version,
        completedAt = completedAt,
        status = status,
        error = error,
        registrySnapshotId = snapshot.id,
        pricingVersion = cost.priceVersion ?: task.model.pricing.priceVersion,
        usage = usage,
        cost = cost,
        taskRun = TaskRun(
            id = TaskRunId(task.id.value), taskId = task.id,
            startedAt = attempts.first().startedAt, completedAt = completedAt,
            status = when (status) {
                InvocationStatus.SUCCEEDED -> TaskRunStatus.SUCCEEDED
                InvocationStatus.FAILED -> TaskRunStatus.FAILED
                InvocationStatus.CANCELLED -> TaskRunStatus.CANCELLED
                InvocationStatus.BLOCKED -> TaskRunStatus.BLOCKED
            },
            attempts = attempts,
        ),
    )

    private fun failedWithoutTransport(task: AiTask, error: AiTaskError): AiTaskRunResult.Failure {
        val now = clock.instant()
        return AiTaskRunResult.Failure(
            InvocationRecord(
                id = InvocationId.new(), taskId = task.id, providerId = task.providerId, modelId = task.model.id,
                harnessVersion = task.harness.version, completedAt = now, status = InvocationStatus.BLOCKED, error = error,
            ), error,
        )
    }

    private fun failedAttempt(
        task: AiTask, snapshot: ModelRegistrySnapshot, position: Int, startedAt: Instant, completedAt: Instant, error: AiTaskError,
    ) = ProviderAttempt(
        id = ProviderAttemptId("${task.id.value}:attempt:$position"),
        providerId = ProviderId.OPENROUTER, modelId = task.model.id, registrySnapshotId = snapshot.id,
        startedAt = startedAt, completedAt = completedAt, status = ProviderAttemptStatus.FAILED, error = error,
    )

    private fun cancelledAttempt(task: AiTask, snapshot: ModelRegistrySnapshot, position: Int, startedAt: Instant, completedAt: Instant) = ProviderAttempt(
        id = ProviderAttemptId("${task.id.value}:attempt:$position"),
        providerId = ProviderId.OPENROUTER, modelId = task.model.id, registrySnapshotId = snapshot.id,
        startedAt = startedAt, completedAt = completedAt, status = ProviderAttemptStatus.CANCELLED,
        error = AiTaskError.ProviderRequestCancelled,
    )

    private fun hasCurrentConsent(consent: EgressConsent?, task: AiTask, draft: CaptureDraft): Boolean = consent != null &&
        consent.providerId == ProviderId.OPENROUTER && consent.modelId == task.model.id &&
        consent.contentFingerprint == draft.egressFingerprint()

    private fun matchesCostDisclosure(disclosure: CostDisclosure?, priceVersion: String?, currencyCode: String?): Boolean = disclosure != null &&
        disclosure.priceVersion == priceVersion && disclosure.currencyCode == currencyCode

    private fun isRetryable(statusCode: Int): Boolean = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}

private fun java.io.InputStream.readAtMost(limit: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return output.toByteArray()
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
}
