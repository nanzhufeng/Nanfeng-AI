package com.nanzhufeng.ai.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nanzhufeng.ai.ai.OpenRouterErrorMapper
import com.nanzhufeng.ai.data.AndroidModelServiceSettingsRepository
import com.nanzhufeng.ai.data.CredentialCipher
import com.nanzhufeng.ai.data.CredentialPayloadStorage
import com.nanzhufeng.ai.data.EncryptedCredentialPayload
import com.nanzhufeng.ai.data.EncryptedProviderCredentialStore
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class P2ModelServiceContractsTest {
    private lateinit var settings: RecordingSettingsRepository
    private lateinit var credentials: RecordingCredentialStore
    private lateinit var load: LoadModelServiceConfigurationUseCase
    private lateinit var save: SaveModelServiceConfigurationUseCase

    @Before
    fun setUp() {
        settings = RecordingSettingsRepository()
        credentials = RecordingCredentialStore()
        load = LoadModelServiceConfigurationUseCase(settings, credentials)
        save = SaveModelServiceConfigurationUseCase(settings, credentials, load)
    }

    @Test
    fun `catalog keeps provider preset and actual model identity separate`() {
        val configuration = load.execute()!!

        assertEquals(ProviderId.OPENROUTER, configuration.provider.id)
        assertEquals("https://openrouter.ai/api/v1", configuration.provider.fixedEndpoint)
        assertEquals(ModelPresetId.CLAUDE_FABLE_5, configuration.preset.id)
        assertEquals("Anthropic", configuration.preset.modelFamilyHint)
        assertEquals(CredentialState.MISSING, configuration.credentialState)
    }

    @Test
    fun `enabling without a credential is rejected before settings change`() {
        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_SONNET_5_5, null)

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialMissing), result)
        assertFalse(settings.load(ProviderId.OPENROUTER).enabled)
    }

    @Test
    fun `credential and preset save through their separate owners`() {
        val result = save.execute(
            ProviderId.OPENROUTER,
            enabled = true,
            presetId = ModelPresetId.CLAUDE_HAIKU_5_5,
            replacementCredential = "test-key-not-real",
        ) as SaveModelServiceConfigurationResult.Saved

        assertEquals(ModelPresetId.CLAUDE_HAIKU_5_5, result.configuration.settings.presetId)
        assertEquals(CredentialState.STORED, result.configuration.credentialState)
        assertTrue(result.configuration.settings.enabled)
        assertEquals("test-key-not-real", credentials.value?.concatToString())
    }

    @Test
    fun `stored credential never overrides an explicit disabled provider`() {
        credentials.value = "test-key-not-real".toCharArray()

        val configuration = load.execute(ProviderId.OPENROUTER)!!

        assertFalse(settings.load(ProviderId.OPENROUTER).enabled)
        assertFalse(configuration.settings.enabled)
        assertEquals(CredentialState.STORED, configuration.credentialState)
    }

    @Test fun `model selection immediately persists without a Key and leaves enabled unchanged`() {
        settings.save(ProviderSettings(ProviderId.OPENROUTER, false, ModelPresetId.CLAUDE_SONNET_5_5))
        val result = save.setPreset(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_HAIKU_5_5) as SaveModelServiceConfigurationResult.Saved
        assertEquals(ModelPresetId.CLAUDE_HAIKU_5_5, load.execute()!!.settings.presetId)
        assertFalse(result.configuration.settings.enabled)
        assertNull(credentials.value)
        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid), save.setPreset(ProviderId.ZHIPU, ModelPresetId.GLM_OCR))
    }
    @Test fun `saving a Key never writes or changes the selected model or platform switch`() {
        save.setPreset(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_HAIKU_5_5)
        val before = settings.writes
        val result = save.saveCredential(ProviderId.OPENROUTER, "synthetic-key-only") as SaveModelServiceConfigurationResult.Saved
        assertEquals(before, settings.writes)
        assertEquals(ModelPresetId.CLAUDE_HAIKU_5_5, result.configuration.settings.presetId)
        assertFalse(result.configuration.settings.enabled)
        assertEquals("synthetic-key-only", credentials.value!!.concatToString())
    }
    @Test fun `settings read failure is rejected without escaping the independent toggle or details save`() {
        val unavailable = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId): ProviderSettings = throw IllegalStateException("synthetic read failure")
            override fun save(settings: ProviderSettings): ProviderSettings = settings
        }
        val useCase = SaveModelServiceConfigurationUseCase(unavailable, credentials, LoadModelServiceConfigurationUseCase(unavailable, credentials))
        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.PersistenceConflict), useCase.setEnabled(ProviderId.OPENROUTER, true))
        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.PersistenceConflict), useCase.saveDetails(ProviderId.OPENROUTER, ModelPresetId.CLAUDE_SONNET_5_5, null))
    }
    @Test fun `metadata read failure after a persisted toggle is reported without crashing`() {
        val unavailable = object : ProviderCredentialStore {
            override fun hasCredential(providerId: ProviderId): Boolean = throw IllegalStateException("synthetic metadata failure")
            override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean = false
            override fun loadCredential(providerId: ProviderId): CharArray? = null
        }
        val useCase = SaveModelServiceConfigurationUseCase(settings, unavailable, LoadModelServiceConfigurationUseCase(settings, unavailable))
        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.PersistenceConflict), useCase.setEnabled(ProviderId.OPENROUTER, true))
        assertTrue(settings.load(ProviderId.OPENROUTER).enabled)
    }
    @Test fun `settings persistence cancellation remains cancellation`() {
        val cancelled = object : ModelServiceSettingsRepository {
            override fun load(providerId: ProviderId): ProviderSettings = throw java.util.concurrent.CancellationException("synthetic cancellation")
            override fun save(settings: ProviderSettings): ProviderSettings = settings
        }
        val useCase = SaveModelServiceConfigurationUseCase(cancelled, credentials, LoadModelServiceConfigurationUseCase(cancelled, credentials))
        org.junit.Assert.assertThrows(java.util.concurrent.CancellationException::class.java) { useCase.setEnabled(ProviderId.OPENROUTER, true) }
    }
    @Test fun `every configured platform persists its independent switch across readback`() {
        ProviderId.entries.filter { NanfengModelServiceCatalog.provider(it) != null }.forEach { provider ->
            settings.save(ProviderSettings(provider, false, NanfengModelServiceCatalog.defaultPreset(provider)))
            save.setEnabled(provider, true)
            assertTrue(load.execute(provider)!!.settings.enabled)
            credentials.value = "synthetic-key-only".toCharArray()
            save.setEnabled(provider, false)
            assertFalse(load.execute(provider)!!.settings.enabled)
        }
    }
    @Test fun `provider switch controls model visibility independently of usable credentials`() {
        val snapshot = NormalChatRoutingSnapshot(emptyMap(), emptySet(), true, enabledProviders = setOf(ProviderId.RELAYCORE_CLAUDE))
        val visible = ComposerModelRoutingCatalog.choices.filter { it != ComposerModelRoutingCatalog.auto && snapshot.isVisible(it) }
        assertEquals(2,visible.size)
        assertTrue(visible.all { choice -> choice.routes.all { NanfengModelServiceCatalog.providerFor(it) == ProviderId.RELAYCORE_CLAUDE } })
        assertTrue(ComposerModelRoutingCatalog.choices.filter { it != ComposerModelRoutingCatalog.auto }.none { snapshot.copy(enabledProviders = emptySet()).isVisible(it) })
    }
    @Test fun `provider toggle needs no credential and details never undo it`() {
        val on = save.setEnabled(ProviderId.OPENROUTER, true) as SaveModelServiceConfigurationResult.Saved
        assertTrue(on.configuration.settings.enabled)
        assertEquals(CredentialState.MISSING, on.configuration.credentialState)
        save.setEnabled(ProviderId.OPENROUTER, false)
        val details = save.saveDetails(ProviderId.OPENROUTER, ModelPresetId.GPT_6_LUNA, "synthetic-key-only") as SaveModelServiceConfigurationResult.Saved
        assertFalse(details.configuration.settings.enabled)
        assertEquals(ModelPresetId.GPT_6_LUNA, details.configuration.settings.presetId)
        assertEquals(CredentialState.STORED, details.configuration.credentialState)
        assertFalse(load.execute(ProviderId.OPENROUTER)!!.settings.enabled)
        assertTrue((save.setEnabled(ProviderId.OPENROUTER, true) as SaveModelServiceConfigurationResult.Saved).configuration.settings.enabled)
    }

    @Test
    fun `saving settings with a blank key keeps the existing secure credential`() {
        save.execute(ProviderId.OPENROUTER, true, ModelPresetId.GPT_6_LUNA, "test-key-not-real")

        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_HAIKU_5_5, null)
            as SaveModelServiceConfigurationResult.Saved

        assertEquals(ModelPresetId.CLAUDE_HAIKU_5_5, result.configuration.settings.presetId)
        assertEquals(CredentialState.STORED, result.configuration.credentialState)
        assertEquals("test-key-not-real", credentials.value?.concatToString())
    }

    @Test
    fun `invalid credential never reaches secure storage`() {
        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.GPT_6_LUNA, "short")

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialInvalid), result)
        assertNull(credentials.value)
    }

    @Test
    fun `retired chat presets cannot be newly saved or erase a credential`() {
        save.execute(ProviderId.OPENROUTER, true, ModelPresetId.GPT_6_LUNA, "test-key-not-real")
        for (preset in listOf(ModelPresetId.CLAUDE_FABLE_5, ModelPresetId.KIMI_K3)) {
            assertEquals(
                SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid),
                save.execute(ProviderId.OPENROUTER, true, preset, null),
            )
            assertEquals(ModelPresetId.GPT_6_LUNA, settings.load(ProviderId.OPENROUTER).presetId)
            assertEquals("test-key-not-real", credentials.value?.concatToString())
        }
    }

    @Test
    fun `document utility model cannot become a chat default`() {
        val result = save.execute(ProviderId.ZHIPU, false, ModelPresetId.GLM_OCR, null)

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid), result)
        assertEquals(ModelPresetId.CLAUDE_FABLE_5, settings.load(ProviderId.ZHIPU).presetId)
    }

    @Test
    fun `preset cannot be saved under the wrong provider`() {
        val result = save.execute(ProviderId.DEEPSEEK, false, ModelPresetId.GLM_5_3_FLASH, null)

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid), result)
    }

    @Test
    fun `Zhipu flagship can be selected in settings without changing the Flash default`() {
        val result = save.execute(ProviderId.ZHIPU, false, ModelPresetId.GLM_5_3, null)

        assertTrue(result is SaveModelServiceConfigurationResult.Saved)
        assertEquals(ModelPresetId.GLM_5_3, settings.load(ProviderId.ZHIPU).presetId)
        assertEquals(ModelPresetId.GLM_5_3_FLASH, NanfengModelServiceCatalog.defaultPreset(ProviderId.ZHIPU))
    }

    @Test
    fun `encrypted credential store round trips without plaintext persistence`() {
        val storage = RecordingPayloadStorage()
        val store = EncryptedProviderCredentialStore(XorCredentialCipher(), storage)
        val credential = "test-key-not-real".toCharArray()

        assertTrue(store.saveCredential(ProviderId.OPENROUTER, credential))
        val restored = store.loadCredential(ProviderId.OPENROUTER)!!

        assertEquals("test-key-not-real", restored.concatToString())
        assertFalse(storage.payload!!.ciphertext.toString(Charsets.UTF_8).contains("test-key-not-real"))
        restored.fill('\u0000')
    }

    @Test
    fun `credential presence check never decrypts stored key bytes`() {
        val storage = RecordingPayloadStorage().apply {
            payload = EncryptedCredentialPayload(ByteArray(12) { 1 }, byteArrayOf(2, 3, 4))
        }
        val cipher = DecryptCountingCipher()
        val store = EncryptedProviderCredentialStore(cipher, storage)

        assertTrue(store.hasCredential(ProviderId.OPENROUTER))
        assertEquals(CredentialPresence.PRESENT, store.credentialPresence(ProviderId.OPENROUTER))
        assertEquals(0, cipher.decryptCalls)
    }

    @Test
    fun `settings can reveal only the transient saved credential when requested`() {
        credentials.value = "test-key-not-real".toCharArray()

        val revealed = load.revealStoredCredential()!!

        assertEquals("test-key-not-real", revealed.concatToString())
        revealed.fill('\u0000')
    }

    @Test
    fun `settings repository falls back from unknown old preset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("model_service_settings_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("OPENROUTER.preset", "RETIRED_MODEL")
            .commit()

        val restored = AndroidModelServiceSettingsRepository(context).load(ProviderId.OPENROUTER)

        assertEquals(ModelPresetId.GPT_6_LUNA, restored.presetId)
    }

    @Test fun `October upgrade migrates saved selections without touching credentials`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((old, current) in listOf(
            "CLAUDE_OPUS_5" to ModelPresetId.CLAUDE_OPUS_5_5,
            "CLAUDE_SONNET_5" to ModelPresetId.CLAUDE_SONNET_5_5,
            "CLAUDE_HAIKU_4_5" to ModelPresetId.CLAUDE_HAIKU_5_5,
            "GPT_5_6_SOL" to ModelPresetId.GPT_6_1_SOL,
            "GPT_5_6_TERRA" to ModelPresetId.GPT_6_LUNA,
            "GPT_5_6_LUNA" to ModelPresetId.GPT_6_LUNA,
        )) {
            context.getSharedPreferences("model_service_settings_v1", Context.MODE_PRIVATE).edit().putString("OPENROUTER.preset", old).commit()
            assertEquals(current, AndroidModelServiceSettingsRepository(context).load(ProviderId.OPENROUTER).presetId)
        }
        assertEquals(ModelPresetId.GPT_6_LUNA, ComposerModelRoutingCatalog.choice("logical:daily:gpt-terra").routes.single())
    }

    @Test
    fun `settings repository upgrades persisted Gemini 3_7 selection to Gemini 3_8`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("model_service_settings_v1", Context.MODE_PRIVATE)
            .edit()
            .putString("OPENROUTER.preset", "GEMINI_3_7_FLASH")
            .commit()

        val restored = AndroidModelServiceSettingsRepository(context).load(ProviderId.OPENROUTER)

        assertEquals(ModelPresetId.GEMINI_3_8_FLASH, restored.presetId)
    }

    @Test
    fun `catalog contains the approved logical provider models`() {
        assertEquals(
            listOf("Gemini 3.8 Flash", "Qwen3.7-Plus", "Qwen3.8-Max", "Qwen3.6 Flash", "DeepSeek V4 Pro", "DeepSeek V4.1 Flash", "GLM-5.3", "GLM-5.3 Flash"),
            NanfengModelServiceCatalog.presets.filter { it.id in setOf(ModelPresetId.GEMINI_3_8_FLASH, ModelPresetId.QWEN_3_7_PLUS, ModelPresetId.QWEN_3_8_MAX, ModelPresetId.QWEN_3_6_FLASH, ModelPresetId.DEEPSEEK_V4_PRO, ModelPresetId.DEEPSEEK_V4_FLASH, ModelPresetId.GLM_5_3, ModelPresetId.GLM_5_3_FLASH) }.map { it.displayName },
        )
    }

    @Test fun `provider catalog keeps OpenRouter and official direct endpoints separate`() {
        assertEquals(ProviderId.OPENROUTER, NanfengModelServiceCatalog.providerFor(ModelPresetId.GEMINI_3_8_FLASH))
        assertEquals(ProviderId.QWEN, NanfengModelServiceCatalog.providerFor(ModelPresetId.QWEN_3_7_PLUS))
        assertEquals(ProviderId.DEEPSEEK, NanfengModelServiceCatalog.providerFor(ModelPresetId.DEEPSEEK_V4_PRO))
        assertEquals(ProviderId.DEEPSEEK, NanfengModelServiceCatalog.providerFor(ModelPresetId.DEEPSEEK_V4_FLASH))
        assertEquals(ProviderId.ZHIPU, NanfengModelServiceCatalog.providerFor(ModelPresetId.GLM_5_3))
        assertEquals(ProviderId.ZHIPU, NanfengModelServiceCatalog.providerFor(ModelPresetId.GLM_5_3_FLASH))
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", NanfengModelServiceCatalog.provider(ProviderId.QWEN)?.fixedEndpoint)
        assertEquals("https://open.bigmodel.cn/api/paas/v4", NanfengModelServiceCatalog.provider(ProviderId.ZHIPU)?.fixedEndpoint)
        assertEquals(ModelPresetId.GLM_5_3_FLASH, NanfengModelServiceCatalog.defaultPreset(ProviderId.ZHIPU))
    }

    @Test fun `Zhipu settings expose both chat presets but keep OCR out of the chat selector`() {
        assertEquals(
            listOf("GLM-5.3", "GLM-5.3 Flash"),
            NanfengModelServiceCatalog.chatPresets
                .filter { NanfengModelServiceCatalog.providerFor(it.id) == ProviderId.ZHIPU }
                .map { it.displayName },
        )
    }

    @Test
    fun `openrouter errors map to stable domain categories`() {
        assertEquals(AiTaskError.ProviderAuthenticationFailed, OpenRouterErrorMapper.fromHttpStatus(401))
        assertEquals(AiTaskError.ProviderBalanceInsufficient, OpenRouterErrorMapper.fromHttpStatus(402))
        assertEquals(AiTaskError.ProviderRateLimited, OpenRouterErrorMapper.fromHttpStatus(429))
        assertEquals(AiTaskError.ProviderUnavailable, OpenRouterErrorMapper.fromHttpStatus(503))
        assertEquals(AiTaskError.ProviderTimedOut, OpenRouterErrorMapper.fromThrowable(SocketTimeoutException()))
        assertEquals(AiTaskError.ProviderNetworkUnavailable, OpenRouterErrorMapper.fromThrowable(UnknownHostException()))
    }
}

private class RecordingSettingsRepository : ModelServiceSettingsRepository {
    var writes = 0
    private val values = mutableMapOf<ProviderId, ProviderSettings>()

    override fun load(providerId: ProviderId): ProviderSettings =
        values[providerId] ?: ProviderSettings(providerId, enabled = false, presetId = ModelPresetId.CLAUDE_FABLE_5)

    override fun save(settings: ProviderSettings): ProviderSettings = settings.also { writes++; values[it.providerId] = it }
}

private class RecordingCredentialStore : ProviderCredentialStore {
    var value: CharArray? = null

    override fun hasCredential(providerId: ProviderId): Boolean = value?.isNotEmpty() == true

    override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean {
        value = credential.copyOf()
        return true
    }

    override fun loadCredential(providerId: ProviderId): CharArray? = value?.copyOf()
}

private class RecordingPayloadStorage : CredentialPayloadStorage {
    var payload: EncryptedCredentialPayload? = null

    override fun read(providerId: ProviderId): EncryptedCredentialPayload? = payload
    override fun write(providerId: ProviderId, payload: EncryptedCredentialPayload): Boolean {
        this.payload = payload
        return true
    }
}

private class XorCredentialCipher : CredentialCipher {
    private val mask = 0x5A.toByte()

    override fun encrypt(providerId: ProviderId, plaintext: ByteArray): EncryptedCredentialPayload =
        EncryptedCredentialPayload(ByteArray(12) { 1 }, plaintext.map { (it.toInt() xor mask.toInt()).toByte() }.toByteArray())

    override fun decrypt(providerId: ProviderId, payload: EncryptedCredentialPayload): ByteArray =
        payload.ciphertext.map { (it.toInt() xor mask.toInt()).toByte() }.toByteArray()
}

private class DecryptCountingCipher : CredentialCipher {
    var decryptCalls = 0
    override fun encrypt(providerId: ProviderId, plaintext: ByteArray): EncryptedCredentialPayload =
        EncryptedCredentialPayload(ByteArray(12) { 1 }, plaintext)
    override fun decrypt(providerId: ProviderId, payload: EncryptedCredentialPayload): ByteArray {
        decryptCalls += 1
        return payload.ciphertext
    }
}
