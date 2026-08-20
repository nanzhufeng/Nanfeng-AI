package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.P6KZipAssetCandidate
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkOwner
import com.nanzhufeng.ai.domain.P6KZipManualAssetLinkResult
import com.nanzhufeng.ai.domain.P6KZipManualLinkTarget
import com.nanzhufeng.ai.domain.P6KZipImportTask
import com.nanzhufeng.ai.domain.P6KZipAssetRole
import com.nanzhufeng.ai.domain.toConversationReference
import java.time.Instant
import java.util.concurrent.Callable

/** K8's sole Android receipt owner.  The archive reader supplies one already verified private
 * attachment; this class never receives a path, URI, file name, or ZIP bytes. */
class RoomP6KZipManualAssetLinkOwner(
    private val database: NanfengAiDatabase,
    private val conversations: RoomConversationRepository,
) : P6KZipManualAssetLinkOwner {
    override fun link(task: P6KZipImportTask, asset: P6KZipAssetCandidate, target: P6KZipManualLinkTarget, attachment: AttachmentReference, at: Instant): P6KZipManualAssetLinkResult = runCatching {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao()
            val stored = dao.asset(task.id.value, asset.entryName) ?: return@Callable P6KZipManualAssetLinkResult.Failed
            if (stored.sha256 != asset.sha256 || stored.byteCount != asset.byteCount || stored.mimeType != asset.mimeType) return@Callable P6KZipManualAssetLinkResult.Failed
            val prior = dao.assetLinkReceipt(task.id.value, asset.entryName)
            if (prior != null) return@Callable if (prior.sha256 == attachment.sha256 && prior.conversationId == target.conversationId.value && prior.messageId == target.messageId.value) P6KZipManualAssetLinkResult.Replayed else P6KZipManualAssetLinkResult.Conflict
            if (dao.provenanceForTask(task.id.value).none { it.conversationId == target.conversationId.value }) return@Callable P6KZipManualAssetLinkResult.Failed
            if (attachment.sha256 != stored.sha256 || attachment.byteCount != stored.byteCount || attachment.mimeType != stored.mimeType) return@Callable P6KZipManualAssetLinkResult.Failed
            val attachments = database.privateAttachmentAssetDao(); val existing = attachments.findBySha256(stored.sha256)
            val effective = existing?.toP6KAttachmentReference() ?: attachment
            when (conversations.attachExistingMessageInTransaction(target.conversationId, target.messageId, effective.toConversationReference(), at)) {
                ExistingMessageAttachmentResult.Failed -> return@Callable P6KZipManualAssetLinkResult.Failed
                ExistingMessageAttachmentResult.Linked, ExistingMessageAttachmentResult.Replayed -> Unit
            }
            if (existing == null) attachments.insert(PrivateAttachmentAssetEntity(effective.id.value, effective.reference, effective.mimeType, effective.displayName, requireNotNull(effective.byteCount), requireNotNull(effective.sha256)))
            dao.upsertAssets(listOf(stored.copy(role = P6KZipAssetRole.MANUAL_LINKED.name, linkedConversationId = target.conversationId.value, linkedMessageId = target.messageId.value, attachmentId = effective.id.value, failure = null)))
            dao.insertAssetLinkReceipt(P6KZipAssetLinkReceiptEntity(task.id.value, asset.entryName, stored.sha256, effective.id.value, target.conversationId.value, target.messageId.value, at.toEpochMilli()))
            dao.insertAssetLinkProvenance(P6KZipAssetLinkProvenanceEntity(effective.id.value, task.id.value, asset.entryName, stored.sha256, target.conversationId.value, target.messageId.value, at.toEpochMilli()))
            P6KZipManualAssetLinkResult.Linked
        })
    }.getOrElse { markFailed(task, asset, at); P6KZipManualAssetLinkResult.Failed }

    override fun revokeBatch(task: P6KZipImportTask, at: Instant): Boolean = runCatching {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao(); dao.deleteAssetLinkReceiptsForTask(task.id.value); dao.deleteAssetLinkProvenanceForTask(task.id.value)
            dao.assets(task.id.value).filter { it.role == P6KZipAssetRole.MANUAL_LINKED.name }.forEach { asset -> dao.upsertAssets(listOf(asset.copy(role = P6KZipAssetRole.UNMAPPED_REJECTED.name, linkedConversationId = null, linkedMessageId = null, attachmentId = null, failure = null))) }
            true
        })
    }.getOrDefault(false)

    private fun markFailed(task: P6KZipImportTask, asset: P6KZipAssetCandidate, at: Instant) = runCatching {
        database.runInTransaction(Callable {
            val dao = database.p6kZipImportTaskDao(); val stored = dao.asset(task.id.value, asset.entryName) ?: return@Callable
            if (stored.role != P6KZipAssetRole.MANUAL_LINKED.name) dao.upsertAssets(listOf(stored.copy(role = P6KZipAssetRole.MANUAL_LINK_FAILED.name, failure = "MANUAL_LINK_FAILED")))
        })
    }
}

private fun PrivateAttachmentAssetEntity.toP6KAttachmentReference() = AttachmentReference(reference = storageKey, mimeType = mimeType, displayName = displayName, id = com.nanzhufeng.ai.domain.AttachmentId(attachmentId), byteCount = byteCount, sha256 = sha256)
