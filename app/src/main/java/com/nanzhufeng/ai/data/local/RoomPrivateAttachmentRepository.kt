package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.AttachmentReference
import com.nanzhufeng.ai.domain.PrivateAttachmentRepository
import com.nanzhufeng.ai.domain.PrivateAttachmentCleanupResult
import com.nanzhufeng.ai.domain.isReadyPrivateCopy

/** The only Room adapter allowed to reveal a private storage key to Attachment Domain. */
class RoomPrivateAttachmentRepository(private val database: NanfengAiDatabase) : PrivateAttachmentRepository {
    override fun save(asset: AttachmentReference): AttachmentReference {
        var stored: AttachmentReference? = null
        database.runInTransaction {
        require(asset.isReadyPrivateCopy()) { "私有附件尚未准备完成。" }
        val dao = database.privateAttachmentAssetDao()
        val existingById = dao.findById(asset.id.value)
        if (existingById != null) { stored = existingById.toDomain(); return@runInTransaction }
        val existingByHash = dao.findBySha256(requireNotNull(asset.sha256))
        if (existingByHash != null) { stored = existingByHash.toDomain(); return@runInTransaction }
        dao.insert(PrivateAttachmentAssetEntity(
            attachmentId = asset.id.value,
            storageKey = asset.reference,
            mimeType = asset.mimeType,
            displayName = asset.displayName,
            byteCount = requireNotNull(asset.byteCount),
            sha256 = requireNotNull(asset.sha256),
        ))
        stored = dao.findById(asset.id.value)?.toDomain() ?: error("私有附件写入后无法回读。")
        }
        return requireNotNull(stored)
    }

    override fun findById(id: AttachmentId): AttachmentReference? =
        database.privateAttachmentAssetDao().findById(id.value)?.toDomain()

    override fun findBySha256(sha256: String): AttachmentReference? =
        database.privateAttachmentAssetDao().findBySha256(sha256)?.toDomain()

    override fun removeIfUnreferenced(id: AttachmentId): AttachmentReference? {
        var removed: AttachmentReference? = null
        database.runInTransaction {
            val dao = database.privateAttachmentAssetDao()
            val asset = dao.findById(id.value) ?: return@runInTransaction
            if (dao.referenceCount(id.value) > 0) return@runInTransaction
            dao.deleteById(id.value)
            removed = asset.toDomain()
        }
        return removed
    }

    override fun deleteIfUnreferenced(
        id: AttachmentId,
        deletePrivateCopy: (AttachmentReference) -> Boolean,
    ): PrivateAttachmentCleanupResult {
        var result: PrivateAttachmentCleanupResult = PrivateAttachmentCleanupResult.Missing
        database.runInTransaction {
            val dao = database.privateAttachmentAssetDao()
            val asset = dao.findById(id.value) ?: return@runInTransaction
            val references = dao.referenceCount(id.value)
            if (references > 0) {
                result = PrivateAttachmentCleanupResult.Retained(references)
                return@runInTransaction
            }
            if (!deletePrivateCopy(asset.toDomain())) {
                result = PrivateAttachmentCleanupResult.DeleteFailed
                return@runInTransaction
            }
            check(dao.deleteById(id.value) == 1) { "私有附件文件已清理，但目录行未能同步删除。" }
            result = PrivateAttachmentCleanupResult.Deleted
        }
        return result
    }
}

private fun PrivateAttachmentAssetDao.referenceCount(attachmentId: String): Int =
    normalDraftReferences(attachmentId) +
        captureDraftReferences(attachmentId) +
        knowledgeReferences(attachmentId) +
        normalMessageReferences(attachmentId) +
        zipOccurrenceReceiptReferences(attachmentId) +
        temporaryConversationReferences(attachmentId) +
        resumableUploadReferences(attachmentId) +
        glmOcrTaskReferences(attachmentId)

private fun PrivateAttachmentAssetEntity.toDomain() = AttachmentReference(
    reference = storageKey,
    mimeType = mimeType,
    displayName = displayName,
    id = AttachmentId(attachmentId),
    byteCount = byteCount,
    sha256 = sha256,
)
