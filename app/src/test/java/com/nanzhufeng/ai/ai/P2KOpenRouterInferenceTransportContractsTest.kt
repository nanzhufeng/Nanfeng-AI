package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.AiTask
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.AiTaskRunResult
import com.nanzhufeng.ai.domain.CaptureDraft
import com.nanzhufeng.ai.domain.CaptureDraftId
import com.nanzhufeng.ai.domain.CaptureSourceType
import com.nanzhufeng.ai.domain.CostDisclosure
import com.nanzhufeng.ai.domain.EgressConsent
import com.nanzhufeng.ai.domain.HarnessProfile
import com.nanzhufeng.ai.domain.InMemoryVersionedModelRegistry
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ModelCapabilities
import com.nanzhufeng.ai.domain.ModelDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetMapping
import com.nanzhufeng.ai.domain.ModelPricing
import com.nanzhufeng.ai.domain.ModelRegistrySnapshot
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.RegistrySnapshotSource
import com.nanzhufeng.ai.domain.RegistryVerificationStatus
import com.nanzhufeng.ai.domain.RunAiTaskUseCase
import com.nanzhufeng.ai.domain.SourceEvidence
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2KOpenRouterInferenceTransportContractsTest {
    private val now = Instant.parse("2026-08-12T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `disabled production gate blocks before credential read or transport and records no attempt`() {
        val credentials = TestCredentialStore()
        val transport = QueueTransport()
        val runner = adapter(credentials, transport, OpenRouterEgressPolicy.Disabled)
        val ledger = RecordingLedger()

        val result = RunAiTaskUseCase(runner, ledger, clock).execute(task(draft("safe fixture")), draft("safe fixture"))

        val failure = result as AiTaskRunResult.Failure
        assertEquals(AiTaskError.ProviderEgressNotAuthorized, failure.error)
        assertEquals(InvocationStatus.BLOCKED, failure.invocation.status)
        assertTrue(failure.invocation.taskRun.attempts.isEmpty())
        assertEquals(0, credentials.loadCalls)
        assertEquals(0, transport.calls)
        assertEquals(1, ledger.records.size)
    }

    @Test
    fun `local loopback proves headers authorization json and response projection without official egress`() {
        LocalLoopbackStub(
            status = 200,
            responseBody = """{"id":"local-request","choices":[{"message":{"content":"{\"title\":\" Clean title \",\"body\":\"Clean body\",\"private_debug\":\"drop me\"}"}}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5,"cost":0,"price_version":"catalog-k","currency":"USD"}}""",
        ).use { stub ->
            val credentials = TestCredentialStore()
            val transport = LoopbackTransport(stub.url)
            val runner = adapter(credentials, transport, OpenRouterEgressPolicy.ExplicitlyAuthorizedForFutureUse)
            val localDraft = draft("local-only fixture")

            val result = runner.run(task(localDraft), localDraft) as AiTaskRunResult.Success

            stub.await()
            assertEquals("POST", stub.method)
            assertEquals("Bearer test-key-not-real", stub.header("Authorization"))
            assertEquals("application/json; charset=utf-8", stub.header("Content-Type"))
            assertEquals(result.invocation.taskId.value, stub.header("Idempotency-Key"))
            assertTrue(stub.body.contains("\"model\":\"anthropic/fixture\""))
            assertTrue(stub.body.contains("\"stream\":false"))
            assertEquals("Clean title", result.candidate.title)
            assertEquals("Clean body", result.candidate.body)
            assertFalse(result.invocation.toString().contains("private_debug"))
            assertEquals(0L, result.invocation.cost.totalMicros)
        }
    }

    @Test
    fun `http errors malformed response context overflow cancellation and retry keep structured safe attempts`() {
        val cases = listOf(
            OpenRouterTransportOutcome.HttpResponse(401, "") to AiTaskError.ProviderAuthenticationFailed,
            OpenRouterTransportOutcome.HttpResponse(402, "") to AiTaskError.ProviderBalanceInsufficient,
            OpenRouterTransportOutcome.HttpResponse(408, "") to AiTaskError.ProviderTimedOut,
            OpenRouterTransportOutcome.HttpResponse(413, "") to AiTaskError.ProviderContextOverflow,
            OpenRouterTransportOutcome.HttpResponse(429, "") to AiTaskError.ProviderRateLimited,
            OpenRouterTransportOutcome.HttpResponse(503, "") to AiTaskError.ProviderUnavailable,
            OpenRouterTransportOutcome.TimedOut to AiTaskError.ProviderTimedOut,
            OpenRouterTransportOutcome.Cancelled to AiTaskError.ProviderRequestCancelled,
            OpenRouterTransportOutcome.HttpResponse(200, "{bad") to AiTaskError.ProviderResponseFormatInvalid,
        )
        cases.forEach { (outcome, expected) ->
            val transport = QueueTransport(outcome, outcome)
            val runner = adapter(TestCredentialStore(), transport, OpenRouterEgressPolicy.ExplicitlyAuthorizedForFutureUse)
            val localDraft = draft("safe fixture ${expected.hashCode()}")
            val result = runner.run(task(localDraft), localDraft) as AiTaskRunResult.Failure

            assertEquals(expected, result.error)
            if (expected == AiTaskError.ProviderRequestCancelled) {
                assertEquals(InvocationStatus.CANCELLED, result.invocation.status)
            } else {
                assertEquals(InvocationStatus.FAILED, result.invocation.status)
            }
            assertTrue(result.invocation.taskRun.attempts.isNotEmpty())
            val expectedCalls = if (expected in setOf(AiTaskError.ProviderRateLimited, AiTaskError.ProviderUnavailable, AiTaskError.ProviderTimedOut)) 2 else 1
            assertEquals(expectedCalls, transport.calls)
        }
    }

    @Test
    fun `retry reuses one idempotency key and gate rejects unsafe content or missing cost before any send`() {
        val success = OpenRouterTransportOutcome.HttpResponse(
            200,
            """{"choices":[{"message":{"content":"{\"title\":\"ok\",\"body\":\"body\"}"}}]}""",
        )
        val transport = QueueTransport(OpenRouterTransportOutcome.HttpResponse(503, ""), success)
        val runner = adapter(TestCredentialStore(), transport, OpenRouterEgressPolicy.ExplicitlyAuthorizedForFutureUse)
        val retriedDraft = draft("safe retry fixture")
        val retried = runner.run(task(retriedDraft), retriedDraft) as AiTaskRunResult.Success

        assertEquals(2, transport.calls)
        assertEquals(1, transport.idempotencyKeys.distinct().size)
        assertEquals(2, retried.invocation.taskRun.attempts.size)

        val blockedTransport = QueueTransport()
        val blockedRunner = adapter(TestCredentialStore(), blockedTransport, OpenRouterEgressPolicy.ExplicitlyAuthorizedForFutureUse)
        val unsafe = draft("Authorization: Bearer sk-this-is-not-a-real-key")
        val blocked = RunAiTaskUseCase(blockedRunner, RecordingLedger(), clock).execute(task(unsafe), unsafe) as AiTaskRunResult.Failure
        assertEquals(AiTaskError.ProviderRequestContentUnsafe, blocked.error)
        assertTrue(blocked.invocation.taskRun.attempts.isEmpty())
        assertEquals(0, blockedTransport.calls)

        val noBudget = task(draft("safe"), disclosure = null)
        val budgetBlocked = RunAiTaskUseCase(blockedRunner, RecordingLedger(), clock).execute(noBudget, draft("safe")) as AiTaskRunResult.Failure
        assertEquals(AiTaskError.ProviderBudgetDisclosureRequired, budgetBlocked.error)
        assertTrue(budgetBlocked.invocation.taskRun.attempts.isEmpty())
    }

    @Test
    fun `p2m exact single use policy freezes retry count at zero`() {
        val transport = QueueTransport(
            OpenRouterTransportOutcome.HttpResponse(503, ""),
            OpenRouterTransportOutcome.HttpResponse(200, ""),
        )
        val runner = adapter(
            TestCredentialStore(),
            transport,
            OpenRouterEgressPolicy.ExactSingleUseRun("a".repeat(64)),
            maxAttempts = 1,
        )
        val localDraft = draft("p2m retry freeze fixture")
        val result = runner.run(task(localDraft), localDraft) as AiTaskRunResult.Failure

        assertEquals(AiTaskError.ProviderUnavailable, result.error)
        assertEquals(1, result.invocation.taskRun.attempts.size)
        assertEquals(1, transport.calls)
    }

    private fun adapter(
        credentials: TestCredentialStore,
        transport: OpenRouterInferenceTransport,
        policy: OpenRouterEgressPolicy,
        maxAttempts: Int = OpenRouterInferenceRequestPolicy.MAX_ATTEMPTS,
    ) = OpenRouterInferenceAdapter(
        registry = InMemoryVersionedModelRegistry(listOf(snapshot())),
        credentialStore = credentials,
        transport = transport,
        egressPolicy = policy,
        clock = clock,
        maxAttempts = maxAttempts,
    )

    private fun snapshot(): ModelRegistrySnapshot = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("snapshot-k"), schemaVersion = 1, providerId = ProviderId.OPENROUTER,
        catalogVersion = "catalog-k", source = RegistrySnapshotSource.OPENROUTER_CATALOG, capturedAt = now,
        verificationStatus = RegistryVerificationStatus.VERIFIED, lastVerifiedAt = now,
        models = listOf(model()),
        presetMappings = ModelPresetId.entries.map { ModelPresetMapping(it, "anthropic/fixture") },
        catalogSha256 = "a".repeat(64),
    )

    private fun model() = ModelDescriptor(
        id = "anthropic/fixture", displayName = "Fixture", contextWindowTokens = 4096,
        capabilities = ModelCapabilities(true, false, false, supportsStructuredOutput = true),
        pricing = ModelPricing("catalog-k", "USD", 1, 1),
    )

    private fun draft(text: String) = CaptureDraft(
        id = CaptureDraftId("draft-${text.hashCode()}"), text = text, attachments = emptyList(), createdAt = now,
        sourceEvidence = listOf(SourceEvidence(CaptureSourceType.MANUAL_TEXT, now, contributedFields = setOf("text"))),
    )

    private fun task(draft: CaptureDraft, disclosure: CostDisclosure? = CostDisclosure(now, "catalog-k", "USD", null)) = AiTask(
        id = AiTaskId("task-${draft.id.value}"), draftId = draft.id, providerId = ProviderId.OPENROUTER,
        model = model(), harness = HarnessProfile("capture-organize-openrouter", 1), createdAt = now,
        consent = EgressConsent(now, ProviderId.OPENROUTER, model().id, draft.egressFingerprint(), disclosure),
    )
}

