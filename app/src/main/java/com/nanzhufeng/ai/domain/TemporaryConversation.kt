package com.nanzhufeng.ai.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** P6-E is deliberately not a Conversation. Its only durable owner is this recovery chain. */
@JvmInline value class TemporaryConversationId(val value: String) { companion object { fun new() = TemporaryConversationId(UUID.randomUUID().toString()) } }

enum class ConversationKind { NORMAL, TEMPORARY }
enum class TemporaryAttachmentScope { TEMPORARY_SESSION }

/**
 * A type-level boundary for the separate temporary recovery chain. Temporary drafts may be
 * retained locally for at most 24 hours, but they never become normal-conversation context or
 * automatic product data. Keep these declarations next to the only temporary owner so a future
 * send path cannot quietly inherit normal-chat behavior.
 */
object TemporaryConversationIsolation {
    const val memoryRetrievalEnabled = false
    const val knowledgeRetrievalEnabled = false
    const val normalHistorySearchEnabled = false
    const val automaticTitleEnabled = false
    const val automaticMemorySummaryEnabled = false
    const val providerEgressEnabled = false

    fun entryNotice(): String = "临时聊天仅在本机恢复，最多保留 24 小时；不会读取记忆或资料库、进入搜索、自动生成标题或记忆摘要，也不会连接模型服务。"
    fun sentNotice(): String = "已在本机临时聊天发送；未连接模型服务，且不会写入记忆、资料库、搜索或自动沉淀。"
}

data class TemporaryOfflineMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val attachmentIds: List<AttachmentId>,
    val createdAt: Instant,
) { init { require(text.isNotBlank() || attachmentIds.isNotEmpty()) } }

data class TemporaryConversationRecovery(
    val id: TemporaryConversationId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val draftText: String = "",
    val draftAttachmentIds: List<AttachmentId> = emptyList(),
    val messages: List<TemporaryOfflineMessage> = emptyList(),
    val modelOverrideId: String? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1)
        require(draftText.length <= MAX_TEXT && messages.size <= MAX_MESSAGES)
        require(draftAttachmentIds.size <= MAX_ATTACHMENTS)
        require(messages.all { it.text.length <= MAX_TEXT && it.attachmentIds.size <= MAX_ATTACHMENTS })
        require(modelOverrideId.isNullOrBlank() || modelOverrideId.matches(Regex("[A-Za-z0-9._:-]{1,160}")))
    }
    companion object { const val MAX_TEXT = 20_000; const val MAX_MESSAGES = 128; const val MAX_ATTACHMENTS = 4 }
}

interface TemporaryConversationRecoveryStore {
    fun readActive(): TemporaryConversationRecovery?
    fun save(record: TemporaryConversationRecovery): TemporaryConversationRecovery
    /** Deletes one recovery record and returns its referenced private attachment IDs. */
    fun delete(id: TemporaryConversationId): List<AttachmentId>
}

