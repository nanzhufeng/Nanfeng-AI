package com.nanzhufeng.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nanzhufeng.ai.BuildConfig
import com.nanzhufeng.ai.domain.P7CAuthenticatedRpcTransport
import com.nanzhufeng.ai.domain.P7CPrivateServiceConfig
import com.nanzhufeng.ai.domain.P7CServiceAvailability
import com.nanzhufeng.ai.domain.P7CServiceConfiguration
import com.nanzhufeng.ai.domain.NfaiSyncAccountWrappingMaterial
import com.nanzhufeng.ai.domain.NfaiSyncV1Gateway
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class P7FCloudSession(
    val userId: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)

sealed interface P7FGoogleSignInResult {
    data class Success(val idToken: String, val rawNonce: String) : P7FGoogleSignInResult
    data object Cancelled : P7FGoogleSignInResult
    data class Failure(val message: String) : P7FGoogleSignInResult
}

class P7FGoogleSignInClient(context: Context, private val serverClientId: String) {
    private val credentials = CredentialManager.create(context.applicationContext)
    val configured: Boolean get() = serverClientId.trim().endsWith(".apps.googleusercontent.com")

    suspend fun signIn(activityContext: Context): P7FGoogleSignInResult {
        if (!configured) return P7FGoogleSignInResult.Failure("尚未配置 Google 登录。")
        val rawNonce = secureNonce()
        return try {
            val option = GetSignInWithGoogleOption.Builder(serverClientId.trim())
                .setNonce(rawNonce.sha256Hex())
                .build()
            val response = credentials.getCredential(
                context = activityContext,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                P7FGoogleSignInResult.Failure("Google 没有返回可验证的账号凭据。")
            } else {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                P7FGoogleSignInResult.Success(google.idToken, rawNonce)
            }
        } catch (_: GetCredentialCancellationException) {
            P7FGoogleSignInResult.Cancelled
        } catch (_: NoCredentialException) {
            P7FGoogleSignInResult.Failure("设备上没有可用的 Google 账号。")
        } catch (_: GetCredentialException) {
            P7FGoogleSignInResult.Failure("无法打开 Google 登录，请稍后重试。")
        } catch (_: Throwable) {
            P7FGoogleSignInResult.Failure("Google 登录失败，请重试。")
        }
    }

    suspend fun clearCredentialState() {
        runCatching { credentials.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun secureNonce(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return try { Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }
        finally { bytes.fill(0) }
    }

    private fun String.sha256Hex(): String {
        val source = encodeToByteArray()
        return try { MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) } }
        finally { source.fill(0) }
    }
}

