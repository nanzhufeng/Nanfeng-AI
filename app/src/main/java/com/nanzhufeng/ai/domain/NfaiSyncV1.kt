package com.nanzhufeng.ai.domain

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** P7-A local-only E2EE envelope. It has no account, Room, filesystem, or network ownership. */
const val NFAI_SYNC_V1_ENVELOPE_FORMAT = "nfai.sync.envelope"
private const val PAYLOAD_FORMAT = "nfai.sync.payload"
private const val DIRECT_FORMAT = "nfai.sync.direct"
private const val SYNC_VERSION = 1
private const val KDF_ITERATIONS = 210_000
private const val MAX_ENVELOPE_BYTES = 2 * 1024 * 1024
private const val MAX_PAYLOAD_BYTES = 1024 * 1024
private const val MAX_RECORDS = 10_000
private val SYNC_KINDS = setOf("project", "conversation", "knowledge", "memory", "relation", "safe_settings")
private val FORBIDDEN_SYNC_KEY = Regex("(?i)(credential|api[_-]?key|authorization|provider|token|prompt|runspec|runtime|diagnostic|uri|path|avatar|recovery|private[_-]?key)")

data class NfaiSyncRecord(val kind: String, val id: String, val revision: Long, val classification: String, val contentJson: String)
data class NfaiSyncPreparedSnapshot(val appId: String, val documentId: String, val revision: Long, val records: List<NfaiSyncRecord>)
data class NfaiSyncKnownAnswerMaterial(val dataKey: ByteArray, val salt: ByteArray, val wrappingNonce: ByteArray, val payloadNonce: ByteArray)
data class NfaiSyncAccountWrappingMaterial(val wrappingKey: ByteArray, val salt: ByteArray) {
    init { require(wrappingKey.size == 32 && salt.size == 16) }
}
data class NfaiSyncPreflight(val appId: String, val documentId: String, val revision: Long, val payloadHash: String, val payloadByteCount: Int)
data class NfaiSyncOpenedSnapshot(val snapshot: NfaiSyncPreparedSnapshot, val canonicalPayload: String)

/** Shared P7-A ingress detector: classification is authoritative; key-shape scanning is a backstop. */
object NfaiSyncSensitivityDetector {
    fun requireSafe(classification: String, value: Any?, depth: Int = 0) {
        require(classification == "NORMAL") { "HIGH_SENSITIVE records never enter sync payloads." }
        requireContentSafe(value, depth)
    }
    private fun requireContentSafe(value: Any?, depth: Int) {
        require(depth <= 32)
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key ->
                val child = value.get(key)
                if (key == "modelUsage") {
                    requirePortableModelUsage(child)
                } else {
                    require(!FORBIDDEN_SYNC_KEY.containsMatchIn(key))
                    require(!(key == "classification" && child == "HIGH_SENSITIVE"))
                    requireContentSafe(child, depth + 1)
                }
            }
            is JSONArray -> repeat(value.length()) { requireContentSafe(value.get(it), depth + 1) }
        }
    }

    /** Same fixed accounting boundary as Desktop; token counts are not credentials. */
    private fun requirePortableModelUsage(value: Any?) {
        require(value is JSONObject)
        val counters = setOf("inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens")
        require(value.keys().asSequence().toSet() == counters + setOf(
            "modelId", "modelDisplayName", "costPriceVersion", "costCurrencyCode", "costTotalMicros", "costSource",
        ))
        fun boundedString(key: String, max: Int, nullable: Boolean = false) {
            val field = value.get(key)
            require((nullable && field == JSONObject.NULL) ||
                (field is String && field.isNotEmpty() && field.toByteArray(StandardCharsets.UTF_8).size <= max))
        }
        fun boundedInteger(key: String, max: Long) {
            val field = value.get(key)
            require(field == JSONObject.NULL || ((field is Int || field is Long) && (field as Number).toLong() in 0..max))
        }
        boundedString("modelId", 256)
        boundedString("modelDisplayName", 256)
        counters.forEach { boundedInteger(it, 10_000_000) }
        boundedString("costPriceVersion", 128, true)
        val currency = value.get("costCurrencyCode")
        require(currency == JSONObject.NULL || (currency is String && currency.matches(Regex("[A-Z]{3}"))))
        boundedInteger("costTotalMicros", 1_000_000_000_000)
        val source = value.get("costSource")
        require(source == JSONObject.NULL || source in setOf("PROVIDER_RESPONSE", "LOCAL_ESTIMATE"))
    }
}

