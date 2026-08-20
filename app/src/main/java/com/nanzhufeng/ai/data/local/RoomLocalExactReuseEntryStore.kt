package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.LocalExactReuseDecision
import com.nanzhufeng.ai.domain.LocalExactReuseEntry
import com.nanzhufeng.ai.domain.LocalExactReuseEntryStore
import com.nanzhufeng.ai.domain.LocalExactReuseIndex
import com.nanzhufeng.ai.domain.LocalExactReuseKey
import com.nanzhufeng.ai.domain.LocalExactReuseOutcome
import com.nanzhufeng.ai.domain.LocalExactReuseSensitivity
import java.util.concurrent.Callable

/** P6-L2 durable owner: no prompt, response body, credential, URI or provider event reaches Room. */
class RoomLocalExactReuseEntryStore(private val database: NanfengAiDatabase) : LocalExactReuseEntryStore {
    override fun record(entry: LocalExactReuseEntry): Boolean = database.runInTransaction(Callable {
        val dao = database.localExactReuseEntryDao()
        val existing = dao.find(entry.key.canonicalRequestHash)
        when {
            existing == null && entry.key.sensitivity != LocalExactReuseSensitivity.HIGH && !entry.revoked -> {
                dao.insert(entry.toEntity()); true
            }
            existing != null -> runCatching { existing.toDomain() }.getOrNull() == entry
            else -> false
        }
    })

    override fun resolve(key: LocalExactReuseKey?, isTemporaryConversation: Boolean, nowEpochMs: Long): LocalExactReuseDecision {
        if (key == null) return LocalExactReuseIndex().resolve(null, isTemporaryConversation, nowEpochMs)
        val entry = database.localExactReuseEntryDao().find(key.canonicalRequestHash) ?: return LocalExactReuseIndex().resolve(key, isTemporaryConversation, nowEpochMs)
        val index = LocalExactReuseIndex()
        if (!runCatching { index.restore(entry.toDomain()) }.getOrDefault(false)) {
            return LocalExactReuseDecision(LocalExactReuseOutcome.UNKNOWN, reason = "本地精确复用记录无效")
        }
        return index.resolve(key, isTemporaryConversation, nowEpochMs)
    }

    override fun revoke(canonicalRequestHash: String): Boolean = database.localExactReuseEntryDao().revoke(canonicalRequestHash) > 0
    override fun purgeExpiredOrRevoked(nowEpochMs: Long): Int = database.localExactReuseEntryDao().purge(nowEpochMs)
}

private fun LocalExactReuseEntry.toEntity() = LocalExactReuseEntryEntity(
    canonicalRequestHash = key.canonicalRequestHash, scopeId = key.scopeId, providerId = key.providerId,
    modelSnapshotId = key.modelSnapshotId, endpointMode = key.endpointMode,
    generationParametersHash = key.generationParametersHash, toolSchemaHash = key.toolSchemaHash,
    contextManifestHash = key.contextManifestHash, messageTreeHash = key.messageTreeHash,
    attachmentHash = key.attachmentHash, templateVersion = key.templateVersion, policyVersion = key.policyVersion,
    sensitivity = key.sensitivity.name, keyVersion = key.keyVersion, responseMessageId = responseMessageId,
    createdAtEpochMs = createdAtEpochMs, expiresAtEpochMs = expiresAtEpochMs, revoked = revoked,
)

private fun LocalExactReuseEntryEntity.toDomain() = LocalExactReuseEntry(
    key = LocalExactReuseKey(
        scopeId = scopeId, providerId = providerId, modelSnapshotId = modelSnapshotId, endpointMode = endpointMode,
        generationParametersHash = generationParametersHash, toolSchemaHash = toolSchemaHash,
        contextManifestHash = contextManifestHash, messageTreeHash = messageTreeHash, attachmentHash = attachmentHash,
        templateVersion = templateVersion, policyVersion = policyVersion, canonicalRequestHash = canonicalRequestHash,
        sensitivity = LocalExactReuseSensitivity.valueOf(sensitivity), keyVersion = keyVersion,
    ),
    responseMessageId = responseMessageId, createdAtEpochMs = createdAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs, revoked = revoked,
)
