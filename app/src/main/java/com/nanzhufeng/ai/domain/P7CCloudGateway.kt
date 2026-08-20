package com.nanzhufeng.ai.domain

import org.json.JSONObject

/**
 * P7-C transport boundary. It deliberately sees only the P7-A envelope, never Room entities,
 * profile data, recovery codes, Provider credentials, or authentication tokens.
 */
data class P7CPrivateServiceConfig(
    val supabaseUrl: String,
    val publishableKey: String,
    val googleWebClientId: String,
) {
    init {
        require(supabaseUrl.startsWith("https://") && !supabaseUrl.contains('@'))
        require(publishableKey.isNotBlank() && googleWebClientId.isNotBlank())
    }
}

sealed interface P7CServiceAvailability {
    data object Disabled : P7CServiceAvailability
    class Configured internal constructor(val config: P7CPrivateServiceConfig) : P7CServiceAvailability
}

object P7CServiceConfiguration {
    /** Values arrive only from private build injection. Missing or malformed input is offline-safe. */
    fun resolve(supabaseUrl: String?, publishableKey: String?, googleWebClientId: String?): P7CServiceAvailability = runCatching {
        P7CServiceAvailability.Configured(
            P7CPrivateServiceConfig(
                supabaseUrl.orEmpty().trim(),
                publishableKey.orEmpty().trim(),
                googleWebClientId.orEmpty().trim(),
            ),
        )
    }.getOrDefault(P7CServiceAvailability.Disabled)
}

data class P7CRemoteEnvelope(
    val documentId: String,
    val revision: Long,
    val payloadHash: String,
    val canonicalEnvelope: String,
)

data class P7CCommitReceipt(val revision: Long, val payloadHash: String)

sealed interface P7CCloudResult<out T> {
    data class Value<T>(val value: T) : P7CCloudResult<T>
    data object Disabled : P7CCloudResult<Nothing>
    data class Rejected(val code: String) : P7CCloudResult<Nothing>
}

/** Authentication/session ownership is intentionally outside this interface and remains transient. */
interface P7CAuthenticatedRpcTransport {
    fun call(function: String, body: String): String
}

interface P7CCloudGateway {
    fun read(documentId: String, minimumRevision: Long): P7CCloudResult<P7CRemoteEnvelope>
    fun commit(expectedRevision: Long, canonicalEnvelope: String): P7CCloudResult<P7CCommitReceipt>
}

object P7CDisabledCloudGateway : P7CCloudGateway {
    override fun read(documentId: String, minimumRevision: Long) = P7CCloudResult.Disabled
    override fun commit(expectedRevision: Long, canonicalEnvelope: String) = P7CCloudResult.Disabled
}

/**
 * Enabled only after private configuration exists and a future Credential-Manager -> Supabase
 * authentication owner supplies a transient authenticated transport. This class never accepts,
 * stores, logs, or returns ID/access/refresh tokens.
 */
class P7CSupabaseEnvelopeGateway(
    private val config: P7CPrivateServiceConfig,
    private val transport: P7CAuthenticatedRpcTransport,
) : P7CCloudGateway {
    override fun read(documentId: String, minimumRevision: Long): P7CCloudResult<P7CRemoteEnvelope> = runCatching {
        requireId(documentId); require(minimumRevision >= 0)
        val response = JSONObject(transport.call("nanfeng_sync_read_document", JSONObject().put("p_app_id", APP_ID).put("p_document_id", documentId).toString()))
        if (response.optBoolean("missing", false)) return P7CCloudResult.Rejected("REMOTE_MISSING")
        val envelope = response.getJSONObject("envelope").toString()
        val preflight = (NfaiSyncV1Gateway.preflight(envelope) as? NfaiSyncResult.Preflighted)?.value ?: return P7CCloudResult.Rejected("REMOTE_ENVELOPE_REJECTED")
        if (preflight.appId != APP_ID || preflight.documentId != documentId || preflight.revision < minimumRevision) return P7CCloudResult.Rejected("REMOTE_ENVELOPE_REJECTED")
        P7CCloudResult.Value(P7CRemoteEnvelope(documentId, preflight.revision, preflight.payloadHash, envelope))
    }.getOrElse { P7CCloudResult.Rejected("REMOTE_READ_REJECTED") }

    override fun commit(expectedRevision: Long, canonicalEnvelope: String): P7CCloudResult<P7CCommitReceipt> = runCatching {
        require(expectedRevision >= 0)
        val preflight = (NfaiSyncV1Gateway.preflight(canonicalEnvelope) as? NfaiSyncResult.Preflighted)?.value ?: return P7CCloudResult.Rejected("LOCAL_ENVELOPE_REJECTED")
        if (preflight.appId != APP_ID || preflight.revision != expectedRevision + 1) return P7CCloudResult.Rejected("LOCAL_ENVELOPE_REJECTED")
        val body = JSONObject()
            .put("p_app_id", APP_ID)
            .put("p_document_id", preflight.documentId)
            .put("p_expected_revision", expectedRevision)
            .put("p_envelope", JSONObject(canonicalEnvelope))
        val response = JSONObject(transport.call("nanfeng_sync_commit_document", body.toString()))
        val revision = response.getLong("revision"); val hash = response.getString("payload_hash")
        if (revision != preflight.revision || hash != preflight.payloadHash) P7CCloudResult.Rejected("REMOTE_RECEIPT_REJECTED")
        else P7CCloudResult.Value(P7CCommitReceipt(revision, hash))
    }.getOrElse { P7CCloudResult.Rejected("REMOTE_COMMIT_REJECTED") }

    private fun requireId(value: String) = require(value.matches(Regex("[A-Za-z0-9._-]{2,128}")))
    private companion object { const val APP_ID = "com.nanzhufeng.ai" }
}