class P7FEncryptedSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): P7FCloudSession? = preferences.getString(STATE, null)?.let { encrypted ->
        runCatching { JSONObject(decrypt(encrypted)).toSession() }.getOrNull()
    }

    fun write(session: P7FCloudSession) {
        val payload = JSONObject()
            .put("userId", session.userId)
            .put("email", session.email)
            .put("displayName", session.displayName ?: JSONObject.NULL)
            .put("avatarUrl", session.avatarUrl ?: JSONObject.NULL)
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAtEpochSeconds)
            .toString()
        check(preferences.edit().putString(STATE, encrypt(payload)).commit()) { "SESSION_WRITE_FAILED" }
    }

    fun clear() { check(preferences.edit().remove(STATE).commit()) { "SESSION_CLEAR_FAILED" } }

    fun hasWrappingMaterial(userId: String): Boolean = preferences.contains(wrappingStateKey(userId))

    fun saveWrappingMaterial(userId: String, material: NfaiSyncAccountWrappingMaterial) {
        saveMaterial(wrappingStateKey(userId), userId, material)
    }

    fun savePendingWrappingMaterial(userId: String, material: NfaiSyncAccountWrappingMaterial) {
        saveMaterial(pendingWrappingStateKey(userId), userId, material)
    }

    fun promotePendingWrappingMaterial(userId: String) {
        val pending = preferences.getString(pendingWrappingStateKey(userId), null) ?: error("RECOVERY_ROTATION_NOT_READY")
        check(
            preferences.edit()
                .putString(wrappingStateKey(userId), pending)
                .remove(pendingWrappingStateKey(userId))
                .commit(),
        ) { "RECOVERY_ROTATION_PROMOTE_FAILED" }
    }

    fun <T> withWrappingMaterial(userId: String, block: (NfaiSyncAccountWrappingMaterial) -> T): T =
        withMaterial(wrappingStateKey(userId), userId, block)

    fun <T> withPendingWrappingMaterial(userId: String, block: (NfaiSyncAccountWrappingMaterial) -> T): T =
        withMaterial(pendingWrappingStateKey(userId), userId, block)

    private fun saveMaterial(key: String, userId: String, material: NfaiSyncAccountWrappingMaterial) {
        val payload = JSONObject()
            .put("userId", userId)
            .put("wrappingKey", Base64.encodeToString(material.wrappingKey, Base64.NO_WRAP))
            .put("salt", Base64.encodeToString(material.salt, Base64.NO_WRAP))
            .toString()
        check(preferences.edit().putString(key, encrypt(payload)).commit()) { "WRAPPING_MATERIAL_WRITE_FAILED" }
    }

    private fun <T> withMaterial(key: String, userId: String, block: (NfaiSyncAccountWrappingMaterial) -> T): T {
        val encrypted = preferences.getString(key, null) ?: error("RECOVERY_SETUP_REQUIRED")
        val root = JSONObject(decrypt(encrypted))
        require(root.getString("userId") == userId)
        val keyBytes = Base64.decode(root.getString("wrappingKey"), Base64.NO_WRAP)
        val salt = Base64.decode(root.getString("salt"), Base64.NO_WRAP)
        return try { block(NfaiSyncAccountWrappingMaterial(keyBytes, salt)) }
        finally { keyBytes.fill(0); salt.fill(0) }
    }

    private fun JSONObject.toSession() = P7FCloudSession(
        userId = getString("userId"),
        email = optString("email"),
        displayName = optionalString("displayName"),
        avatarUrl = optionalString("avatarUrl"),
        accessToken = getString("accessToken"),
        refreshToken = getString("refreshToken"),
        expiresAtEpochSeconds = getLong("expiresAt"),
    )

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val plain = value.toByteArray(StandardCharsets.UTF_8)
        return try {
            val encrypted = cipher.doFinal(plain)
            try { Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP) } finally { encrypted.fill(0) }
        } finally { plain.fill(0) }
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 28)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
            val plain = cipher.doFinal(encrypted)
            try { String(plain, StandardCharsets.UTF_8) } finally { plain.fill(0) }
        } finally { bytes.fill(0); iv.fill(0); encrypted.fill(0) }
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private fun wrappingStateKey(userId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray())
        return "encrypted_wrap_${digest.joinToString("") { "%02x".format(it) }.take(32)}"
    }

    private fun pendingWrappingStateKey(userId: String): String = "${wrappingStateKey(userId)}_pending"

    private companion object {
        const val PREFERENCES = "nanfeng_ai_google_account"
        const val STATE = "encrypted_session_v1"
        const val KEY_ALIAS = "com.nanzhufeng.ai.google.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class P7FSupabaseAccountClient(private val config: P7CPrivateServiceConfig) {
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): P7FCloudSession = withContext(Dispatchers.IO) {
        request(
            path = "/auth/v1/token?grant_type=id_token",
            body = JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce),
            bearer = null,
        ).requireSession()
    }

    fun refresh(refreshToken: String): P7FCloudSession = request(
        path = "/auth/v1/token?grant_type=refresh_token",
        body = JSONObject().put("refresh_token", refreshToken),
        bearer = null,
    ).requireSession()

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { request("/auth/v1/logout", JSONObject(), accessToken, allowEmpty = true) }
    }

    /** The proxy sees only an allow-listed Google image URL and the current Supabase JWT. */
    suspend fun fetchGoogleAvatar(accessToken: String, avatarUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!P7DAvatarCache.isAllowedGoogleAvatarUrl(avatarUrl)) return@withContext null
        val connection = runCatching {
            URI(config.supabaseUrl.trim().removeSuffix("/") + "/functions/v1/google-avatar")
                .toURL().openConnection() as HttpURLConnection
        }.getOrNull() ?: return@withContext null
        val request = JSONObject().put("url", avatarUrl.trim()).toString().toByteArray(StandardCharsets.UTF_8)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = AVATAR_TIMEOUT_MILLIS
            connection.readTimeout = AVATAR_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("apikey", config.publishableKey)
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "image/*")
            connection.setFixedLengthStreamingMode(request.size)
            connection.outputStream.use { it.write(request) }
            if (connection.responseCode !in 200..299 ||
                connection.contentLengthLong == 0L || connection.contentLengthLong > P7DAvatarCache.MAX_BYTES ||
                !connection.contentType.orEmpty().startsWith("image/", ignoreCase = true)
            ) return@withContext null
            connection.inputStream.use(::readBoundedGoogleAvatar)
        } catch (_: Exception) {
            null
        } finally {
            request.fill(0)
            connection.disconnect()
        }
    }

    fun rpc(function: String, body: String, accessToken: String): String =
        request("/rest/v1/rpc/$function", JSONObject(body), accessToken).toString()

    private fun request(path: String, body: JSONObject, bearer: String?, allowEmpty: Boolean = false): Any {
        val connection = URI(config.supabaseUrl.trim().removeSuffix("/") + path).toURL().openConnection() as HttpURLConnection
        val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", config.publishableKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.readUtf8Bounded(2 * 1024 * 1024).orEmpty()
            if (code !in 200..299) error("REMOTE_${code}")
            if (text.isBlank() && allowEmpty) return JSONObject()
            return if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        } finally {
            bytes.fill(0)
            connection.disconnect()
        }
    }

    private fun InputStream.readUtf8Bounded(maxChars: Int): String =
        bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val output = StringBuilder(minOf(maxChars, 8 * 1024))
            val buffer = CharArray(8 * 1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(output.length + count <= maxChars) { "REMOTE_RESPONSE_TOO_LARGE" }
                output.append(buffer, 0, count)
            }
            output.toString()
        }

    private fun Any.requireSession(): P7FCloudSession {
        val root = this as? JSONObject ?: error("SESSION_REJECTED")
        val user = root.optJSONObject("user") ?: error("SESSION_REJECTED")
        val metadata = user.optJSONObject("user_metadata")
        val expiresIn = root.optLong("expires_in", 3600L).coerceIn(60L, 86_400L)
        return P7FCloudSession(
            userId = user.getString("id"),
            email = user.optString("email"),
            displayName = metadata?.optString("full_name")?.takeIf(String::isNotBlank)
                ?: metadata?.optString("name")?.takeIf(String::isNotBlank),
            avatarUrl = metadata?.optString("avatar_url")?.takeIf(String::isNotBlank)
                ?: metadata?.optString("picture")?.takeIf(String::isNotBlank),
            accessToken = root.getString("access_token"),
            refreshToken = root.getString("refresh_token"),
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
        )
    }
}

