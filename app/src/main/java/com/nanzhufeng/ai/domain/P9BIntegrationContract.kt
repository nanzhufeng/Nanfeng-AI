package com.nanzhufeng.ai.domain

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock

/**
 * P9-B's local-only integration grammar. It deliberately has no Android IPC, target registry,
 * file access or network dependency. A real application adapter must receive its own contract.
 */
const val P9B_FORMAT = "nfai.integration-contract"
const val P9B_VERSION = 1

enum class P9BState { REQUESTED, AUTHORIZED, PREVIEWED, CONFIRMED, RESULT_READY, READBACK_VERIFIED, REVOKED, REJECTED, CANCELLED, EXPIRED }
enum class P9BCapability { READ_ONLY_PREVIEW }
enum class P9BPermission { READ_ONLY }
enum class P9BClassification { NON_SENSITIVE, HIGH_SENSITIVE, UNKNOWN }

data class P9BProvenance(val source: String, val revision: Long, val contentHash: String)
data class P9BRequest(
    val requestId: String,
    val idempotencyKey: String,
    val appHandle: String,
    val subjectHandle: String,
    val capability: P9BCapability,
    val permission: P9BPermission,
    val classification: P9BClassification,
    val provenance: P9BProvenance,
    val pageLimit: Int,
    val pageCursor: String?,
    val expiresAtEpochMs: Long?,
)
data class P9BPreview(val revision: Long, val contentHash: String, val itemCount: Int, val nextCursor: String?)
data class P9BSession(
    val request: P9BRequest,
    val state: P9BState,
    val preview: P9BPreview? = null,
    val resultHash: String? = null,
    val errorCode: String? = null,
)
data class P9BEvent(val sequence: Long, val kind: String, val fingerprint: String)
data class P9BReceipt(val idempotencyKey: String, val resultHash: String)
data class P9BSnapshot(val session: P9BSession, val events: List<P9BEvent>)

sealed interface P9BResult {
    data class Accepted(val snapshot: P9BSnapshot) : P9BResult
    data class Replayed(val snapshot: P9BSnapshot) : P9BResult
    data class Rejected(val code: String, val snapshot: P9BSnapshot? = null) : P9BResult
}

interface P9BIntegrationLedger {
    fun byRequest(requestId: String): P9BSnapshot?
    fun byIdempotency(key: String): P9BSnapshot?
    fun create(session: P9BSession, event: P9BEvent): P9BSnapshot
    fun append(snapshot: P9BSnapshot, session: P9BSession, event: P9BEvent, receipt: P9BReceipt? = null): P9BSnapshot
}

/** A local test target is synthetic metadata only; it never exposes body text or a storage handle. */
interface P9BLocalTestOnlyTarget {
    val appHandle: String
    fun preview(subjectHandle: String, limit: Int, cursor: String?): P9BPreview
    fun readback(subjectHandle: String): P9BPreview
}

