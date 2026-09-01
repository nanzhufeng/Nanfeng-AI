package com.nanzhufeng.ai.data.local

import android.content.Context
import android.util.Base64
import com.nanzhufeng.ai.domain.ChatGptImportCandidate
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class P6KImportIdentityState { ACTIVE, USER_DELETED, CONFLICT }
enum class P6KImportIdentityObjectType { CONVERSATION, MESSAGE, SOURCE_ASSET, ASSET_OCCURRENCE }
enum class P6KImportIdentityQuality { OFFICIAL_STABLE_ID, STRONG_SOURCE_PATH_FINGERPRINT }

interface P6KImportIdentityEncoder {
    fun encode(value: String): String
}

/** Production encoder: the key is random, app-private and never exported with the ledger. */
class AndroidP6KImportIdentityEncoder(context: Context) : P6KImportIdentityEncoder {
    private val key: ByteArray = context.getSharedPreferences("p6k_import_identity", Context.MODE_PRIVATE).let { preferences ->
        preferences.getString("hmac_key_v1", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: ByteArray(32).also { bytes ->
                SecureRandom().nextBytes(bytes)
                check(preferences.edit().putString("hmac_key_v1", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
            }
    }

    override fun encode(value: String): String = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(Charsets.UTF_8)).toHex()
    }
}

/** Stable only for local JVM tests and exact-evidence migration fixtures. */
object DeterministicP6KImportIdentityEncoder : P6KImportIdentityEncoder {
    override fun encode(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest("nanfeng-p6k-test-v1\u0000$value".toByteArray(Charsets.UTF_8)).toHex()
}

sealed interface P6KConversationIdentityDecision {
    data object New : P6KConversationIdentityDecision
    data class Existing(val conversationId: ConversationId) : P6KConversationIdentityDecision
    data object UserDeleted : P6KConversationIdentityDecision
    data object Conflict : P6KConversationIdentityDecision
}

sealed interface P6KOccurrenceIdentityDecision {
    data object New : P6KOccurrenceIdentityDecision
    data object Existing : P6KOccurrenceIdentityDecision
    data object UserDeleted : P6KOccurrenceIdentityDecision
    data object Conflict : P6KOccurrenceIdentityDecision
}

sealed interface P6KAssetIdentityDecision {
    data object New : P6KAssetIdentityDecision
    data object Existing : P6KAssetIdentityDecision
    data object Conflict : P6KAssetIdentityDecision
}

/**
 * The only owner of permanent import identities. Callers already run inside Room transactions;
 * every visible Conversation/Message/attachment change is committed with its identity fact.
 */
class RoomP6KImportIdentityLedger(
    private val database: NanfengAiDatabase,
    private val encoder: P6KImportIdentityEncoder = DeterministicP6KImportIdentityEncoder,
) {
    private val dao get() = database.p6kImportIdentityLedgerDao()
    private val backfillInProgress = ThreadLocal.withInitial { false }

    fun conversationDecision(task: P6KZipImportTask, candidate: ChatGptImportCandidate): P6KConversationIdentityDecision {
        ensureLegacyEvidenceBackfilled(Instant.now())
        val key = conversationKey(task, candidate.sourceConversationId)
        val row = dao.find(task.provider.name, task.schemaVersion(), P6KImportIdentityObjectType.CONVERSATION.name, key)
            ?: return P6KConversationIdentityDecision.New
        if (row.state == P6KImportIdentityState.USER_DELETED.name) return P6KConversationIdentityDecision.UserDeleted
        if (row.state != P6KImportIdentityState.ACTIVE.name || row.localConversationId == null) {
            return P6KConversationIdentityDecision.Conflict
        }
        return P6KConversationIdentityDecision.Existing(ConversationId(row.localConversationId))
    }

    fun registerConversation(
        task: P6KZipImportTask,
        candidate: ChatGptImportCandidate,
        conversationId: ConversationId,
        sourceMessageIds: Map<String, MessageNodeId>,
        at: Instant,
    ) {
        ensureLegacyEvidenceBackfilled(at)
        val root = conversationKey(task, candidate.sourceConversationId)
        upsertExact(
            task, P6KImportIdentityObjectType.CONVERSATION, root, root, null,
            candidate.contentHash, null, null, conversationId.value, null, null,
            P6KImportIdentityQuality.OFFICIAL_STABLE_ID, at,
        )
        candidate.messages.forEach { message ->
            val local = sourceMessageIds[message.sourceId] ?: return@forEach
            val key = messageKey(task, candidate.sourceConversationId, message.sourceId)
            upsertExact(
                task, P6KImportIdentityObjectType.MESSAGE, key, root, root,
                message.semanticHash(), null, null, conversationId.value, local.value, null,
                P6KImportIdentityQuality.OFFICIAL_STABLE_ID, at,
            )
        }
    }

    fun occurrenceDecision(
        task: P6KZipImportTask,
        sourceConversationId: String,
        sourceMessageId: String,
        sourceAssetId: String,
        sha256: String,
        byteCount: Long,
    ): P6KOccurrenceIdentityDecision {
        ensureLegacyEvidenceBackfilled(Instant.now())
        val root = conversationKey(task, sourceConversationId)
        val conversation = dao.find(task.provider.name, task.schemaVersion(), P6KImportIdentityObjectType.CONVERSATION.name, root)
        if (conversation?.state == P6KImportIdentityState.USER_DELETED.name) return P6KOccurrenceIdentityDecision.UserDeleted
        val assetKey = assetKey(task, sourceAssetId)
        dao.find(task.provider.name, task.schemaVersion(), P6KImportIdentityObjectType.SOURCE_ASSET.name, assetKey)?.let { row ->
            if (row.state == P6KImportIdentityState.USER_DELETED.name) return P6KOccurrenceIdentityDecision.UserDeleted
            if (row.state != P6KImportIdentityState.ACTIVE.name || row.assetSha256 != sha256 || row.assetByteCount != byteCount) {
                return P6KOccurrenceIdentityDecision.Conflict
            }
        }
        val key = occurrenceKey(task, sourceConversationId, sourceMessageId, sourceAssetId)
        val occurrence = dao.find(task.provider.name, task.schemaVersion(), P6KImportIdentityObjectType.ASSET_OCCURRENCE.name, key)
            ?: return P6KOccurrenceIdentityDecision.New
        return when (occurrence.state) {
            P6KImportIdentityState.USER_DELETED.name -> P6KOccurrenceIdentityDecision.UserDeleted
            P6KImportIdentityState.ACTIVE.name -> P6KOccurrenceIdentityDecision.Existing
            else -> P6KOccurrenceIdentityDecision.Conflict
        }
    }

    fun sourceAssetDecision(task: P6KZipImportTask, sourceAssetId: String, sha256: String, byteCount: Long): P6KAssetIdentityDecision {
        ensureLegacyEvidenceBackfilled(Instant.now())
        val key = assetKey(task, sourceAssetId)
        val row = dao.find(task.provider.name, task.schemaVersion(), P6KImportIdentityObjectType.SOURCE_ASSET.name, key)
            ?: return P6KAssetIdentityDecision.New
        return if (row.state == P6KImportIdentityState.ACTIVE.name && row.assetSha256 == sha256 && row.assetByteCount == byteCount) {
            P6KAssetIdentityDecision.Existing
        } else {
            P6KAssetIdentityDecision.Conflict
        }
    }

    fun registerOccurrence(
        task: P6KZipImportTask,
        sourceConversationId: String,
        sourceMessageId: String,
        sourceAssetId: String,
        sha256: String,
        byteCount: Long,
        conversationId: ConversationId,
        messageId: MessageNodeId,
        attachmentId: String,
        at: Instant,
    ) {
        ensureLegacyEvidenceBackfilled(at)
        val root = conversationKey(task, sourceConversationId)
        val sourceAssetKey = assetKey(task, sourceAssetId)
        upsertExact(
            task, P6KImportIdentityObjectType.SOURCE_ASSET, sourceAssetKey, sourceAssetKey, null,
            null, sha256, byteCount, null, null, attachmentId,
            P6KImportIdentityQuality.STRONG_SOURCE_PATH_FINGERPRINT, at,
        )
        upsertExact(
            task, P6KImportIdentityObjectType.ASSET_OCCURRENCE,
            occurrenceKey(task, sourceConversationId, sourceMessageId, sourceAssetId), root,
            messageKey(task, sourceConversationId, sourceMessageId), null, sha256, byteCount,
            conversationId.value, messageId.value, attachmentId,
            P6KImportIdentityQuality.STRONG_SOURCE_PATH_FINGERPRINT, at,
        )
    }

    fun tombstoneConversation(conversationId: String, reason: String, at: Instant) {
        ensureLegacyEvidenceBackfilled(at)
        dao.forConversation(conversationId).forEach { row ->
            if (row.state != P6KImportIdentityState.USER_DELETED.name) {
                check(dao.update(row.copy(
                    state = P6KImportIdentityState.USER_DELETED.name,
                    deletionReason = reason,
                    lastSeenAtEpochMs = at.toEpochMilli(),
                    revision = row.revision + 1,
                )) == 1)
            }
        }
    }

    fun tombstoneOccurrence(conversationId: String, messageId: String, attachmentId: String, reason: String, at: Instant) {
        ensureLegacyEvidenceBackfilled(at)
        dao.forOccurrence(conversationId, messageId, attachmentId).forEach { row ->
            if (row.state != P6KImportIdentityState.USER_DELETED.name) {
                check(dao.update(row.copy(
                    state = P6KImportIdentityState.USER_DELETED.name,
                    deletionReason = reason,
                    lastSeenAtEpochMs = at.toEpochMilli(),
                    revision = row.revision + 1,
                )) == 1)
            }
        }
    }

    /** Backfills only exact extant provenance. Already-purged historical imports remain a declared gap. */
    fun backfillExactLegacyEvidence(at: Instant) {
        if (dao.migrationState() != null) return
        fun work() {
            if (dao.migrationState() != null) return
            backfillInProgress.set(true)
            try {
            var conversations = 0
            var messages = 0
            var occurrences = 0
            val zipDao = database.p6kZipImportTaskDao()
            zipDao.allProvenance().forEach { provenance ->
                val taskRow = zipDao.task(provenance.taskId) ?: return@forEach
                val provider = runCatching { ThirdPartyZipProvider.valueOf(taskRow.provider) }.getOrNull() ?: return@forEach
                val task = P6KZipImportTask(
                    id = com.nanzhufeng.ai.domain.P6KZipTaskId(provenance.taskId), provider = provider,
                    displayName = "", byteCount = 0, packageHash = provenance.packageHash,
                    status = com.nanzhufeng.ai.domain.P6KZipTaskStatus.COMPLETED,
                    formatVersion = taskRow.formatVersion, createdAt = at, updatedAt = at,
                )
                val root = conversationKey(task, provenance.sourceConversationId)
                if (dao.find(provider.name, task.schemaVersion(), P6KImportIdentityObjectType.CONVERSATION.name, root) == null) {
                    dao.insert(baseEntity(
                        task, P6KImportIdentityObjectType.CONVERSATION, root, root, null,
                        provenance.contentHash, null, null, provenance.conversationId, null, null,
                        P6KImportIdentityQuality.OFFICIAL_STABLE_ID, at,
                    ))
                    conversations += 1
                }
                zipDao.messageProvenanceForConversation(provenance.conversationId).forEach { message ->
                    val key = messageKey(task, provenance.sourceConversationId, message.sourceMessageId)
                    if (dao.find(provider.name, task.schemaVersion(), P6KImportIdentityObjectType.MESSAGE.name, key) == null) {
                        dao.insert(baseEntity(
                            task, P6KImportIdentityObjectType.MESSAGE, key, root, root,
                            message.contentHash, null, null, provenance.conversationId, message.messageId, null,
                            P6KImportIdentityQuality.OFFICIAL_STABLE_ID, at,
                        ))
                        messages += 1
                    }
                }
            }
            zipDao.allAssetOccurrenceReceipts().forEach { receipt ->
                val taskRow = zipDao.task(receipt.taskId) ?: return@forEach
                val provider = runCatching { ThirdPartyZipProvider.valueOf(taskRow.provider) }.getOrNull() ?: return@forEach
                val asset = zipDao.asset(receipt.taskId, receipt.entryName) ?: return@forEach
                if (asset.sha256.length != 64 || asset.byteCount < 0) return@forEach
                val task = P6KZipImportTask(
                    id = com.nanzhufeng.ai.domain.P6KZipTaskId(receipt.taskId), provider = provider,
                    displayName = "", byteCount = 0, packageHash = taskRow.packageHash,
                    status = com.nanzhufeng.ai.domain.P6KZipTaskStatus.COMPLETED,
                    formatVersion = taskRow.formatVersion, createdAt = at, updatedAt = at,
                )
                val decision = occurrenceDecision(
                    task, receipt.sourceConversationId, receipt.sourceMessageId, receipt.entryName,
                    asset.sha256, asset.byteCount,
                )
                if (decision == P6KOccurrenceIdentityDecision.New) {
                    registerOccurrence(
                        task, receipt.sourceConversationId, receipt.sourceMessageId, receipt.entryName,
                        asset.sha256, asset.byteCount, ConversationId(receipt.conversationId),
                        MessageNodeId(receipt.messageId), receipt.attachmentId, at,
                    )
                    occurrences += 1
                }
            }
            dao.insertMigrationState(P6KImportIdentityMigrationStateEntity(
                completedAtEpochMs = at.toEpochMilli(),
                backfilledConversations = conversations,
                backfilledMessages = messages,
                backfilledOccurrences = occurrences,
                legacyCoverageGap = true,
            ))
            } finally {
                backfillInProgress.set(false)
            }
        }
        if (database.inTransaction()) {
            work()
        } else {
            database.runInTransaction { work() }
        }
    }

    private fun ensureLegacyEvidenceBackfilled(at: Instant) {
        if (backfillInProgress.get() != true && dao.migrationState() == null) backfillExactLegacyEvidence(at)
    }

    fun batchReceipt(taskId: String): P6KImportBatchReceiptEntity? = dao.batchReceipt(taskId)

    /** UNKNOWN requires a fresh user-selected ZIP; it is never auto-replayed after process loss. */
    fun markInterruptedAsUnknown(at: Instant): Int = dao.markInterruptedAsUnknown(at.toEpochMilli())

    fun mutateBatchReceipt(task: P6KZipImportTask, at: Instant, change: (P6KImportBatchReceiptEntity) -> P6KImportBatchReceiptEntity) {
        val current = dao.batchReceipt(task.id.value) ?: P6KImportBatchReceiptEntity(
            taskId = task.id.value, provider = task.provider.name, schemaVersion = task.schemaVersion(),
            packageHash = task.packageHash, status = "IN_PROGRESS", importedNewConversations = 0,
            importedNewMessages = 0, importedNewAttachments = 0, reusedAssetBytes = 0,
            skippedExisting = 0, skippedUserDeleted = 0, identityConflicts = 0, failed = 0,
            startedAtEpochMs = at.toEpochMilli(), updatedAtEpochMs = at.toEpochMilli(), completedAtEpochMs = null,
        )
        val changed = change(current)
        dao.upsertBatchReceipt(changed.copy(
            status = if (changed.failed > 0 || changed.identityConflicts > 0) "FAILED" else changed.status,
            updatedAtEpochMs = at.toEpochMilli(),
            completedAtEpochMs = changed.completedAtEpochMs.takeIf { changed.failed == 0 && changed.identityConflicts == 0 },
        ))
    }

    fun finishBatch(task: P6KZipImportTask, statuses: List<String>, at: Instant) {
        mutateBatchReceipt(task, at) { receipt ->
            val terminal = statuses.all { it in setOf("CONFIRMED", "SKIPPED", "FAILED") }
            val clean = terminal && task.assets.isEmpty() && receipt.failed == 0 && receipt.identityConflicts == 0
            receipt.copy(
                status = when {
                    clean -> "COMPLETED"
                    receipt.failed > 0 || receipt.identityConflicts > 0 -> "FAILED"
                    else -> "IN_PROGRESS"
                },
                completedAtEpochMs = at.toEpochMilli().takeIf { clean },
            )
        }
    }

    fun finishAssetRecovery(task: P6KZipImportTask, successful: Boolean, at: Instant) {
        mutateBatchReceipt(task, at) { receipt ->
            val clean = successful && receipt.failed == 0 && receipt.identityConflicts == 0
            receipt.copy(
                status = if (clean) "COMPLETED" else "FAILED",
                completedAtEpochMs = at.toEpochMilli().takeIf { clean },
                failed = receipt.failed + if (successful) 0 else 1,
            )
        }
    }

    private fun upsertExact(
        task: P6KZipImportTask,
        type: P6KImportIdentityObjectType,
        key: String,
        root: String,
        parent: String?,
        contentHash: String?,
        assetHash: String?,
        assetBytes: Long?,
        conversationId: String?,
        messageId: String?,
        attachmentId: String?,
        quality: P6KImportIdentityQuality,
        at: Instant,
    ) {
        val previous = dao.find(task.provider.name, task.schemaVersion(), type.name, key)
        if (previous == null) {
            dao.insert(baseEntity(task, type, key, root, parent, contentHash, assetHash, assetBytes, conversationId, messageId, attachmentId, quality, at))
            return
        }
        check(previous.state != P6KImportIdentityState.USER_DELETED.name) { "P6K_IDENTITY_USER_DELETED" }
        val contentMatches = type == P6KImportIdentityObjectType.CONVERSATION || previous.contentSha256 == contentHash
        check(contentMatches && previous.assetSha256 == assetHash && previous.assetByteCount == assetBytes) { "P6K_IDENTITY_CONFLICT" }
        check(dao.update(previous.copy(
            contentSha256 = contentHash ?: previous.contentSha256,
            lastBatchHash = task.packageHash,
            localConversationId = conversationId ?: previous.localConversationId,
            localMessageId = messageId ?: previous.localMessageId,
            localAttachmentId = attachmentId ?: previous.localAttachmentId,
            lastSeenAtEpochMs = at.toEpochMilli(),
            revision = previous.revision + 1,
        )) == 1)
    }

    private fun baseEntity(
        task: P6KZipImportTask,
        type: P6KImportIdentityObjectType,
        key: String,
        root: String,
        parent: String?,
        contentHash: String?,
        assetHash: String?,
        assetBytes: Long?,
        conversationId: String?,
        messageId: String?,
        attachmentId: String?,
        quality: P6KImportIdentityQuality,
        at: Instant,
    ) = P6KImportIdentityLedgerEntity(
        provider = task.provider.name, schemaVersion = task.schemaVersion(), objectType = type.name,
        identityKey = key, rootConversationKey = root, parentIdentityKey = parent,
        contentSha256 = contentHash, assetSha256 = assetHash, assetByteCount = assetBytes,
        localConversationId = conversationId, localMessageId = messageId, localAttachmentId = attachmentId,
        firstBatchHash = task.packageHash, lastBatchHash = task.packageHash, identityQuality = quality.name,
        state = P6KImportIdentityState.ACTIVE.name, deletionReason = null,
        firstSeenAtEpochMs = at.toEpochMilli(), lastSeenAtEpochMs = at.toEpochMilli(), revision = 1,
    )

    private fun conversationKey(task: P6KZipImportTask, sourceConversationId: String) =
        encoder.encode("${task.provider.name}\u0000${task.schemaVersion()}\u0000conversation\u0000$sourceConversationId")

    private fun messageKey(task: P6KZipImportTask, sourceConversationId: String, sourceMessageId: String) =
        encoder.encode("${task.provider.name}\u0000${task.schemaVersion()}\u0000message\u0000$sourceConversationId\u0000$sourceMessageId")

    private fun assetKey(task: P6KZipImportTask, sourceAssetId: String) =
        encoder.encode("${task.provider.name}\u0000${task.schemaVersion()}\u0000asset\u0000$sourceAssetId")

    private fun occurrenceKey(task: P6KZipImportTask, sourceConversationId: String, sourceMessageId: String, sourceAssetId: String) =
        encoder.encode("${task.provider.name}\u0000${task.schemaVersion()}\u0000occurrence\u0000$sourceConversationId\u0000$sourceMessageId\u0000$sourceAssetId")
}

fun P6KZipImportTask.schemaVersion(): String = formatVersion ?: "UNKNOWN"

private fun com.nanzhufeng.ai.domain.ChatGptImportMessage.semanticHash(): String =
    MessageDigest.getInstance("SHA-256").digest(
        listOf(sourceId, parentSourceId.orEmpty(), siblingPosition.toString(), role.name, text, createdAt.toString())
            .joinToString("\u0000").toByteArray(Charsets.UTF_8),
    ).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