/** The one public P6-E write chain. It never reaches ConversationRepository, a normal ID, or the ordinary context/auto-persistence route. */
class TemporaryConversationDomain(
    private val recovery: TemporaryConversationRecoveryStore,
    private val clock: Clock,
) {
    fun enterOrRestore(): TemporaryConversationRecovery {
        pruneExpired()
        return recovery.readActive() ?: recovery.save(TemporaryConversationRecovery(TemporaryConversationId.new(), clock.instant(), clock.instant()))
    }

    fun readRecovery(): TemporaryConversationRecovery? { pruneExpired(); return recovery.readActive() }

    fun updateDraft(text: String, attachmentIds: List<AttachmentId>): TemporaryConversationRecovery {
        val current = enterOrRestore()
        require(text.length <= TemporaryConversationRecovery.MAX_TEXT && attachmentIds.distinct().size == attachmentIds.size && attachmentIds.size <= TemporaryConversationRecovery.MAX_ATTACHMENTS)
        return recovery.save(current.copy(draftText = text, draftAttachmentIds = attachmentIds, updatedAt = clock.instant()))
    }

    fun removeDraftAttachment(id: AttachmentId): TemporaryConversationRecovery {
        val current = enterOrRestore()
        return recovery.save(current.copy(
            draftAttachmentIds = current.draftAttachmentIds.filterNot { it == id },
            updatedAt = clock.instant(),
        ))
    }

    fun appendOfflineMessage(): TemporaryConversationRecovery {
        val current = enterOrRestore()
        require(current.draftText.isNotBlank() || current.draftAttachmentIds.isNotEmpty())
        return recovery.save(current.copy(
            messages = current.messages + TemporaryOfflineMessage(text = current.draftText, attachmentIds = current.draftAttachmentIds, createdAt = clock.instant()),
            draftText = "", draftAttachmentIds = emptyList(), updatedAt = clock.instant(),
        ))
    }

    fun updateModelOverride(modelId: String?): TemporaryConversationRecovery {
        val current = enterOrRestore()
        return recovery.save(current.copy(modelOverrideId = modelId, updatedAt = clock.instant()))
    }

    fun exitOrClear(): List<AttachmentId> = recovery.readActive()?.let { recovery.delete(it.id) }.orEmpty()

    fun pruneExpired(): List<AttachmentId> {
        val record = recovery.readActive() ?: return emptyList()
        return if (!record.updatedAt.plus(RETENTION).isAfter(clock.instant())) recovery.delete(record.id) else emptyList()
    }

    private companion object { val RETENTION: Duration = Duration.ofHours(24) }
}

sealed interface TemporaryAttachmentResult {
    data class Added(val recovery: TemporaryConversationRecovery, val wasAlreadyAttached: Boolean = false) : TemporaryAttachmentResult
    data object Cancelled : TemporaryAttachmentResult
    data class Rejected(val reason: String) : TemporaryAttachmentResult
}

/** Reuses P6-D2's private-copy owner, then links only the temporary ID and TEMPORARY_SESSION scope. */
class AddTemporaryConversationAttachmentUseCase(
    private val temporary: TemporaryConversationDomain,
    private val privateStore: PrivateAttachmentStore,
    private val assets: PrivateAttachmentRepository,
) {
    fun add(selection: ConversationAttachmentSelection?): TemporaryAttachmentResult {
        if (selection == null) return TemporaryAttachmentResult.Cancelled
        val current = temporary.enterOrRestore()
        if (current.draftAttachmentIds.size >= TemporaryConversationRecovery.MAX_ATTACHMENTS) return TemporaryAttachmentResult.Rejected("每次临时聊天最多添加 4 项附件。")
        val imported = privateStore.import(AttachmentImportRequest(selection.input, selection.mimeType, selection.displayName))
        val asset = (imported as? AttachmentImportResult.Imported)?.attachment
            ?: return TemporaryAttachmentResult.Rejected("附件未能复制到本机私有空间，临时草稿未改变。")
        val stable = runCatching { assets.save(asset) }.getOrElse { return TemporaryAttachmentResult.Rejected("附件目录写入失败，临时草稿未改变。") }
        val ids = (current.draftAttachmentIds + stable.id).distinct()
        return runCatching { TemporaryAttachmentResult.Added(temporary.updateDraft(current.draftText, ids), wasAlreadyAttached = ids.size == current.draftAttachmentIds.size) }
            .getOrElse { TemporaryAttachmentResult.Rejected("临时附件引用未保存，草稿仍保留。") }
    }
}

class ClearTemporaryConversationUseCase(
    private val temporary: TemporaryConversationDomain,
    private val assets: PrivateAttachmentRepository,
    private val privateStore: PrivateAttachmentStore,
) {
    fun execute(): Boolean {
        val ids = temporary.exitOrClear()
        ids.forEach { id -> assets.removeIfUnreferenced(id)?.let(privateStore::deletePrivateCopy) }
        return ids.isNotEmpty()
    }
    fun pruneExpired(): Boolean {
        val ids = temporary.pruneExpired()
        ids.forEach { id -> assets.removeIfUnreferenced(id)?.let(privateStore::deletePrivateCopy) }
        return ids.isNotEmpty()
    }
}