object P9BContractParser {
    fun parse(raw: String): P9BResult {
        return try {
            val root = JSONObject(raw)
            requireKeys(root, setOf("format", "version", "mode", "requestId", "idempotencyKey", "appHandle", "subjectHandle", "capability", "permission", "classification", "provenance", "page", "expiresAtEpochMs"))
            if (root.optString("format") != P9B_FORMAT || root.optInt("version", -1) != P9B_VERSION || root.optString("mode") != "LOCAL_TEST_ONLY") throw IllegalArgumentException("UNKNOWN_OR_INVALID_SCHEMA")
            val provenance = root.getJSONObject("provenance").also { requireKeys(it, setOf("source", "revision", "contentHash")) }
            val page = root.getJSONObject("page").also { requireKeys(it, setOf("limit", "cursor")) }
            val request = P9BRequest(
                requestId = opaque(root.getString("requestId")), idempotencyKey = opaque(root.getString("idempotencyKey")),
                appHandle = opaque(root.getString("appHandle")), subjectHandle = opaque(root.getString("subjectHandle")),
                capability = enumValue(root.getString("capability"), P9BCapability.values()),
                permission = enumValue(root.getString("permission"), P9BPermission.values()),
                classification = enumValue(root.getString("classification"), P9BClassification.values()),
                provenance = P9BProvenance(provenance.getString("source"), provenance.getLong("revision"), provenance.getString("contentHash")),
                pageLimit = page.getInt("limit"), pageCursor = if (page.isNull("cursor")) null else opaque(page.getString("cursor")),
                expiresAtEpochMs = if (root.isNull("expiresAtEpochMs")) null else root.getLong("expiresAtEpochMs"),
            )
            val code = preflight(request)
            if (code == null) P9BResult.Accepted(P9BSnapshot(P9BSession(request, P9BState.REQUESTED), emptyList())) else P9BResult.Rejected(code)
        } catch (_: Exception) { P9BResult.Rejected("UNKNOWN_OR_INVALID_SCHEMA") }
    }

    fun preflight(request: P9BRequest): String? = when {
        request.classification != P9BClassification.NON_SENSITIVE -> "HIGH_SENSITIVE_OR_UNKNOWN_DENIED"
        request.capability != P9BCapability.READ_ONLY_PREVIEW || request.permission != P9BPermission.READ_ONLY -> "PERMISSION_OR_CAPABILITY_DENIED"
        request.provenance.source != "LOCAL_TEST_ONLY" || request.provenance.revision <= 0 || !hash(request.provenance.contentHash) -> "PROVENANCE_INVALID"
        request.pageLimit !in 1..25 -> "PAGINATION_LIMIT_DENIED"
        request.expiresAtEpochMs != null && request.expiresAtEpochMs < 0 -> "EXPIRY_INVALID"
        else -> null
    }

    private fun requireKeys(value: JSONObject, allowed: Set<String>) {
        val keys = value.keys().asSequence().toSet()
        if (!allowed.containsAll(keys) || keys.any { it in setOf("uri", "path", "token", "database", "db", "credential", "authorization", "body", "content") }) throw IllegalArgumentException("UNKNOWN_FIELD")
    }
    private fun opaque(value: String): String {
        require(value.matches(Regex("[a-z][a-z0-9_-]{2,63}"))) { "opaque handle invalid" }; return value
    }
    private fun hash(value: String) = value.matches(Regex("[0-9a-f]{64}"))
    private fun <T : Enum<T>> enumValue(value: String, values: Array<T>): T = values.firstOrNull { it.name == value } ?: throw IllegalArgumentException("unknown enum")
}

