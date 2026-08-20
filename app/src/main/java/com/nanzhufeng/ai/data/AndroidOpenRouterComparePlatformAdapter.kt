package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.ai.OpenAiCompatibleHttpClient
import com.nanzhufeng.ai.ai.OpenAiCompatibleHttpOutcome
import com.nanzhufeng.ai.ai.OpenAiCompatibleHttpRequest
import com.nanzhufeng.ai.ai.OpenAiCompatibleProviderPreset
import com.nanzhufeng.ai.ai.ProtectedProviderCredentialHandle
import com.nanzhufeng.ai.domain.CompareExecutionGrantedPlan
import com.nanzhufeng.ai.domain.CompareMvpLogicalModels
import com.nanzhufeng.ai.domain.ProviderCredentialStore
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderTransportCancellationToken
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Clock
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

/**
 * Android-only, unregistered Compare platform boundary. It is intentionally created only from
 * grants that the Compare application owner emitted after the one-time summary acknowledgement.
 * Construction reads neither a Key nor the network; this adapter may be injected only by a future
 * reviewed composition that is reached from the existing visible confirmation surface.
 */
internal class AndroidOpenRouterComparePlatformAdapter private constructor(
    private val credentialStore: ProviderCredentialStore,
    private val authorization: ConfirmedCompareAuthorization,
    private val clock: Clock,
    private val connections: AndroidOpenRouterCompareConnectionFactory,
) : ProtectedProviderCredentialHandle, OpenAiCompatibleHttpClient {
    override val providerHandle: String = "openrouter"
    override val handleReference: String = "compare-confirmed:${authorization.confirmationId.take(24)}"

    /**
     * This is deliberately synchronized: each exact provider-facing model is consumed once for
     * this one confirmed two-branch batch. A retry needs fresh visible confirmation and grants.
     */
    @Synchronized
    override fun execute(
        request: OpenAiCompatibleHttpRequest,
        credential: ProtectedProviderCredentialHandle,
        cancellation: ProviderTransportCancellationToken,
    ): OpenAiCompatibleHttpOutcome {
        if (credential !== this || cancellation.isCancellationRequested() || !authorization.isLive(clock.instant())) {
            return OpenAiCompatibleHttpOutcome.DisabledNoNetwork
        }
        val model = modelFrom(request) ?: return OpenAiCompatibleHttpOutcome.DisabledNoNetwork

        val key = credentialStore.loadCredential(ProviderId.OPENROUTER)
            ?: return OpenAiCompatibleHttpOutcome.CredentialUnavailable
        return try {
            if (cancellation.isCancellationRequested()) return OpenAiCompatibleHttpOutcome.Cancelled
            if (!authorization.consumeModel(model)) return OpenAiCompatibleHttpOutcome.DisabledNoNetwork
            connections.open().use(request, key, cancellation)
        } finally {
            key.fill('\u0000')
        }
    }

    private fun modelFrom(request: OpenAiCompatibleHttpRequest): String? {
        if (request.preset != OpenAiCompatibleProviderPreset.OPENROUTER || request.method != "POST") return null
        val prefix = "{\"model\":\""
        val text = request.body.toString(Charsets.UTF_8)
        if (!text.startsWith(prefix)) return null
        val end = text.indexOf('"', prefix.length)
        if (end <= prefix.length) return null
        return text.substring(prefix.length, end).takeIf { it in authorization.remainingModels }
    }

    companion object {
        /** Returns null rather than widening authorization when grants are incomplete, wrong, or expired. */
        fun createForConfirmedCompare(
            granted: CompareExecutionGrantedPlan,
            credentialStore: ProviderCredentialStore,
            clock: Clock,
            connections: AndroidOpenRouterCompareConnectionFactory = AndroidOpenRouterCompareHttpsConnectionFactory,
        ): AndroidOpenRouterComparePlatformAdapter? {
            val now = clock.instant()
            val expectedLogicalModels = CompareMvpLogicalModels.defaultPair.map { it.id }.toSet()
            val grants = granted.branchGrants
            if (!now.isBefore(granted.expiresAt) || grants.size != 2 ||
                grants.map { it.logicalModelId }.toSet() != expectedLogicalModels ||
                grants.map { it.providerModelId }.toSet().size != 2 ||
                grants.any { it.provider.value != "openrouter" || it.providerModelId.isBlank() ||
                    it.confirmationScopeFingerprint.isBlank() || it.confirmationScopeFingerprint != grants.first().confirmationScopeFingerprint }
            ) return null
            return AndroidOpenRouterComparePlatformAdapter(
                credentialStore = credentialStore,
                authorization = ConfirmedCompareAuthorization(granted.confirmationId, granted.expiresAt, grants.map { it.providerModelId }.toMutableSet()),
                clock = clock,
                connections = connections,
            )
        }
    }
}

