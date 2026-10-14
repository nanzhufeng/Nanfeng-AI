package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock

class ProviderConnectionProbeContractsTest {
    @Test fun `Claude connection test uses proven model endpoint with fixed public input and clears secret`() {
        for (preset in listOf(ModelPresetId.RELAYCORE_OPUS_5_5, ModelPresetId.RELAYCORE_SONNET_5_5)) {
            val spec = RelayCoreModels.spec(preset)!!
            var sent: ProviderChatRequest? = null
            var loadedSecret: CharArray? = null
            val credentials = object : ProviderCredentialStore {
                override fun hasCredential(providerId: ProviderId) = providerId == ProviderId.RELAYCORE_CLAUDE
                override fun saveCredential(providerId: ProviderId, credential: CharArray) = false
                override fun loadCredential(providerId: ProviderId) = "synthetic-key".toCharArray().also { loadedSecret=it }
            }
            val settings = object : ModelServiceSettingsRepository {
                override fun load(providerId: ProviderId) = ProviderSettings(providerId,true,preset)
                override fun save(settings: ProviderSettings) = settings
            }
            val diagnostics = object : ProviderDiagnosticStore {
                override fun append(record: ProviderDiagnosticRecord) = error("successful fixture has no failed diagnostic")
                override fun recent(limit: Int) = emptyList<ProviderDiagnosticRecord>()
            }
            val probe = ProviderConnectionProbe(LoadModelServiceConfigurationUseCase(settings,credentials),
                ModelResolver { ResolvedModelResult.Resolved(ResolvedModel(spec.provider,spec.apiId,spec.compactName,ModelCapabilities(true,false,true),null,ModelHealth.AVAILABLE,null,null)) },
                credentials, object : ProviderChatTransport {
                    override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                        sent=request;return ProviderChatOutcome.HttpResponse(200,"{}")
                    }
                }, diagnostics,Clock.systemUTC())
            assertTrue(probe.execute(ProviderId.RELAYCORE_CLAUDE) is ProviderConnectionProbeResult.Connected)
            val request = requireNotNull(sent)
            assertEquals("https://api.relaycore.cn/v1/messages", request.endpoint)
            assertFalse(request.expectsStream)
            assertTrue(request.jsonBody.contains("\"model\":\"${spec.apiId}\""))
            assertTrue(request.jsonBody.contains("\"content\":\"hi\""))
            assertTrue(request.jsonBody.contains("\"max_tokens\":1"))
            assertTrue(requireNotNull(loadedSecret).all { it=='\u0000' })
        }
    }
    @Test fun `OCR connection test uses its fixed image endpoint without changing the chat default`() {
        var sent: ProviderChatRequest? = null
        var loadedSecret: CharArray? = null
        val credentials = object : ProviderCredentialStore {
            override fun hasCredential(providerId: ProviderId) = providerId == ProviderId.ZHIPU
            override fun saveCredential(providerId: ProviderId, credential: CharArray) = error("probe must not save Key")
            override fun loadCredential(providerId: ProviderId) = "synthetic-key".toCharArray().also { loadedSecret = it }
        }
        val settings = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId) = ProviderSettings(providerId, true, ModelPresetId.GLM_5_3_FLASH)
            override fun save(settings: ProviderSettings): ProviderSettings = error("OCR probe must not change chat defaults")
        }
        val diagnostics = object : ProviderDiagnosticStore {
            override fun append(record: ProviderDiagnosticRecord) = error("successful probe has no failure")
            override fun recent(limit: Int) = emptyList<ProviderDiagnosticRecord>()
        }
        val probe = ProviderConnectionProbe(LoadModelServiceConfigurationUseCase(settings, credentials),
            ModelResolver { error("OCR probe does not resolve a chat model") }, credentials,
            object : ProviderChatTransport {
                override fun execute(request: ProviderChatRequest, credential: CharArray): ProviderChatOutcome {
                    sent = request
                    return ProviderChatOutcome.HttpResponse(200, "{}")
                }
            }, diagnostics, Clock.systemUTC())
        val result = probe.execute(ProviderId.ZHIPU, ModelPresetId.GLM_OCR)
        assertTrue(result is ProviderConnectionProbeResult.Connected)
        assertEquals("glm-ocr", (result as ProviderConnectionProbeResult.Connected).apiModelId)
        val request = requireNotNull(sent)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/layout_parsing", request.endpoint)
        assertTrue(request.jsonBody.contains("data:image/png;base64,$OCR_CONNECTION_PROBE_BASE64"))
        assertFalse(request.jsonBody.contains("messages"))
        assertTrue(requireNotNull(loadedSecret).all { it == '\u0000' })
        assertTrue(probe.execute(ProviderId.OPENROUTER, ModelPresetId.GLM_OCR) is ProviderConnectionProbeResult.Blocked)
    }

}