/** Test-only callers must inject a synthetic target; production has no target registry or adapter. */
class P9BLocalTestOnlyHarness(private val ledger: P9BIntegrationLedger, private val target: P9BLocalTestOnlyTarget, private val clock: Clock) {
    fun request(raw: String): P9BResult {
        val parsed = P9BContractParser.parse(raw)
        val request = ((parsed as? P9BResult.Accepted)?.snapshot?.session?.request) ?: return parsed
        ledger.byIdempotency(request.idempotencyKey)?.let { return P9BResult.Replayed(it) }
        val session = P9BSession(request, P9BState.REQUESTED)
        return P9BResult.Accepted(ledger.create(session, event(session, "REQUESTED")))
    }
    fun authorize(requestId: String): P9BResult = transition(requestId, setOf(P9BState.REQUESTED), "AUTHORIZED") { session ->
        when {
            session.request.appHandle != target.appHandle -> P9BResult.Rejected("APP_SCOPE_DENIED", reject(session, "APP_SCOPE_DENIED"))
            session.request.expiresAtEpochMs?.let { clock.millis() > it } == true -> P9BResult.Rejected("PERMISSION_EXPIRED", expire(session))
            else -> null
        }
    }
    fun preview(requestId: String): P9BResult = transition(requestId, setOf(P9BState.AUTHORIZED), "PREVIEWED") { session ->
        val preview = target.preview(session.request.subjectHandle, session.request.pageLimit, session.request.pageCursor)
        if (preview.itemCount !in 0..session.request.pageLimit || preview.revision != session.request.provenance.revision || preview.contentHash != session.request.provenance.contentHash) P9BResult.Rejected("SOURCE_PROVENANCE_MISMATCH", reject(session, "SOURCE_PROVENANCE_MISMATCH"))
        else P9BResult.Accepted(append(session, session.copy(state = P9BState.PREVIEWED, preview = preview), "PREVIEWED"))
    }
    fun confirm(requestId: String): P9BResult = transition(requestId, setOf(P9BState.PREVIEWED), "CONFIRMED")
    fun result(requestId: String): P9BResult {
        ledger.byRequest(requestId)?.let { snapshot ->
            if (snapshot.session.state in setOf(P9BState.RESULT_READY, P9BState.READBACK_VERIFIED, P9BState.REVOKED)) return P9BResult.Replayed(snapshot)
        }
        return transition(requestId, setOf(P9BState.CONFIRMED), "RESULT_READY") { session ->
            val hash = p9bHash("${session.request.requestId}|${session.preview!!.revision}|${session.preview.contentHash}")
            P9BResult.Accepted(append(session, session.copy(state = P9BState.RESULT_READY, resultHash = hash), "RESULT_READY", P9BReceipt(session.request.idempotencyKey, hash)))
        }
    }
    fun readback(requestId: String): P9BResult = transition(requestId, setOf(P9BState.RESULT_READY), "READBACK_VERIFIED") { session ->
        val now = target.readback(session.request.subjectHandle)
        val preview = session.preview!!
        if (now.revision != preview.revision || now.contentHash != preview.contentHash) P9BResult.Rejected("TARGET_UPDATED", reject(session, "TARGET_UPDATED"))
        else null
    }
    fun revoke(requestId: String): P9BResult = transition(requestId, setOf(P9BState.AUTHORIZED, P9BState.PREVIEWED, P9BState.CONFIRMED, P9BState.RESULT_READY, P9BState.READBACK_VERIFIED), "REVOKED")
    fun cancel(requestId: String): P9BResult = transition(requestId, setOf(P9BState.REQUESTED, P9BState.AUTHORIZED, P9BState.PREVIEWED, P9BState.CONFIRMED, P9BState.RESULT_READY), "CANCELLED")

    private fun transition(id: String, allowed: Set<P9BState>, event: String, override: (P9BSession) -> P9BResult? = { null }): P9BResult {
        val snapshot = ledger.byRequest(id) ?: return P9BResult.Rejected("REQUEST_NOT_FOUND")
        if (snapshot.session.state !in allowed) return P9BResult.Rejected("INVALID_STATE_TRANSITION", snapshot)
        override(snapshot.session)?.let { return it }
        return P9BResult.Accepted(append(snapshot.session, snapshot.session.copy(state = P9BState.valueOf(event)), event))
    }
    private fun append(old: P9BSession, next: P9BSession, kind: String, receipt: P9BReceipt? = null) = ledger.append(ledger.byRequest(old.request.requestId)!!, next, event(next, kind), receipt)
    private fun reject(session: P9BSession, code: String) = append(session, session.copy(state = P9BState.REJECTED, errorCode = code), code)
    private fun expire(session: P9BSession) = append(session, session.copy(state = P9BState.EXPIRED, errorCode = "PERMISSION_EXPIRED"), "PERMISSION_EXPIRED")
    private fun event(session: P9BSession, kind: String) = P9BEvent(ledger.byRequest(session.request.requestId)?.events?.size?.toLong() ?: 0, kind, p9bHash("${session.request.requestId}|$kind|${session.state}|${session.resultHash ?: ""}"))
}

fun p9bHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
