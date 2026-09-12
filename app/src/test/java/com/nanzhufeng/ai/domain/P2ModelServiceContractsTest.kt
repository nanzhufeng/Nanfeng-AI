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
        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_SONNET_5, null)

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialMissing), result)
        assertFalse(settings.load(ProviderId.OPENROUTER).enabled)
    }

    @Test
    fun `credential and preset save through their separate owners`() {
        val result = save.execute(
            ProviderId.OPENROUTER,
            enabled = true,
            presetId = ModelPresetId.CLAUDE_HAIKU_4_5,
            replacementCredential = "test-key-not-real",
        ) as SaveModelServiceConfigurationResult.Saved

        assertEquals(ModelPresetId.CLAUDE_HAIKU_4_5, result.configuration.settings.presetId)
        assertEquals(CredentialState.STORED, result.configuration.credentialState)
        assertTrue(result.configuration.settings.enabled)
        assertEquals("test-key-not-real", credentials.value?.concatToString())
    }

    @Test
    fun `stored local credential repairs a stale disabled flag for settings and composer alike`() {
        credentials.value = "test-key-not-real".toCharArray()

        val configuration = load.execute(ProviderId.OPENROUTER)!!

        assertFalse(settings.load(ProviderId.OPENROUTER).enabled)
        assertTrue(configuration.settings.enabled)
        assertEquals(CredentialState.STORED, configuration.credentialState)
    }

    @Test
    fun `saving settings with a blank key keeps the existing secure credential`() {
        save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_FABLE_5, "test-key-not-real")

        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_HAIKU_4_5, null)
            as SaveModelServiceConfigurationResult.Saved

        assertEquals(ModelPresetId.CLAUDE_HAIKU_4_5, result.configuration.settings.presetId)
        assertEquals(CredentialState.STORED, result.configuration.credentialState)
        assertEquals("test-key-not-real", credentials.value?.concatToString())
    }

    @Test
    fun `invalid credential never reaches secure storage`() {
        val result = save.execute(ProviderId.OPENROUTER, true, ModelPresetId.CLAUDE_FABLE_5, "short")

        assertEquals(SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialInvalid), result)
        assertNull(credentials.value)
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

        assertEquals(ModelPresetId.GPT_5_6_TERRA, restored.presetId)
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
    private val values = mutableMapOf<ProviderId, ProviderSettings>()

    override fun load(providerId: ProviderId): ProviderSettings =
        values[providerId] ?: ProviderSettings(providerId, enabled = false, presetId = ModelPresetId.CLAUDE_FABLE_5)

    override fun save(settings: ProviderSettings): ProviderSettings = settings.also { values[it.providerId] = it }
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