class P7FGoogleAccountOwner(context: Context) {
    private val availability = P7CServiceConfiguration.resolve(
        BuildConfig.NANFENG_SUPABASE_URL,
        BuildConfig.NANFENG_SUPABASE_PUBLISHABLE_KEY,
        BuildConfig.NANFENG_GOOGLE_WEB_CLIENT_ID,
    )
    private val config = (availability as? P7CServiceAvailability.Configured)?.config
    private val google = P7FGoogleSignInClient(context, BuildConfig.NANFENG_GOOGLE_WEB_CLIENT_ID)
    private val store = P7FEncryptedSessionStore(context)
    private val avatarCache = P7DAvatarCache(context)
    private val client = config?.let(::P7FSupabaseAccountClient)

    val configured: Boolean get() = config != null && google.configured
    fun cachedSession(): P7FCloudSession? = store.read()
    fun recoveryReady(userId: String): Boolean = store.hasWrappingMaterial(userId)

    fun prepareRecoveryMaterial(userId: String, recoveryCode: CharArray) {
        require(cachedSession()?.userId == userId) { "SIGNED_OUT" }
        require(!recoveryReady(userId)) { "RECOVERY_ALREADY_READY" }
        val material = NfaiSyncV1Gateway.createAccountWrappingMaterial(recoveryCode)
        try { store.saveWrappingMaterial(userId, material) }
        finally { material.wrappingKey.fill(0); material.salt.fill(0) }
    }

    fun preparePendingRecoveryMaterial(userId: String, recoveryCode: CharArray) {
        require(cachedSession()?.userId == userId) { "SIGNED_OUT" }
        require(recoveryReady(userId)) { "RECOVERY_SETUP_REQUIRED" }
        val material = NfaiSyncV1Gateway.createAccountWrappingMaterial(recoveryCode)
        try { store.savePendingWrappingMaterial(userId, material) }
        finally { material.wrappingKey.fill(0); material.salt.fill(0) }
    }

    fun promotePendingRecoveryMaterial(userId: String) = store.promotePendingWrappingMaterial(userId)

    fun <T> withWrappingMaterial(userId: String, block: (NfaiSyncAccountWrappingMaterial) -> T): T =
        store.withWrappingMaterial(userId, block)

