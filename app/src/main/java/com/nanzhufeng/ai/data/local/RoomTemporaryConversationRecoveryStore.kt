package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.AttachmentId
import com.nanzhufeng.ai.domain.TemporaryAttachmentScope
import com.nanzhufeng.ai.domain.TemporaryConversationId
import com.nanzhufeng.ai.domain.TemporaryConversationRecovery
import com.nanzhufeng.ai.domain.TemporaryConversationRecoveryStore
import com.nanzhufeng.ai.domain.TemporaryOfflineMessage
import java.time.Instant
import java.util.concurrent.Callable

/** Room adapter for the P6-E owner. It cannot create or read a normal Conversation. */
class RoomTemporaryConversationRecoveryStore(private val database: NanfengAiDatabase) : TemporaryConversationRecoveryStore {
    override fun readActive(): TemporaryConversationRecovery? = database.runInTransaction(Callable {
        database.temporaryConversationRecoveryDao().active()?.toDomain(database.temporaryConversationRecoveryDao())
    })

    override fun save(record: TemporaryConversationRecovery): TemporaryConversationRecovery = database.runInTransaction(Callable {
        val dao = database.temporaryConversationRecoveryDao()
        dao.upsert(TemporaryConversationRecoveryEntity(record.id.value, record.createdAt.toEpochMilli(), record.updatedAt.toEpochMilli(), record.draftText, record.modelOverrideId, record.schemaVersion))
        dao.deleteMessages(record.id.value)
        dao.deleteAttachments(record.id.value)
        dao.insertMessages(record.messages.mapIndexed { position, message ->
            TemporaryConversationMessageEntity(message.id, record.id.value, position, message.text, message.createdAt.toEpochMilli())
        })
        val attachments = buildList {
            record.draftAttachmentIds.forEachIndexed { position, id -> add(TemporaryConversationAttachmentEntity(record.id.value, "DRAFT", "draft", position, id.value, TemporaryAttachmentScope.TEMPORARY_SESSION.name)) }
            record.messages.forEach { message -> message.attachmentIds.forEachIndexed { position, id -> add(TemporaryConversationAttachmentEntity(record.id.value, "MESSAGE", message.id, position, id.value, TemporaryAttachmentScope.TEMPORARY_SESSION.name)) } }
        }
        dao.insertAttachments(attachments)
        dao.active()?.toDomain(dao) ?: error("temporary recovery write missing")
    })

    override fun delete(id: TemporaryConversationId): List<AttachmentId> = database.runInTransaction(Callable {
        val dao = database.temporaryConversationRecoveryDao()
        val referenced = dao.attachments(id.value).map { AttachmentId(it.attachmentId) }.distinct()
        dao.deleteRecord(id.value)
        referenced
    })
}

private fun TemporaryConversationRecoveryEntity.toDomain(dao: TemporaryConversationRecoveryDao): TemporaryConversationRecovery {
    require(schemaVersion == 1)
    val attachments = dao.attachments(temporaryId)
    require(attachments.all { it.scope == TemporaryAttachmentScope.TEMPORARY_SESSION.name && it.ownerKind in setOf("DRAFT", "MESSAGE") })
    val messages = dao.messages(temporaryId).map { message ->
        TemporaryOfflineMessage(message.messageId, message.text, attachments.filter { it.ownerKind == "MESSAGE" && it.ownerId == message.messageId }.map { AttachmentId(it.attachmentId) }, Instant.ofEpochMilli(message.createdAtEpochMs))
    }
    return TemporaryConversationRecovery(
        TemporaryConversationId(temporaryId), Instant.ofEpochMilli(createdAtEpochMs), Instant.ofEpochMilli(updatedAtEpochMs), draftText,
        attachments.filter { it.ownerKind == "DRAFT" && it.ownerId == "draft" }.map { AttachmentId(it.attachmentId) }, messages, modelOverrideId, schemaVersion,
    )
}