private class TestCredentialStore : ProviderCredentialStore {
    var loadCalls = 0
    override fun hasCredential(providerId: ProviderId): Boolean = providerId == ProviderId.OPENROUTER
    override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = true
    override fun loadCredential(providerId: ProviderId): CharArray? = "test-key-not-real".toCharArray().also { loadCalls += 1 }
}

private class RecordingLedger : InvocationRepository {
    val records = mutableListOf<InvocationRecord>()
    override fun save(record: InvocationRecord): InvocationRecord = record.also(records::add)
    override fun findById(id: com.nanzhufeng.ai.domain.InvocationId): InvocationRecord? = records.firstOrNull { it.id == id }
    override fun listNewestFirst(): List<InvocationRecord> = records
}

private class QueueTransport(vararg outcomes: OpenRouterTransportOutcome) : OpenRouterInferenceTransport {
    private val queue = ArrayDeque(outcomes.toList())
    var calls = 0
    val idempotencyKeys = mutableListOf<String>()
    override fun execute(request: OpenRouterTransportRequest, credential: CharArray, cancellation: OpenRouterCancellationSignal): OpenRouterTransportOutcome {
        calls += 1
        idempotencyKeys += request.idempotencyKey
        return queue.removeFirstOrNull() ?: OpenRouterTransportOutcome.NetworkFailure
    }
}

