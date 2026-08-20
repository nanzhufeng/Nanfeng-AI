package com.nanzhufeng.ai.domain

/** P6-L1 index: content-free hashes plus an existing local message reference only. */
private const val LOCAL_EXACT_REUSE_KEY_VERSION = "nfai.local-exact-key.v1"

enum class LocalExactReuseOutcome { LOCAL_EXACT_HIT, MISS, INELIGIBLE, UNKNOWN }
enum class LocalExactReuseSensitivity { LOW, MEDIUM, HIGH }

data class LocalExactReuseKey(
    val scopeId: String, val providerId: String, val modelSnapshotId: String, val endpointMode: String,
    val generationParametersHash: String, val toolSchemaHash: String, val contextManifestHash: String,
    val messageTreeHash: String, val attachmentHash: String, val templateVersion: String,
    val policyVersion: Long, val canonicalRequestHash: String, val sensitivity: LocalExactReuseSensitivity,
    val keyVersion: String = LOCAL_EXACT_REUSE_KEY_VERSION,
) {
    init {
        require(scopeId.isStableReuseId() && providerId.isStableReuseId() && modelSnapshotId.isStableReuseId())
        require(endpointMode.isStableReuseId() && templateVersion.isStableReuseId() && policyVersion > 0)
        require(keyVersion == LOCAL_EXACT_REUSE_KEY_VERSION)
        listOf(generationParametersHash, toolSchemaHash, contextManifestHash, messageTreeHash, attachmentHash, canonicalRequestHash).forEach { require(it.isSha256()) }
    }
}

data class LocalExactReuseEntry(val key: LocalExactReuseKey, val responseMessageId: String, val createdAtEpochMs: Long, val expiresAtEpochMs: Long, val revoked: Boolean = false) {
    init { require(responseMessageId.isStableReuseId()); require(createdAtEpochMs >= 0 && expiresAtEpochMs > createdAtEpochMs) }
}

data class LocalExactReuseDecision(val outcome: LocalExactReuseOutcome, val responseMessageId: String? = null, val reason: String)

/** Persistence may retain only this content-free exact-key fact. It never dispatches a request. */
interface LocalExactReuseEntryStore {
    fun record(entry: LocalExactReuseEntry): Boolean
    fun resolve(key: LocalExactReuseKey?, isTemporaryConversation: Boolean, nowEpochMs: Long): LocalExactReuseDecision
    fun revoke(canonicalRequestHash: String): Boolean
    fun purgeExpiredOrRevoked(nowEpochMs: Long): Int
}

/** A fail-closed owner. Future execution must reuse the normal message renderer, never raw cache text. */
class LocalExactReuseIndex : LocalExactReuseEntryStore {
    private val entries = linkedMapOf<String, LocalExactReuseEntry>()

    override fun record(entry: LocalExactReuseEntry): Boolean {
        if (entry.key.sensitivity == LocalExactReuseSensitivity.HIGH || entry.revoked) return false
        val old = entries[entry.key.canonicalRequestHash]
        if (old != null && old != entry) return false
        entries[entry.key.canonicalRequestHash] = entry
        return true
    }

    /** Restores a durable content-free row; callers must still resolve through this owner. */
    fun restore(entry: LocalExactReuseEntry): Boolean {
        if (entry.key.sensitivity == LocalExactReuseSensitivity.HIGH) return false
        val old = entries[entry.key.canonicalRequestHash]
        if (old != null && old != entry) return false
        entries[entry.key.canonicalRequestHash] = entry
        return true
    }

    override fun resolve(key: LocalExactReuseKey?, isTemporaryConversation: Boolean, nowEpochMs: Long): LocalExactReuseDecision {
        if (key == null) return LocalExactReuseDecision(LocalExactReuseOutcome.UNKNOWN, reason = "缺少完整精确复用键")
        if (isTemporaryConversation) return LocalExactReuseDecision(LocalExactReuseOutcome.INELIGIBLE, reason = "临时会话不建立本地精确复用")
        if (key.sensitivity == LocalExactReuseSensitivity.HIGH) return LocalExactReuseDecision(LocalExactReuseOutcome.INELIGIBLE, reason = "高敏感请求不建立本地精确复用")
        val entry = entries[key.canonicalRequestHash] ?: return LocalExactReuseDecision(LocalExactReuseOutcome.MISS, reason = "没有完全一致的本地结果")
        if (entry.key != key) return LocalExactReuseDecision(LocalExactReuseOutcome.MISS, reason = "精确键字段不一致")
        if (entry.revoked || nowEpochMs >= entry.expiresAtEpochMs) return LocalExactReuseDecision(LocalExactReuseOutcome.MISS, reason = "本地结果已撤销或过期")
        return LocalExactReuseDecision(LocalExactReuseOutcome.LOCAL_EXACT_HIT, entry.responseMessageId, "复用既有本地消息；不会请求 Provider")
    }

    override fun revoke(canonicalRequestHash: String): Boolean {
        if (!canonicalRequestHash.isSha256()) return false
        val entry = entries[canonicalRequestHash] ?: return false
        if (entry.revoked) return true
        entries[canonicalRequestHash] = entry.copy(revoked = true)
        return true
    }

    override fun purgeExpiredOrRevoked(nowEpochMs: Long): Int {
        val keys = entries.filterValues { it.revoked || nowEpochMs >= it.expiresAtEpochMs }.keys
        keys.forEach(entries::remove)
        return keys.size
    }
}

private fun String.isStableReuseId() = matches(Regex("[A-Za-z0-9._:-]{1,160}"))
private fun String.isSha256() = matches(Regex("[0-9a-f]{64}"))