    fun <T> withPendingWrappingMaterial(userId: String, block: (NfaiSyncAccountWrappingMaterial) -> T): T =
        store.withPendingWrappingMaterial(userId, block)

    suspend fun signIn(activityContext: Context): Result<P7FCloudSession> {
        if (!configured) return Result.failure(IllegalStateException("尚未配置 Google 登录与云端服务。"))
        return when (val result = google.signIn(activityContext)) {
            P7FGoogleSignInResult.Cancelled -> Result.failure(
                java.util.concurrent.CancellationException("Google 未完成授权，请重试。"),
            )
            is P7FGoogleSignInResult.Failure -> Result.failure(IllegalStateException(result.message))
            is P7FGoogleSignInResult.Success -> runCatching {
                client!!.signInWithGoogle(result.idToken, result.rawNonce).also(store::write)
            }
        }
    }

    suspend fun signOut() {
        store.read()?.let { session ->
            client?.signOut(session.accessToken)
            avatarCache.deleteAccount(session.userId)
        }
        store.clear()
        google.clearCredentialState()
    }

    /** UI loads private cache first. A verified proxy is preferred; direct Google delivery is the
     * constrained fallback for deployments where the optional proxy is unavailable. */
    suspend fun loadGoogleAvatar(avatarUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!P7DAvatarCache.isAllowedGoogleAvatarUrl(avatarUrl)) return@withContext null
        val session = store.read()
        if (session != null) client?.fetchGoogleAvatar(session.accessToken, avatarUrl)?.let { return@withContext it }
        fetchGoogleAvatarDirect(avatarUrl)
    }

    suspend fun switchAccount(activityContext: Context): Result<P7FCloudSession> {
        signOut()
        return signIn(activityContext)
    }

    fun authenticatedTransport(): P7CAuthenticatedRpcTransport = object : P7CAuthenticatedRpcTransport {
        override fun call(function: String, body: String): String {
            val current = store.read() ?: error("SIGNED_OUT")
            val session = if (current.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
                client!!.refresh(current.refreshToken).also(store::write)
            } else current
            return client!!.rpc(function, body, session.accessToken).let(::unwrapRpcResult)
        }
    }

    /** List responses are arrays: do not use the single-document RPC unwrapping path. */
    fun listCloudDocuments(): List<String> {
        val current = store.read() ?: error("请先登录 Google 账号。")
        val session = if (current.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 60L) {
            client!!.refresh(current.refreshToken).also(store::write)
        } else current
        val response = client!!.rpc("nanfeng_sync_list_documents", JSONObject().put("p_app_id", "com.nanzhufeng.ai").toString(), session.accessToken)
        val rows = JSONArray(response)
        require(rows.length() <= 10000) { "云端列表过大，请缩小恢复范围。" }
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val envelope = when (val raw = row.get("envelope")) {
                is JSONObject -> raw.toString()
                is String -> raw
                else -> error("REMOTE_DOCUMENT_INVALID")
            }
            val checked = NfaiSyncV1Gateway.preflight(envelope) as? com.nanzhufeng.ai.domain.NfaiSyncResult.Preflighted
                ?: error("云端文档校验失败。")
            require(checked.value.appId == "com.nanzhufeng.ai") { "云端文档不属于本应用。" }
            envelope
        }
    }

    private fun unwrapRpcResult(raw: String): String {
        val value = raw.trim()
        if (!value.startsWith("[")) return value
        val array = JSONArray(value)
        return if (array.length() == 0) JSONObject().put("missing", true).toString() else array.getJSONObject(0).toString()
    }
}

private fun fetchGoogleAvatarDirect(avatarUrl: String): ByteArray? {
    if (!P7DAvatarCache.isAllowedGoogleAvatarUrl(avatarUrl)) return null
    val connection = runCatching { URL(avatarUrl).openConnection() as HttpURLConnection }.getOrNull() ?: return null
    return try {
        connection.connectTimeout = AVATAR_TIMEOUT_MILLIS
        connection.readTimeout = AVATAR_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "image/*")
        connection.connect()
        if (connection.responseCode !in 200..299 ||
            connection.contentLengthLong == 0L || connection.contentLengthLong > P7DAvatarCache.MAX_BYTES ||
            !connection.contentType.orEmpty().startsWith("image/", ignoreCase = true)
        ) null else connection.inputStream.use(::readBoundedGoogleAvatar)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun readBoundedGoogleAvatar(input: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > P7DAvatarCache.MAX_BYTES) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray().takeIf { it.isNotEmpty() }
}

private const val AVATAR_TIMEOUT_MILLIS = 8_000
