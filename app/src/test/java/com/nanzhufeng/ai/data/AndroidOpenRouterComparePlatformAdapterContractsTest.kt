package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.ai.OpenAiCompatibleHttpOutcome
import com.nanzhufeng.ai.ai.OpenAiCompatibleHttpRequest
import com.nanzhufeng.ai.ai.OpenAiCompatibleProviderPreset
import com.nanzhufeng.ai.domain.CompareBranchExecutionGrant
import com.nanzhufeng.ai.domain.CompareBranchId
import com.nanzhufeng.ai.domain.CompareExecutionGrantedPlan
import com.nanzhufeng.ai.domain.LogicalModelId
import com.nanzhufeng.ai.domain.ModelDeploymentId
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderHandle
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderTransportNotCancelled
import com.nanzhufeng.ai.domain.CredentialPresence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOpenRouterComparePlatformAdapterContractsTest {
    private val now = Instant.parse("2026-08-16T11:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `construction from current confirmed grants reads neither key nor network`() {
        val credentials = Credentials()
        val connections = Connections()
        val adapter = AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted(), credentials, clock, connections)

        assertNotNull(adapter)
        assertEquals(0, credentials.loads)
        assertEquals(0, connections.opens)
        assertFalse(adapter!!.handleReference.contains("provider/gpt"))
    }

    @Test fun `expired or non compare grant cannot create a platform adapter`() {
        assertNull(AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted(expiresAt = now), Credentials(), clock, Connections()))
        assertNull(AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted(logicalModels = listOf("logical.chatgpt", "logical.other")), Credentials(), clock, Connections()))
    }

    @Test fun `only a confirmed provider model can load a key and each branch is one shot`() {
        val credentials = Credentials()
        val connections = Connections()
        val adapter = requireNotNull(AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted(), credentials, clock, connections))

        assertEquals(OpenAiCompatibleHttpOutcome.DisabledNoNetwork, adapter.execute(request("provider/other"), adapter, ProviderTransportNotCancelled))
        assertEquals(0, credentials.loads)
        assertEquals(0, connections.opens)

        val first = adapter.execute(request("provider/gpt"), adapter, ProviderTransportNotCancelled)
        assertTrue(first is OpenAiCompatibleHttpOutcome.Response)
        assertEquals(1, credentials.loads)
        assertEquals(1, connections.opens)
        assertEquals(OpenAiCompatibleHttpOutcome.DisabledNoNetwork, adapter.execute(request("provider/gpt"), adapter, ProviderTransportNotCancelled))
        assertEquals(1, credentials.loads)
        assertEquals(1, connections.opens)
        assertTrue(connections.connection.disconnected)
        assertEquals(setOf("Accept", "Content-Type", "Idempotency-Key", "Authorization"), connections.connection.headers.keys)
    }

    @Test fun `cancellation before platform dispatch does not read key or open a connection`() {
        val credentials = Credentials()
        val connections = Connections()
        val adapter = requireNotNull(AndroidOpenRouterComparePlatformAdapter.createForConfirmedCompare(granted(), credentials, clock, connections))
        val cancelled = object : com.nanzhufeng.ai.domain.ProviderTransportCancellationToken {
            override fun isCancellationRequested(): Boolean = true
        }

        assertEquals(OpenAiCompatibleHttpOutcome.DisabledNoNetwork, adapter.execute(request("provider/gpt"), adapter, cancelled))
        assertEquals(0, credentials.loads)
        assertEquals(0, connections.opens)
    }

    private fun granted(
        expiresAt: Instant = now.plusSeconds(300),
        logicalModels: List<String> = listOf("logical.chatgpt", "logical.claude"),
    ) = CompareExecutionGrantedPlan(
        confirmationId = "compare-confirmation",
        requestFingerprint = "a".repeat(64), contextHash = "b".repeat(64), textSha256 = "c".repeat(64), expiresAt = expiresAt,
        branchGrants = listOf(
            grant("chatgpt", logicalModels[0], "provider/gpt", expiresAt),
            grant("claude", logicalModels[1], "provider/claude", expiresAt),
        ),
    )

    private fun grant(branch: String, logical: String, model: String, expiresAt: Instant) = CompareBranchExecutionGrant(
        grantId = "grant-$branch", branchId = CompareBranchId(branch), requestFingerprint = "a".repeat(64), contextHash = "b".repeat(64), textSha256 = "c".repeat(64),
        logicalModelId = LogicalModelId(logical), deploymentId = ModelDeploymentId("deployment-$branch"), provider = ProviderHandle("openrouter"), providerModelId = model,
        catalogVersion = "catalog", priceVersion = "price", currencyCode = "USD", maximumBudgetMicros = 10, confirmationScopeFingerprint = "d".repeat(64), expiresAt = expiresAt,
    )

    private fun request(model: String) = OpenAiCompatibleHttpRequest(
        preset = OpenAiCompatibleProviderPreset.OPENROUTER, method = "POST",
        headers = linkedMapOf("Accept" to "application/json", "Content-Type" to "application/json; charset=utf-8", "Idempotency-Key" to "execution"),
        body = "{\"model\":\"$model\",\"messages\":[]}".toByteArray(),
    )

    private class Credentials : ProviderCredentialStore {
        var loads = 0
        override fun credentialPresence(providerId: ProviderId) = CredentialPresence.PRESENT
        override fun hasCredential(providerId: ProviderId) = true
        override fun saveCredential(providerId: ProviderId, credential: CharArray) = true
        override fun loadCredential(providerId: ProviderId): CharArray? { loads += 1; return "test-only-key".toCharArray() }
    }

    private class Connections : AndroidOpenRouterCompareConnectionFactory {
        var opens = 0
        val connection = Connection()
        override fun open(): AndroidOpenRouterCompareConnection { opens += 1; return connection }
    }

    private class Connection : AndroidOpenRouterCompareConnection {
        val headers = linkedMapOf<String, String>()
        var disconnected = false
        override fun setHeader(name: String, value: String) { headers[name] = value }
        override fun write(body: ByteArray) = Unit
        override fun responseCode() = 200
        override fun contentType() = "application/json"
        override fun readResponse(maxBytes: Int) = "{\"model\":\"provider/gpt\",\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".toByteArray()
        override fun disconnect() { disconnected = true }
    }
}
