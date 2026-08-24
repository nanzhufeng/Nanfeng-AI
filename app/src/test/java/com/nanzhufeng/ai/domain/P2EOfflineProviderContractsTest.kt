package com.nanzhufeng.ai.domain

import com.nanzhufeng.ai.ai.OpenRouterAdapterDecodeResult
import com.nanzhufeng.ai.ai.OpenRouterAdapterEncodeResult
import com.nanzhufeng.ai.ai.OpenRouterChatCompletionRequest
import com.nanzhufeng.ai.ai.OpenRouterJsonCodec
import com.nanzhufeng.ai.ai.OpenRouterMessage
import com.nanzhufeng.ai.ai.OpenRouterMessageRole
import com.nanzhufeng.ai.ai.OpenRouterOfflineAdapterContract
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2EOfflineProviderContractsTest {
    private val now = Instant.parse("2026-08-12T00:00:00Z")

    @Test
    fun `only a verified snapshot becomes current and prior stable is retained`() {
        val registry = InMemoryVersionedModelRegistry()
        val unverified = snapshot("unverified", RegistryVerificationStatus.UNVERIFIED, null)

        assertEquals(
            ModelRegistryPublicationResult.Rejected(AiTaskError.ModelRegistrySnapshotInvalid),
            registry.publishVerified(unverified),
        )
        val v1 = snapshot("catalog-1", RegistryVerificationStatus.VERIFIED, now)
        val v2 = snapshot("catalog-2", RegistryVerificationStatus.VERIFIED, now.plusSeconds(60))

        assertTrue(registry.publishVerified(v1) is ModelRegistryPublicationResult.Published)
        assertEquals("catalog-1", (registry.resolve(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_FABLE_5)
            as ModelRegistryResolution.Resolved).snapshot.catalogVersion)
        assertTrue(registry.publishVerified(v2) is ModelRegistryPublicationResult.Published)
        assertEquals("catalog-1", registry.previousStableSnapshot(ProviderId.OPENROUTER)!!.catalogVersion)
    }

    @Test
    fun `offline request codec is deterministic structured and never streams`() {
        val encoded = OpenRouterJsonCodec().encodeRequest(OpenRouterChatCompletionRequest(
            modelId = "fixture/structured",
            messages = listOf(OpenRouterMessage(OpenRouterMessageRole.USER, "line one\n\"quoted\"")),
            outputContractVersion = 3,
        ))

        assertEquals(
            "{\"model\":\"fixture/structured\",\"messages\":[{\"role\":\"user\",\"content\":\"line one\\n\\\"quoted\\\"\"}],\"stream\":false,\"temperature\":0.2,\"response_format\":{\"type\":\"json_object\"},\"metadata\":{\"nanfeng_output_contract_version\":3}}",
            encoded.jsonBody,
        )
    }

    @Test
    fun `response codec preserves unknown fields as null and verified zero cost as zero`() {
        val codec = OpenRouterJsonCodec()
        val missing = codec.decodeResponse("{\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"ok\\\"}\"}}]}")
            as OpenRouterAdapterDecodeResult.Decoded
        assertNull(missing.response.usage.inputTokens)
        assertTrue(missing.response.cost.isUnknown)

        val known = codec.decodeResponse(
            "{\"id\":\"fixture-request\",\"choices\":[{\"message\":{\"content\":\"{\\\"title\\\":\\\"ok\\\"}\"}}],\"usage\":{\"prompt_tokens\":0,\"completion_tokens\":7,\"total_tokens\":7,\"cost\":0,\"currency\":\"USD\",\"price_version\":\"catalog-1\"}}",
        ) as OpenRouterAdapterDecodeResult.Decoded
        assertEquals(0L, known.response.usage.inputTokens)
        assertEquals(0L, known.response.cost.totalMicros)
        assertTrue(known.response.cost.isVerifiedFreeOrLocal)
    }

    @Test
    fun `chat reply survives fine grained price metadata and typed text parts`() {
        val decoded = OpenRouterJsonCodec().decodeResponse(
            """{"choices":[{"message":{"content":[{"type":"text","text":"正常回复"}]}}],"usage":{"prompt_tokens":12,"completion_tokens":8,"cost":0.0000001}}""",
        ) as OpenRouterAdapterDecodeResult.Decoded

        assertEquals("正常回复", decoded.response.structuredContent)
        assertTrue(decoded.response.cost.isUnknown)
        assertEquals(12L, decoded.response.usage.inputTokens)
        assertEquals(8L, decoded.response.usage.outputTokens)
    }

    @Test
    fun `adapter refuses unverified registry instead of creating a transport request`() {
        val registry = InMemoryVersionedModelRegistry()
        val adapter = OpenRouterOfflineAdapterContract(registry)

        val result = adapter.encode(ModelPresetId.CLAUDE_FABLE_5, request())

        assertEquals(
            OpenRouterAdapterEncodeResult.Rejected(AiTaskError.ModelRegistrySnapshotUnverified),
            result,
        )
    }

    @Test
    fun `invocation records a task run attempt generation and validation without sensitive content`() {
        val record = InvocationRecord(
            id = InvocationId("invocation-1"),
            taskId = AiTaskId("task-1"),
            providerId = ProviderId.OPENROUTER,
            modelId = "fixture/structured",
            harnessVersion = 3,
            completedAt = now,
            status = InvocationStatus.SUCCEEDED,
            registrySnapshotId = ModelRegistrySnapshotId("snapshot-1"),
            pricingVersion = "catalog-1",
            usage = ProviderUsage(inputTokens = 10, outputTokens = 5, totalTokens = 15),
            cost = ProviderCost(priceVersion = "catalog-1", currencyCode = "USD", totalMicros = 0),
        )

        val attempt = record.taskRun.attempts.single()
        assertEquals(TaskRunStatus.SUCCEEDED, record.taskRun.status)
        assertEquals(ProviderAttemptStatus.SUCCEEDED, attempt.status)
        assertEquals(GenerationStatus.COMPLETED, attempt.generation!!.status)
        assertEquals(ValidationStatus.PASSED, attempt.generation!!.validation.status)
        assertEquals(record.usage, attempt.usage)
        assertTrue(record.toString().contains("fixture/structured"))
    }

    private fun request(): OpenRouterChatCompletionRequest = OpenRouterChatCompletionRequest(
        modelId = "fixture/structured",
        messages = listOf(OpenRouterMessage(OpenRouterMessageRole.USER, "fixture only")),
        outputContractVersion = 1,
    )

    private fun snapshot(
        version: String,
        status: RegistryVerificationStatus,
        verifiedAt: Instant?,
    ): ModelRegistrySnapshot = ModelRegistrySnapshot(
        id = ModelRegistrySnapshotId("snapshot-$version"),
        schemaVersion = 1,
        providerId = ProviderId.OPENROUTER,
        catalogVersion = version,
        source = RegistrySnapshotSource.LOCAL_FIXTURE,
        capturedAt = now,
        verificationStatus = status,
        lastVerifiedAt = verifiedAt,
        models = listOf(ModelDescriptor(
            id = "fixture/structured",
            displayName = "Fixture structured model",
            capabilities = ModelCapabilities(true, false, false, supportsStructuredOutput = true),
            contextWindowTokens = 4096,
            pricing = ModelPricing(priceVersion = version, currencyCode = "USD", inputMicrosPerToken = 0, outputMicrosPerToken = 0),
        )),
        presetMappings = listOf(ModelPresetMapping(ModelPresetId.CLAUDE_FABLE_5, "fixture/structured")),
    )
}