sealed interface NfaiSyncResult {
    data class Sealed(val canonicalEnvelope: String) : NfaiSyncResult
    data class Preflighted(val value: NfaiSyncPreflight) : NfaiSyncResult
    data class Opened(val value: NfaiSyncOpenedSnapshot) : NfaiSyncResult
    data class Rejected(val code: String) : NfaiSyncResult
}

object NfaiSyncV1Gateway {
    private val random = SecureRandom()

    /** P7-A deliberately accepts a caller-held key; P7-B will own per-account key creation/storage. */
    fun seal(snapshot: NfaiSyncPreparedSnapshot, recoveryCode: CharArray, dataKey: ByteArray): NfaiSyncResult =
        sealInternal(snapshot, recoveryCode, NfaiSyncKnownAnswerMaterial(dataKey, ByteArray(16).also(random::nextBytes), ByteArray(12).also(random::nextBytes), ByteArray(12).also(random::nextBytes)))

    /** Test-only known-answer path; production callers must use [seal]. */
    fun sealKnownAnswer(snapshot: NfaiSyncPreparedSnapshot, recoveryCode: CharArray, material: NfaiSyncKnownAnswerMaterial): NfaiSyncResult =
        sealInternal(snapshot, recoveryCode, material)

    /** Google-account-authorized direct sync: no recovery code, ciphertext, or key material. */
    fun sealDirect(snapshot: NfaiSyncPreparedSnapshot): NfaiSyncResult = runCatching {
        val payload = payloadFor(snapshot)
        // Direct envelopes are shared with Desktop's serde_json implementation.  Do not use
        // JSONObject.quote here: it escapes `</` and U+2000..U+20FF differently, making a
        // faithfully returned Desktop document fail its hash check on Android.
        val canonicalPayload = canonicalDirect(payload)
        val bytes = canonicalPayload.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..MAX_PAYLOAD_BYTES)
        val envelope = JSONObject()
            .put("format", DIRECT_FORMAT)
            .put("protocolVersion", SYNC_VERSION)
            .put("schemaVersion", SYNC_VERSION)
            .put("appId", snapshot.appId)
            .put("documentId", snapshot.documentId)
            .put("revision", snapshot.revision)
            .put("payloadHash", sha256(bytes))
            .put("payloadByteCount", bytes.size)
            .put("payload", payload)
        NfaiSyncResult.Sealed(canonical(envelope))
    }.getOrElse { NfaiSyncResult.Rejected("DIRECT_SEAL_REJECTED") }

    /** Opens only the direct, Google-authorized representation. Legacy encrypted records are
     * deliberately not a user-action requirement; their source device migrates them on its next sync. */
    fun openDirect(envelopeJson: String, expectedAppId: String, expectedDocumentId: String, minimumRevision: Long): NfaiSyncResult {
        var failureCode = "DIRECT_ENVELOPE_REJECTED"
        return runCatching {
        val (preflight, envelope) = parseDirectEnvelope(envelopeJson)
        failureCode = "DIRECT_BINDING_REJECTED"
        require(preflight.appId == expectedAppId && preflight.documentId == expectedDocumentId)
        require(preflight.revision >= minimumRevision)
        val payload = envelope.getJSONObject("payload")
        failureCode = "DIRECT_CANONICAL_REJECTED"
        val directCanonicalPayload = canonicalDirect(payload).toByteArray(StandardCharsets.UTF_8)
        val legacyAndroidCanonicalPayload = canonical(payload).toByteArray(StandardCharsets.UTF_8)
        // Accept the old Android direct spelling only when its own byte count and hash match.
        // This preserves already committed Android documents while all newly sealed documents
        // use the Desktop-compatible spelling above.
        failureCode = "DIRECT_HASH_MISMATCH"
        require(
            (directCanonicalPayload.size == preflight.payloadByteCount && sha256(directCanonicalPayload) == preflight.payloadHash) ||
                (legacyAndroidCanonicalPayload.size == preflight.payloadByteCount && sha256(legacyAndroidCanonicalPayload) == preflight.payloadHash),
        )
        failureCode = "DIRECT_PAYLOAD_REJECTED"
        val snapshot = parsePayload(payload)
        failureCode = "DIRECT_BINDING_REJECTED"
        require(snapshot.appId == preflight.appId && snapshot.documentId == preflight.documentId && snapshot.revision == preflight.revision)
        NfaiSyncResult.Opened(NfaiSyncOpenedSnapshot(snapshot, canonicalDirect(payload)))
        }.getOrElse { NfaiSyncResult.Rejected(failureCode) }
    }

    /** The recovery code is consumed once; only its Keystore-protected derived material is retained. */
    fun createAccountWrappingMaterial(recoveryCode: CharArray): NfaiSyncAccountWrappingMaterial {
        require(recoveryCode.size >= 12)
        val salt = ByteArray(16).also(random::nextBytes)
        return NfaiSyncAccountWrappingMaterial(derive(recoveryCode, salt), salt)
    }

    fun sealWithAccountWrappingMaterial(
        snapshot: NfaiSyncPreparedSnapshot,
        dataKey: ByteArray,
        material: NfaiSyncAccountWrappingMaterial,
    ): NfaiSyncResult = sealInternal(
        snapshot = snapshot,
        recoveryCode = CharArray(0),
        known = NfaiSyncKnownAnswerMaterial(
            dataKey = dataKey,
            salt = material.salt,
            wrappingNonce = ByteArray(12).also(random::nextBytes),
            payloadNonce = ByteArray(12).also(random::nextBytes),
        ),
        wrappingKeyOverride = material.wrappingKey,
    )

    fun preflight(envelopeJson: String): NfaiSyncResult = runCatching {
        val root = strictObject(envelopeJson)
        NfaiSyncResult.Preflighted(if (root.optString("format") == DIRECT_FORMAT) parseDirectEnvelope(envelopeJson).first else parseEnvelope(envelopeJson).first)
    }
        .getOrElse { NfaiSyncResult.Rejected("PREFLIGHT_REJECTED") }

    fun open(envelopeJson: String, recoveryCode: CharArray, expectedAppId: String, expectedDocumentId: String, minimumRevision: Long): NfaiSyncResult = openInternal(
        envelopeJson, expectedAppId, expectedDocumentId, minimumRevision,
    ) { salt -> derive(recoveryCode, salt) }

    /** Opens a locally protected envelope without ever reconstructing the recovery code. */
    fun openWithAccountWrappingMaterial(
        envelopeJson: String,
        material: NfaiSyncAccountWrappingMaterial,
        expectedAppId: String,
        expectedDocumentId: String,
        minimumRevision: Long,
    ): NfaiSyncResult = openInternal(envelopeJson, expectedAppId, expectedDocumentId, minimumRevision) { salt ->
        require(material.salt.contentEquals(salt)) { "wrapping material mismatch" }
        material.wrappingKey.copyOf()
    }

    private fun openInternal(
        envelopeJson: String,
        expectedAppId: String,
        expectedDocumentId: String,
        minimumRevision: Long,
        wrappingKeyForSalt: (ByteArray) -> ByteArray,
    ): NfaiSyncResult = runCatching {
        val (preflight, envelope) = parseEnvelope(envelopeJson)
        require(preflight.appId == expectedAppId && preflight.documentId == expectedDocumentId) { "跨 App/document 拒绝" }
        require(preflight.revision >= minimumRevision) { "REVISION_ROLLBACK" }
        val aad = aad(envelope)
        val wrappingKey = wrappingKeyForSalt(decode(envelope.getJSONObject("kdf").getString("salt"), 16))
        val dataKey = decrypt(wrappingKey, decode(envelope.getJSONObject("wrappedDataKey").getString("nonce"), 12), decode(envelope.getJSONObject("wrappedDataKey").getString("ciphertext"), 48), aad)
        try {
            val plain = decrypt(dataKey, decode(envelope.getJSONObject("payload").getString("nonce"), 12), decodeVariable(envelope.getJSONObject("payload").getString("ciphertext"), MAX_PAYLOAD_BYTES + 16), aad)
            try {
                require(plain.size == preflight.payloadByteCount && sha256(plain) == preflight.payloadHash) { "payload hash 不一致" }
                val payload = strictObject(plain.toString(StandardCharsets.UTF_8)); val snapshot = parsePayload(payload)
                require(snapshot.appId == expectedAppId && snapshot.documentId == expectedDocumentId && snapshot.revision == preflight.revision) { "payload header 不一致" }
                NfaiSyncResult.Opened(NfaiSyncOpenedSnapshot(snapshot, canonical(payload)))
            } finally { plain.fill(0) }
        } finally { dataKey.fill(0); wrappingKey.fill(0) }
    }.getOrElse { error -> NfaiSyncResult.Rejected(if (error.message == "REVISION_ROLLBACK") "REVISION_ROLLBACK" else "OPEN_REJECTED") }

    private fun sealInternal(
        snapshot: NfaiSyncPreparedSnapshot,
        recoveryCode: CharArray,
        known: NfaiSyncKnownAnswerMaterial,
        wrappingKeyOverride: ByteArray? = null,
    ): NfaiSyncResult = runCatching {
        val payload = payloadFor(snapshot); val plain = canonical(payload).toByteArray(StandardCharsets.UTF_8)
        require(plain.size <= MAX_PAYLOAD_BYTES) { "payload 超限" }
        val dataKey = known.dataKey.copyOf(); val salt = known.salt.copyOf(); val wrappingNonce = known.wrappingNonce.copyOf(); val payloadNonce = known.payloadNonce.copyOf()
        try {
            require(dataKey.size == 32 && salt.size == 16 && wrappingNonce.size == 12 && payloadNonce.size == 12 && !wrappingNonce.contentEquals(payloadNonce)) { "随机材料无效" }
            val envelope = JSONObject().apply {
                put("format", NFAI_SYNC_V1_ENVELOPE_FORMAT); put("protocolVersion", SYNC_VERSION); put("schemaVersion", SYNC_VERSION)
                put("appId", snapshot.appId); put("documentId", snapshot.documentId); put("revision", snapshot.revision)
                put("payloadHash", sha256(plain)); put("payloadByteCount", plain.size)
                put("kdf", JSONObject().put("algorithm", "PBKDF2-HMAC-SHA256").put("version", SYNC_VERSION).put("iterations", KDF_ITERATIONS).put("salt", encode(salt)))
                put("wrappedDataKey", JSONObject().put("algorithm", "AES-256-GCM").put("nonce", encode(wrappingNonce)).put("ciphertext", ""))
                put("payload", JSONObject().put("algorithm", "AES-256-GCM").put("nonce", encode(payloadNonce)).put("ciphertext", ""))
            }
            val wrappingKey = wrappingKeyOverride?.copyOf() ?: derive(recoveryCode, salt); val aad = aad(envelope)
            try {
                envelope.getJSONObject("wrappedDataKey").put("ciphertext", encode(encrypt(wrappingKey, wrappingNonce, dataKey, aad)))
                envelope.getJSONObject("payload").put("ciphertext", encode(encrypt(dataKey, payloadNonce, plain, aad)))
                val result = canonical(envelope); require(result.toByteArray(StandardCharsets.UTF_8).size <= MAX_ENVELOPE_BYTES)
                NfaiSyncResult.Sealed(result)
            } finally { wrappingKey.fill(0) }
        } finally { plain.fill(0); dataKey.fill(0); salt.fill(0); wrappingNonce.fill(0); payloadNonce.fill(0) }
    }.getOrElse { NfaiSyncResult.Rejected("SEAL_REJECTED") }

    private fun parseEnvelope(text: String): Pair<NfaiSyncPreflight, JSONObject> {
        require(text.toByteArray(StandardCharsets.UTF_8).size in 1..MAX_ENVELOPE_BYTES) { "envelope 超限" }
        val root = strictObject(text); root.requireExact("format", "protocolVersion", "schemaVersion", "appId", "documentId", "revision", "payloadHash", "payloadByteCount", "kdf", "wrappedDataKey", "payload")
        require(root.getString("format") == NFAI_SYNC_V1_ENVELOPE_FORMAT && root.getInt("protocolVersion") == SYNC_VERSION && root.getInt("schemaVersion") == SYNC_VERSION)
        val appId = root.getString("appId"); val documentId = root.getString("documentId"); requireId(appId); requireId(documentId)
        val revision = root.getLong("revision"); require(revision > 0); val hash = root.getString("payloadHash"); requireHash(hash)
        val byteCount = root.getInt("payloadByteCount"); require(byteCount in 1..MAX_PAYLOAD_BYTES)
        val kdf = root.getJSONObject("kdf"); kdf.requireExact("algorithm", "version", "iterations", "salt"); require(kdf.getString("algorithm") == "PBKDF2-HMAC-SHA256" && kdf.getInt("version") == SYNC_VERSION && kdf.getInt("iterations") == KDF_ITERATIONS); decode(kdf.getString("salt"), 16)
        listOf("wrappedDataKey", "payload").forEach { name -> root.getJSONObject(name).requireExact("algorithm", "nonce", "ciphertext"); require(root.getJSONObject(name).getString("algorithm") == "AES-256-GCM"); decode(root.getJSONObject(name).getString("nonce"), 12) }
        decode(root.getJSONObject("wrappedDataKey").getString("ciphertext"), 48); decodeVariable(root.getJSONObject("payload").getString("ciphertext"), MAX_PAYLOAD_BYTES + 16)
        return NfaiSyncPreflight(appId, documentId, revision, hash, byteCount) to root
    }

    private fun parseDirectEnvelope(text: String): Pair<NfaiSyncPreflight, JSONObject> {
        require(text.toByteArray(StandardCharsets.UTF_8).size in 1..MAX_ENVELOPE_BYTES)
        val root = strictObject(text)
        root.requireExact("format", "protocolVersion", "schemaVersion", "appId", "documentId", "revision", "payloadHash", "payloadByteCount", "payload")
        require(root.getString("format") == DIRECT_FORMAT && root.getInt("protocolVersion") == SYNC_VERSION && root.getInt("schemaVersion") == SYNC_VERSION)
        val appId = root.getString("appId"); val documentId = root.getString("documentId"); requireId(appId); requireId(documentId)
        val revision = root.getLong("revision"); require(revision > 0)
        val hash = root.getString("payloadHash"); requireHash(hash)
        val byteCount = root.getInt("payloadByteCount"); require(byteCount in 1..MAX_PAYLOAD_BYTES)
        return NfaiSyncPreflight(appId, documentId, revision, hash, byteCount) to root
    }

    private fun payloadFor(snapshot: NfaiSyncPreparedSnapshot): JSONObject {
        requireId(snapshot.appId); requireId(snapshot.documentId); require(snapshot.revision > 0 && snapshot.records.size <= MAX_RECORDS)
        val seen = mutableSetOf<String>(); val records = JSONArray()
        snapshot.records.forEach { record ->
            require(record.kind in SYNC_KINDS && record.revision > 0); requireId(record.id); require(seen.add(record.id))
            val content = strictObject(record.contentJson); NfaiSyncSensitivityDetector.requireSafe(record.classification, content)
            records.put(JSONObject().put("kind", record.kind).put("id", record.id).put("revision", record.revision).put("classification", record.classification).put("content", content))
        }
        return JSONObject().put("format", PAYLOAD_FORMAT).put("protocolVersion", SYNC_VERSION).put("schemaVersion", SYNC_VERSION).put("appId", snapshot.appId).put("documentId", snapshot.documentId).put("revision", snapshot.revision).put("records", records)
    }

    private fun parsePayload(payload: JSONObject): NfaiSyncPreparedSnapshot {
        payload.requireExact("format", "protocolVersion", "schemaVersion", "appId", "documentId", "revision", "records"); require(payload.getString("format") == PAYLOAD_FORMAT && payload.getInt("protocolVersion") == SYNC_VERSION && payload.getInt("schemaVersion") == SYNC_VERSION)
        val appId = payload.getString("appId"); val documentId = payload.getString("documentId"); requireId(appId); requireId(documentId); val revision = payload.getLong("revision"); require(revision > 0)
        val values = payload.getJSONArray("records"); require(values.length() <= MAX_RECORDS); val ids = mutableSetOf<String>(); val records = buildList {
            repeat(values.length()) { index -> val value = values.getJSONObject(index); value.requireExact("kind", "id", "revision", "classification", "content"); val kind = value.getString("kind"); val id = value.getString("id"); val classification = value.getString("classification"); require(kind in SYNC_KINDS && ids.add(id)); requireId(id); val itemRevision = value.getLong("revision"); require(itemRevision > 0); val content = value.getJSONObject("content"); NfaiSyncSensitivityDetector.requireSafe(classification, content); add(NfaiSyncRecord(kind, id, itemRevision, classification, canonical(content))) }
        }
        return NfaiSyncPreparedSnapshot(appId, documentId, revision, records)
    }

    private fun aad(envelope: JSONObject) = canonical(JSONObject().put("appId", envelope.getString("appId")).put("documentId", envelope.getString("documentId")).put("format", envelope.getString("format")).put("payloadHash", envelope.getString("payloadHash")).put("protocolVersion", envelope.getInt("protocolVersion")).put("revision", envelope.getLong("revision")).put("schemaVersion", envelope.getInt("schemaVersion"))).toByteArray(StandardCharsets.UTF_8)
    private fun derive(recovery: CharArray, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(recovery, salt, KDF_ITERATIONS, 256)).encoded
    private fun encrypt(key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad); doFinal(input) }
    private fun decrypt(key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad); doFinal(input) }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun decode(value: String, exact: Int): ByteArray = decodeVariable(value, exact).also { require(it.size == exact) }
    private fun decodeVariable(value: String, max: Int): ByteArray { require(value.matches(Regex("[A-Za-z0-9_-]+"))); val decoded = Base64.decode(value, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP); require(decoded.size in 1..max && encode(decoded) == value); return decoded }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun requireId(value: String) { require(value.matches(Regex("[A-Za-z0-9._-]{2,128}"))) }
    private fun requireHash(value: String) { require(value.matches(Regex("[a-f0-9]{64}"))) }
    private fun strictObject(text: String): JSONObject { rejectDuplicateKeys(text); return JSONObject(text) }
    /** org.json accepts duplicate names, so reject them before its value parser can overwrite one. */
    private fun rejectDuplicateKeys(text: String) {
        class Parser(private val value: String) {
            var index = 0
            fun skip() { while (index < value.length && value[index].isWhitespace()) index++ }
            fun string(): String {
                require(index < value.length && value[index] == '"'); val start = index++ ; var escaped = false
                while (index < value.length) { val char = value[index++]; if (escaped) { escaped = false; continue }; if (char == '\\') { escaped = true; continue }; if (char == '"') return value.substring(start, index); require(char.code >= 0x20) }
                error("unterminated JSON string")
            }
            fun parseValue() {
                skip(); require(index < value.length)
                when (value[index]) {
                    '{' -> { index++; skip(); val keys = mutableSetOf<String>(); if (index < value.length && value[index] == '}') { index++; return }; while (true) { skip(); val key = string(); require(keys.add(key)); skip(); require(index < value.length && value[index++] == ':'); parseValue(); skip(); when { index < value.length && value[index] == ',' -> index++; index < value.length && value[index] == '}' -> { index++; return }; else -> error("invalid JSON object") } } }
                    '[' -> { index++; skip(); if (index < value.length && value[index] == ']') { index++; return }; while (true) { parseValue(); skip(); when { index < value.length && value[index] == ',' -> index++; index < value.length && value[index] == ']' -> { index++; return }; else -> error("invalid JSON array") } } }
                    '"' -> string()
                    else -> { val start = index; while (index < value.length && value[index] !in charArrayOf(',', ']', '}') && !value[index].isWhitespace()) index++; require(index > start) }
                }
            }
        }
        Parser(text).run { parseValue(); skip(); require(index == text.length) }
    }
    private fun JSONObject.requireExact(vararg keys: String) { require(keys().asSequence().toSet() == keys.toSet()) }
    private fun canonical(value: Any?): String = when (value) { null, JSONObject.NULL -> "null"; is String -> JSONObject.quote(value); is Boolean -> value.toString(); is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()).let { value.toLong().toString() }; is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${JSONObject.quote(it)}:${canonical(value.get(it))}" }; is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }; else -> error("unsupported JSON") }

    /** Matches serde_json string emission used by the Desktop direct-sync producer. */
    private fun canonicalDirect(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is String -> directQuote(value)
        is Boolean -> value.toString()
        is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()).let { value.toLong().toString() }
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${directQuote(it)}:${canonicalDirect(value.get(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalDirect(value.get(it)) }
        else -> error("unsupported JSON")
    }

    private fun directQuote(value: String): String = buildString(value.length + 2) {
        append('"')
        var index = 0
        while (index < value.length) {
            when (val character = value[index++]) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> when {
                    character.code < 0x20 -> append("\\u%04x".format(java.util.Locale.ROOT, character.code))
                    character.isHighSurrogate() -> {
                        require(index < value.length && value[index].isLowSurrogate())
                        append(character).append(value[index++])
                    }
                    character.isLowSurrogate() -> error("unpaired surrogate")
                    else -> append(character)
                }
            }
        }
        append('"')
    }
}