/** No request body, credential or response is retained by this small platform test seam. */
internal fun interface AndroidOpenRouterCompareConnectionFactory {
    fun open(): AndroidOpenRouterCompareConnection
}

internal interface AndroidOpenRouterCompareConnection {
    fun setHeader(name: String, value: String)
    fun write(body: ByteArray)
    fun responseCode(): Int
    fun contentType(): String
    /** Null means the response exceeded [maxBytes]. */
    fun readResponse(maxBytes: Int): ByteArray?
    fun disconnect()
}

private data class ConfirmedCompareAuthorization(
    val confirmationId: String,
    val expiresAt: Instant,
    val remainingModels: MutableSet<String>,
) {
    fun isLive(now: Instant): Boolean = now.isBefore(expiresAt)
    fun consumeModel(model: String): Boolean = remainingModels.remove(model)
}

private object AndroidOpenRouterCompareHttpsConnectionFactory : AndroidOpenRouterCompareConnectionFactory {
    override fun open(): AndroidOpenRouterCompareConnection = AndroidOpenRouterCompareHttpsConnection(
        (URL(OPENROUTER_COMPARE_ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        },
    )
}

private class AndroidOpenRouterCompareHttpsConnection(
    private val connection: HttpsURLConnection,
) : AndroidOpenRouterCompareConnection {
    override fun setHeader(name: String, value: String) = connection.setRequestProperty(name, value)
    override fun write(body: ByteArray) = connection.outputStream.use { it.write(body) }
    override fun responseCode(): Int = connection.responseCode
    override fun contentType(): String = connection.contentType.orEmpty()
    override fun readResponse(maxBytes: Int): ByteArray? = connection.inputStream.use { it.readAtMost(maxBytes) }
    override fun disconnect() = connection.disconnect()
}

private fun AndroidOpenRouterCompareConnection.use(
    request: OpenAiCompatibleHttpRequest,
    key: CharArray,
    cancellation: ProviderTransportCancellationToken,
): OpenAiCompatibleHttpOutcome {
    return try {
        request.headers.forEach(::setHeader)
        // This short-lived String exists only for the TLS header API and is never logged or persisted.
        setHeader("Authorization", "Bearer ${key.concatToString()}")
        if (cancellation.isCancellationRequested()) return OpenAiCompatibleHttpOutcome.Cancelled
        write(request.body)
        if (cancellation.isCancellationRequested()) return OpenAiCompatibleHttpOutcome.Cancelled
        val code = responseCode()
        if (code !in 200..299) return OpenAiCompatibleHttpOutcome.Response(code, "application/json", ByteArray(0))
        val contentType = contentType()
        if (contentType.length > 120) return OpenAiCompatibleHttpOutcome.ResponseTooLarge
        val body = readResponse(OpenAiCompatibleHttpOutcome.MAX_RESPONSE_BYTES) ?: return OpenAiCompatibleHttpOutcome.ResponseTooLarge
        if (cancellation.isCancellationRequested()) OpenAiCompatibleHttpOutcome.Cancelled
        else OpenAiCompatibleHttpOutcome.Response(code, contentType, body)
    } catch (_: SocketTimeoutException) {
        OpenAiCompatibleHttpOutcome.TimedOut
    } catch (_: Exception) {
        OpenAiCompatibleHttpOutcome.NetworkUnavailable
    } finally {
        disconnect()
    }
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

private const val OPENROUTER_COMPARE_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 30_000