/** Test-only loopback transport. It never knows the official endpoint and never leaves 127.0.0.1. */
private class LoopbackTransport(private val endpoint: String) : OpenRouterInferenceTransport {
    override fun execute(request: OpenRouterTransportRequest, credential: CharArray, cancellation: OpenRouterCancellationSignal): OpenRouterTransportOutcome {
        if (cancellation.isCancelled()) return OpenRouterTransportOutcome.Cancelled
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 2_000; readTimeout = 2_000; doOutput = true
            setFixedLengthStreamingMode(request.jsonBody.toByteArray(Charsets.UTF_8).size)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${credential.concatToString()}")
            setRequestProperty("Idempotency-Key", request.idempotencyKey)
        }
        return try {
            connection.outputStream.use { it.write(request.jsonBody.toByteArray()) }
            val code = connection.responseCode
            val body = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            OpenRouterTransportOutcome.HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}

private class LocalLoopbackStub(private val status: Int, private val responseBody: String) : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val completion = Object()
    @Volatile private var complete = false
    @Volatile var method: String? = null
    @Volatile var bodyReceived: String = ""
    private val headers = linkedMapOf<String, String>()
    val url: String = "http://127.0.0.1:${server.localPort}/v1/chat/completions"
    val body: String get() = bodyReceived

    init {
        thread(isDaemon = true, name = "p2k-loopback") {
            server.accept().use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val headerText = input.readHeaderBlock()
                val lines = headerText.split("\r\n")
                method = lines.first().substringBefore(' ')
                lines.drop(1).filter { ':' in it }.forEach { line ->
                    val (name, value) = line.split(':', limit = 2)
                    headers[name.trim().lowercase()] = value.trim()
                }
                val length = headers["content-length"]?.toInt() ?: 0
                bodyReceived = input.readExact(length).toString(Charsets.UTF_8)
                socket.getOutputStream().write(
                    "HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nContent-Length: ${responseBody.toByteArray().size}\r\nConnection: close\r\n\r\n$responseBody"
                        .toByteArray(),
                )
                socket.getOutputStream().flush()
            }
            synchronized(completion) { complete = true; completion.notifyAll() }
        }
    }

    fun header(name: String): String? = headers[name.lowercase()]
    fun await() = synchronized(completion) {
        if (!complete) completion.wait(2_000)
        assertTrue("loopback server did not receive a request", complete)
    }

    override fun close() = server.close()
}

private fun BufferedInputStream.readHeaderBlock(): String {
    val output = StringBuilder()
    var previous = '\u0000'
    var current: Char
    do {
        current = read().toChar()
        output.append(current)
        if (output.endsWith("\r\n\r\n")) break
        previous = current
    } while (previous.code >= 0 && output.length < 16_384)
    return output.toString().removeSuffix("\r\n\r\n")
}

private fun BufferedInputStream.readExact(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) error("unexpected loopback EOF")
        offset += read
    }
    return bytes
}
