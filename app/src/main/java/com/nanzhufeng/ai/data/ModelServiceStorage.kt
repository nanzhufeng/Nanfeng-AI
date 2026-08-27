package com.nanzhufeng.ai.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.CredentialPresence
import com.nanzhufeng.ai.domain.ModelServiceSettingsRepository
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderSettings
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidModelServiceSettingsRepository(context: Context) : ModelServiceSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    override fun load(providerId: ProviderId): ProviderSettings = ProviderSettings(
        providerId = providerId,
        // Qwen is the app's preferred lightweight title service. A newly installed app should
        // expose it as ready to configure; an explicit user disable remains authoritative.
        enabled = if (preferences.contains("${providerId.name}.enabled")) {
            preferences.getBoolean("${providerId.name}.enabled", false)
        } else {
            defaultEnabled(providerId)
        },
        presetId = preferences.getString("${providerId.name}.preset", null)
            ?.let(::migratePreset)
            ?: defaultPreset(providerId),
    )

    override fun save(settings: ProviderSettings): ProviderSettings {
        check(preferences.edit()
            .putBoolean("${settings.providerId.name}.enabled", settings.enabled)
            .putString("${settings.providerId.name}.preset", settings.presetId.name)
            .commit()) { "模型设置无法写入本机。" }
        return load(settings.providerId)
    }

    private companion object {
        const val SETTINGS_FILE = "model_service_settings_v1"

        fun defaultEnabled(providerId: ProviderId): Boolean = providerId == ProviderId.QWEN

        fun migratePreset(saved: String): ModelPresetId? = when (saved) {
            "FLAGSHIP", "CLAUDE_OPUS_4_1" -> ModelPresetId.CLAUDE_FABLE_5
            "BALANCED", "CLAUDE_SONNET_4", "CLAUDE_SONNET_3_7" -> ModelPresetId.CLAUDE_SONNET_5
            "FAST", "CLAUDE_HAIKU_3_5" -> ModelPresetId.CLAUDE_HAIKU_4_5
            "GPT_5_1", "GPT_5" -> ModelPresetId.GPT_5_6_SOL
            "GPT_5_MINI" -> ModelPresetId.GPT_5_6_TERRA
            "GPT_5_NANO" -> ModelPresetId.GPT_5_6_LUNA
            else -> runCatching { ModelPresetId.valueOf(saved) }.getOrNull()
        }

        fun defaultPreset(providerId: ProviderId): ModelPresetId = when (providerId) {
            ProviderId.OPENROUTER -> ModelPresetId.GPT_5_6_TERRA
            ProviderId.QWEN -> ModelPresetId.QWEN_3_7_PLUS
            ProviderId.DEEPSEEK -> ModelPresetId.DEEPSEEK_V4_PRO
            ProviderId.MOCK -> ModelPresetId.GPT_5_6_TERRA
        }
    }
}

data class EncryptedCredentialPayload(val iv: ByteArray, val ciphertext: ByteArray)

interface CredentialCipher {
    fun encrypt(providerId: ProviderId, plaintext: ByteArray): EncryptedCredentialPayload
    fun decrypt(providerId: ProviderId, payload: EncryptedCredentialPayload): ByteArray
}

interface CredentialPayloadStorage {
    fun read(providerId: ProviderId): EncryptedCredentialPayload?
    fun write(providerId: ProviderId, payload: EncryptedCredentialPayload): Boolean
}

class EncryptedProviderCredentialStore(
    private val cipher: CredentialCipher,
    private val storage: CredentialPayloadStorage,
) : ProviderCredentialStore {
    override fun credentialPresence(providerId: ProviderId): CredentialPresence =
        if (storage.read(providerId) == null) CredentialPresence.MISSING else CredentialPresence.PRESENT

    override fun hasCredential(providerId: ProviderId): Boolean {
        // Settings and offline readiness must not decrypt a stored Key merely to render a status.
        // The only reader remains the future, explicitly authorized inference adapter.
        return credentialPresence(providerId) == CredentialPresence.PRESENT
    }

    override fun saveCredential(providerId: ProviderId, credential: CharArray): Boolean {
        if (credential.isEmpty()) return false
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(credential))
        val plaintext = ByteArray(encoded.remaining()).also(encoded::get)
        return try {
            storage.write(providerId, cipher.encrypt(providerId, plaintext))
        } catch (_: Exception) {
            false
        } finally {
            plaintext.fill(0)
        }
    }

    override fun loadCredential(providerId: ProviderId): CharArray? {
        val payload = storage.read(providerId) ?: return null
        val plaintext = runCatching { cipher.decrypt(providerId, payload) }.getOrNull() ?: return null
        return try {
            val decoded = Charsets.UTF_8.decode(ByteBuffer.wrap(plaintext))
            CharArray(decoded.remaining()).also(decoded::get).takeIf { it.isNotEmpty() }
        } finally {
            plaintext.fill(0)
        }
    }
}

class SharedPreferencesCredentialPayloadStorage(context: Context) : CredentialPayloadStorage {
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences(CREDENTIAL_FILE, Context.MODE_PRIVATE)

    override fun read(providerId: ProviderId): EncryptedCredentialPayload? {
        val encoded = preferences.getString(providerId.name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes)
            val version = buffer.get().toInt()
            val ivLength = buffer.get().toInt() and 0xFF
            require(version == PAYLOAD_VERSION && ivLength in 12..32 && buffer.remaining() > ivLength)
            val iv = ByteArray(ivLength).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            EncryptedCredentialPayload(iv, ciphertext)
        }.getOrNull()
    }

    override fun write(providerId: ProviderId, payload: EncryptedCredentialPayload): Boolean {
        val bytes = ByteBuffer.allocate(2 + payload.iv.size + payload.ciphertext.size)
            .put(PAYLOAD_VERSION.toByte())
            .put(payload.iv.size.toByte())
            .put(payload.iv)
            .put(payload.ciphertext)
            .array()
        return preferences.edit()
            .putString(providerId.name, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit()
    }

    private companion object {
        const val CREDENTIAL_FILE = "provider_credentials_v1"
        const val PAYLOAD_VERSION = 1
    }
}

class AndroidKeystoreCredentialCipher : CredentialCipher {
    override fun encrypt(providerId: ProviderId, plaintext: ByteArray): EncryptedCredentialPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(providerId))
        cipher.updateAAD(providerId.name.toByteArray(Charsets.UTF_8))
        return EncryptedCredentialPayload(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(providerId: ProviderId, payload: EncryptedCredentialPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyFor(providerId), GCMParameterSpec(128, payload.iv))
        cipher.updateAAD(providerId.name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(payload.ciphertext)
    }

    private fun keyFor(providerId: ProviderId): SecretKey {
        val alias = "com.nanzhufeng.ai.provider.${providerId.name.lowercase()}.v1"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

fun createAndroidProviderCredentialStore(context: Context): ProviderCredentialStore =
    EncryptedProviderCredentialStore(
        cipher = AndroidKeystoreCredentialCipher(),
        storage = SharedPreferencesCredentialPayloadStorage(context),
    )
