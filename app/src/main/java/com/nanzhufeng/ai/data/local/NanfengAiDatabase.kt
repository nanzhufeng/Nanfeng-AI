package com.nanzhufeng.ai.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "capture_drafts")
data class CaptureDraftEntity(
    @androidx.room.PrimaryKey val id: String,
    val text: String?,
    val createdAtEpochMs: Long,
    val schemaVersion: Int,
)

@Entity(
    tableName = "capture_draft_evidence",
    primaryKeys = ["draftId", "position"],
    foreignKeys = [ForeignKey(
        entity = CaptureDraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["draftId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("draftId")],
)
data class CaptureDraftEvidenceEntity(
    val draftId: String,
    val position: Int,
    val sourceType: String,
    val receivedAtEpochMs: Long,
    val sourceReference: String?,
    val contributedFields: String,
)

@Entity(
    tableName = "capture_draft_attachments",
    primaryKeys = ["draftId", "position"],
    foreignKeys = [ForeignKey(
        entity = CaptureDraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["draftId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("draftId"), Index("attachmentId")],
)
data class CaptureDraftAttachmentEntity(
    val draftId: String,
    val position: Int,
    val attachmentId: String,
    val storageKey: String,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
)

@Entity(tableName = "knowledge_items", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class KnowledgeItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val body: String,
    val candidateId: String,
    val invocationId: String,
    val providerId: String,
    val modelId: String,
    val harnessVersion: Int,
    val createdAtEpochMs: Long,
    val schemaVersion: Int,
    val status: String = "ACTIVE",
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val archivedAtEpochMs: Long? = null,
    val deletedAtEpochMs: Long? = null,
    val contentHash: String = "",
)

/** P4-E history is append-only. Tags have their own relation so they remain searchable facts. */
@Entity(tableName = "knowledge_revisions", indices = [Index(value = ["knowledgeId", "revision"], unique = true), Index("knowledgeId")])
data class KnowledgeRevisionEntity(
    @androidx.room.PrimaryKey val id: String,
    val knowledgeId: String,
    val revision: Int,
    val title: String,
    val body: String,
    val status: String,
    val projectId: String?,
    val contentHash: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "knowledge_tags")
data class KnowledgeTagEntity(@androidx.room.PrimaryKey val name: String)

@Entity(tableName = "knowledge_item_tags", primaryKeys = ["knowledgeId", "tag"], indices = [Index("tag")])
data class KnowledgeItemTagEntity(val knowledgeId: String, val tag: String)

@Entity(tableName = "knowledge_revision_tags", primaryKeys = ["revisionId", "tag"], indices = [Index("tag")])
data class KnowledgeRevisionTagEntity(val revisionId: String, val tag: String)

@Entity(
    tableName = "knowledge_evidence",
    primaryKeys = ["knowledgeId", "position"],
    foreignKeys = [ForeignKey(
        entity = KnowledgeItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["knowledgeId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("knowledgeId")],
)
data class KnowledgeEvidenceEntity(
    val knowledgeId: String,
    val position: Int,
    val sourceType: String,
    val receivedAtEpochMs: Long,
    val sourceReference: String?,
    val contributedFields: String,
)

@Entity(
    tableName = "knowledge_attachments",
    primaryKeys = ["knowledgeId", "position"],
    foreignKeys = [ForeignKey(
        entity = KnowledgeItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["knowledgeId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("knowledgeId"), Index("attachmentId")],
)
data class KnowledgeAttachmentEntity(
    val knowledgeId: String,
    val position: Int,
    val attachmentId: String,
    val storageKey: String,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long,
    val sha256: String,
)

/** Attachment Domain's private asset catalog. Conversation tables only retain safe IDs. */
@Entity(tableName = "private_attachment_assets", indices = [Index(value = ["sha256"]), Index(value = ["storageKey"], unique = true)])
data class PrivateAttachmentAssetEntity(
    @androidx.room.PrimaryKey val attachmentId: String,
    val storageKey: String,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long,
    val sha256: String,
    val schemaVersion: Int = 1,
)

/** GLM-OCR stores only durable lineage and safe accounting metadata; content stays in attachments. */
@Entity(
    tableName = "glm_ocr_tasks",
    foreignKeys = [
        ForeignKey(entity = PrivateAttachmentAssetEntity::class, parentColumns = ["attachmentId"], childColumns = ["sourceAttachmentId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = PrivateAttachmentAssetEntity::class, parentColumns = ["attachmentId"], childColumns = ["resultAttachmentId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("sourceAttachmentId"), Index("resultAttachmentId"), Index(value = ["status", "updatedAtEpochMs"])],
)
data class GlmOcrTaskEntity(
    @androidx.room.PrimaryKey val taskId: String,
    val sourceAttachmentId: String,
    val resultAttachmentId: String?,
    val sourceDisplayName: String,
    val sourceMimeType: String,
    val sourceByteCount: Long,
    val sourceSha256: String,
    val status: String,
    val requestId: String?,
    val pageCount: Int?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val costCnyMicros: Long?,
    val safeErrorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** P6-E's recovery is intentionally outside every normal Conversation table. */
@Entity(tableName = "temporary_conversation_recovery")
data class TemporaryConversationRecoveryEntity(
    @androidx.room.PrimaryKey val temporaryId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val draftText: String,
    val modelOverrideId: String?,
    val schemaVersion: Int,
)

@Entity(
    tableName = "temporary_conversation_messages",
    foreignKeys = [ForeignKey(entity = TemporaryConversationRecoveryEntity::class, parentColumns = ["temporaryId"], childColumns = ["temporaryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("temporaryId")],
)
data class TemporaryConversationMessageEntity(
    @androidx.room.PrimaryKey val messageId: String,
    val temporaryId: String,
    val position: Int,
    val text: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "temporary_conversation_attachments",
    primaryKeys = ["temporaryId", "ownerKind", "ownerId", "position"],
    foreignKeys = [ForeignKey(entity = TemporaryConversationRecoveryEntity::class, parentColumns = ["temporaryId"], childColumns = ["temporaryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("temporaryId"), Index("attachmentId")],
)
data class TemporaryConversationAttachmentEntity(
    val temporaryId: String,
    val ownerKind: String,
    val ownerId: String,
    val position: Int,
    val attachmentId: String,
    val scope: String,
)

/** Safe top-level runtime metadata. Sensitive request and response content is intentionally absent. */
@Entity(tableName = "invocation_records")
data class InvocationRecordEntity(
    @androidx.room.PrimaryKey val id: String,
    val taskId: String,
    val providerId: String,
    val modelId: String,
    val harnessVersion: Int,
    val completedAtEpochMs: Long,
    val status: String,
    val errorCode: String?,
    val registrySnapshotId: String?,
    val pricingVersion: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val cachedInputTokens: Long?,
    val costPriceVersion: String?,
    val costCurrencyCode: String?,
    val costTotalMicros: Long?,
)

@Entity(
    tableName = "invocation_task_runs",
    foreignKeys = [ForeignKey(
        entity = InvocationRecordEntity::class,
        parentColumns = ["id"],
        childColumns = ["invocationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["invocationId"], unique = true), Index("completedAtEpochMs")],
)
data class InvocationTaskRunEntity(
    @androidx.room.PrimaryKey val id: String,
    val invocationId: String,
    val taskId: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val status: String,
)

@Entity(
    tableName = "provider_attempts",
    foreignKeys = [ForeignKey(
        entity = InvocationTaskRunEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskRunId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskRunId"), Index(value = ["taskRunId", "position"], unique = true)],
)
data class ProviderAttemptEntity(
    @androidx.room.PrimaryKey val id: String,
    val taskRunId: String,
    val position: Int,
    val providerId: String,
    val modelId: String,
    val registrySnapshotId: String?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val status: String,
    val errorCode: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val cachedInputTokens: Long?,
    val costPriceVersion: String?,
    val costCurrencyCode: String?,
    val costTotalMicros: Long?,
)

@Entity(
    tableName = "generations",
    foreignKeys = [ForeignKey(
        entity = ProviderAttemptEntity::class,
        parentColumns = ["id"],
        childColumns = ["attemptId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["attemptId"], unique = true)],
)
data class GenerationEntity(
    @androidx.room.PrimaryKey val id: String,
    val attemptId: String,
    val createdAtEpochMs: Long,
    val status: String,
)

@Entity(
    tableName = "generation_validations",
    foreignKeys = [ForeignKey(
        entity = GenerationEntity::class,
        parentColumns = ["id"],
        childColumns = ["generationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["generationId"], unique = true)],
)
data class GenerationValidationEntity(
    @androidx.room.PrimaryKey val id: String,
    val generationId: String,
    val completedAtEpochMs: Long,
    val status: String,
    val outputContractVersion: Int,
    val errorCode: String?,
)

/** P3-I's content-free durable bridge. Provider, Usage and message body ownership stay elsewhere. */
@Entity(
    tableName = "conversation_real_text_executions",
    indices = [Index(value = ["idempotencyKey"], unique = true), Index("conversationId"), Index("invocationId"), Index("attemptId")],
)
data class ConversationRealTextExecutionEntity(
    @androidx.room.PrimaryKey val executionId: String,
    val idempotencyKey: String,
    val requestFingerprint: String,
    val conversationId: String,
    val userMessageId: String,
    val assistantMessageId: String,
    val invocationId: String,
    val attemptId: String,
    val state: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val terminalAtEpochMs: Long?,
    val safeErrorCode: String?,
)

/** P0 append-only, quantitative facts. There is no prompt, response, secret or attachment column. */
@Entity(
    tableName = "usage_ledger_entries",
    indices = [
        Index(value = ["replayToken"], unique = true), Index("executionId"), Index("conversationId"),
        Index("reconcilesEntryId"), Index("reconciliationFingerprint"),
    ],
)
data class UsageLedgerEntryEntity(
    @androidx.room.PrimaryKey val entryId: String,
    val replayToken: String,
    val executionId: String,
    val conversationId: String,
    val branchLeafMessageId: String,
    val invocationId: String,
    val attemptId: String,
    val kind: String,
    val factGrade: String,
    val requestedModelId: String,
    val actualModelId: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cachedInputTokens: Long?,
    val chargeMicros: Long?,
    val budgetMicros: Long?,
    val adjustmentMicros: Long?,
    val currencyCode: String?,
    val reconciliationFingerprint: String?,
    val reconcilesEntryId: String?,
    val source: String,
    val occurredAtEpochMs: Long,
)

/** P6-L2 stores only exact-key hashes and a local message reference, never request or response text. */
@Entity(tableName = "local_exact_reuse_entries", indices = [Index("expiresAtEpochMs"), Index("revoked")])
data class LocalExactReuseEntryEntity(
    @androidx.room.PrimaryKey val canonicalRequestHash: String,
    val scopeId: String, val providerId: String, val modelSnapshotId: String, val endpointMode: String,
    val generationParametersHash: String, val toolSchemaHash: String, val contextManifestHash: String,
    val messageTreeHash: String, val attachmentHash: String, val templateVersion: String, val policyVersion: Long,
    val sensitivity: String, val keyVersion: String, val responseMessageId: String,
    val createdAtEpochMs: Long, val expiresAtEpochMs: Long, val revoked: Boolean,
)

/** Reviewable local output. This is intentionally not part of the content-free invocation ledger. */
@Entity(
    tableName = "generated_candidates",
    indices = [Index(value = ["invocationId"], unique = true), Index(value = ["status", "updatedAtEpochMs"])],
)
data class GeneratedCandidateEntity(
    @androidx.room.PrimaryKey val id: String,
    val taskId: String,
    val draftId: String,
    val invocationId: String,
    val providerId: String,
    val modelId: String,
    val harnessVersion: Int,
    val title: String,
    val body: String,
    val generatedAtEpochMs: Long,
    val schemaVersion: Int,
    val status: String,
    val updatedAtEpochMs: Long,
)

/** P3-A conversation truth. Message content never belongs in the invocation ledger. */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["deletedAtEpochMs", "pinnedAtEpochMs", "updatedAtEpochMs"]),
        Index(value = ["surface", "deletedAtEpochMs", "archivedAtEpochMs", "updatedAtEpochMs"]),
    ],
)
data class ConversationEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val projectId: String?,
    val currentLeafMessageId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val defaultProviderId: String?,
    val defaultModelId: String?,
    val harnessId: String?,
    val harnessVersion: Int?,
    val contextPolicyVersion: Int,
    val archivedAtEpochMs: Long?,
    val pinnedAtEpochMs: Long?,
    val deletedAtEpochMs: Long?,
    val schemaVersion: Int,
    val revision: Long = 1L,
    val autoTitlePending: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "'CHAT'")
    val surface: String = "CHAT",
    val favoritedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "message_nodes",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("conversationId"), Index("parentMessageId"), Index(value = ["conversationId", "parentMessageId", "siblingPosition"], unique = true)],
)
data class MessageNodeEntity(
    @androidx.room.PrimaryKey val id: String,
    val conversationId: String,
    val parentMessageId: String?,
    val siblingPosition: Int,
    val role: String,
    val createdAtEpochMs: Long,
    val deliveryState: String,
    val revision: Int,
    val revisesMessageId: String?,
    val invocationId: String?,
    val lastPersistedSequence: Long?,
    val resumableFromSequence: Long?,
    val schemaVersion: Int,
)

@Entity(
    tableName = "message_content_blocks",
    primaryKeys = ["messageId", "position"],
    foreignKeys = [ForeignKey(
        entity = MessageNodeEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("messageId")],
)
data class MessageContentBlockEntity(
    val messageId: String,
    val position: Int,
    val kind: String,
    val textContent: String?,
    val attachmentId: String?,
    val storageKey: String?,
    val mimeType: String?,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
    val toolName: String?,
    val toolSafeSummary: String?,
    val schemaVersion: Int,
)

/** Safe projection. The searchable payload is restricted to title, user/assistant text, and safe attachment labels. */
@Entity(tableName = "local_search_index", indices = [Index("conversationId"), Index(value = ["normalizedText", "deletedAtEpochMs", "archivedAtEpochMs"])])
data class LocalSearchIndexEntity(
    @androidx.room.PrimaryKey val id: String,
    val conversationId: String,
    val messageNodeId: String?,
    val contentKind: String,
    val title: String,
    val normalizedText: String,
    val snippet: String,
    val timestampEpochMs: Long,
    val archivedAtEpochMs: Long?,
    val deletedAtEpochMs: Long?,
)

/** Read-only search projections keep large catalogues out of full ConversationSnapshot rebuilds. */
data class ConversationSearchBrowseRow(
    val conversationId: String,
    val title: String,
    val snippet: String,
    val timestampEpochMs: Long,
)

data class ConversationPathTextSearchRow(
    val conversationId: String,
    val messageNodeId: String,
    val title: String,
    val text: String,
    val timestampEpochMs: Long,
)

data class ConversationPathAttachmentRow(
    val conversationId: String,
    val messageNodeId: String,
    val title: String,
    val createdAtEpochMs: Long,
    val attachmentId: String?,
    val storageKey: String?,
    val mimeType: String?,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
    val messageText: String,
)

@Entity(
    tableName = "conversation_drafts",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
)
data class ConversationDraftEntity(
    @androidx.room.PrimaryKey val conversationId: String,
    val text: String,
    val updatedAtEpochMs: Long,
    val schemaVersion: Int,
)

@Entity(
    tableName = "conversation_draft_attachments",
    primaryKeys = ["conversationId", "position"],
    foreignKeys = [ForeignKey(
        entity = ConversationDraftEntity::class,
        parentColumns = ["conversationId"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("conversationId"), Index("attachmentId")],
)
data class ConversationDraftAttachmentEntity(
    val conversationId: String,
    val position: Int,
    val attachmentId: String,
    val storageKey: String,
    val mimeType: String,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
)

@Entity(
    tableName = "conversation_memory_sources",
    primaryKeys = ["conversationId", "position"],
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.NO_ACTION,
    )],
    indices = [Index("conversationId"), Index("memoryId")],
)
data class ConversationMemorySourceEntity(
    val conversationId: String,
    val position: Int,
    val memoryId: String,
    val sourceKind: String,
    val sourceVersion: Int,
)

/** P3-B stores only event identity/order/fingerprint. Normalized output stays in MessageNode. */
@Entity(
    tableName = "ai_runtime_events",
    indices = [Index(value = ["invocationId", "sequence"], unique = true), Index("conversationId")],
)
data class AiRuntimeEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val invocationId: String,
    val conversationId: String,
    val messageId: String,
    val sequence: Long,
    val kind: String,
    val emittedAtEpochMs: Long,
    val payloadFingerprint: String,
    val source: String,
    val schemaVersion: Int,
)

@Entity(tableName = "conversation_runtime_states", indices = [Index(value = ["conversationId"], unique = true), Index("messageId")])
data class ConversationRuntimeStateEntity(
    @androidx.room.PrimaryKey val invocationId: String,
    val conversationId: String,
    val messageId: String,
    val nextExpectedSequence: Long,
    val status: String,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastCheckpointSequence: Long?,
    val resumableFromSequence: Long?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val safeErrorCode: String?,
    val schemaVersion: Int,
)

/** Content-free ordinary-chat request truth; payload, Key bytes and responses are never stored. */
@Entity(
    tableName = "normal_chat_send_attempts",
    indices = [Index(value = ["idempotencyKey"], unique = true), Index("conversationId"), Index("messageId"), Index(value = ["status", "updatedAtEpochMs"])],
)
data class NormalChatSendAttemptEntity(
    @androidx.room.PrimaryKey val attemptId: String,
    val messageId: String,
    val conversationId: String,
    val providerId: String,
    val modelId: String,
    val idempotencyKey: String,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val safeErrorCode: String?,
    val egressProviderId: String?,
    // Schema 53 compatibility only. These columns were introduced by the now-discarded gateway
    // experiment; normal chat never reads or writes their semantic values. They remain solely so
    // an already-installed database is not downgraded or destructively rebuilt.
    val executionMode: String,
    val gatewayTaskId: String?,
    val finalGatewaySequence: Long?,
    val gatewayFinalAcknowledgedAtEpochMs: Long?,
)

/** Content-free read projection for a completed normal-chat request boundary. */
data class CompletedNormalChatAttemptDuration(
    val attemptId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/** Immutable UI provenance; assistant content itself remains solely in message_nodes/blocks. */
@Entity(
    tableName = "assistant_response_model_attributions",
    primaryKeys = ["assistantMessageId", "attemptId"],
    indices = [
        Index("assistantMessageId"),
        Index("attemptId"),
        Index(value = ["costTotalMicros", "recordedAtEpochMs"]),
    ],
)
data class AssistantResponseModelAttributionEntity(
    val assistantMessageId: String,
    val attemptId: String,
    val providerId: String,
    val receiverProviderId: String,
    val modelId: String,
    val modelDisplayName: String,
    val recordedAtEpochMs: Long,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val cachedInputTokens: Long?,
    val reasoningTokens: Long?,
    val costPriceVersion: String?,
    val costCurrencyCode: String?,
    val costTotalMicros: Long?,
    val costSource: String?,
)

/** Content-free resumable upload cursor. The capable URL/token remains outside Room. */
@Entity(
    tableName = "resumable_attachment_uploads",
    indices = [
        Index(value = ["normalChatAttemptId", "attachmentId"], unique = true),
        Index(value = ["status", "updatedAtEpochMs"]),
        Index("gatewaySessionId"),
    ],
)
data class ResumableAttachmentUploadEntity(
    @androidx.room.PrimaryKey val uploadId: String,
    val normalChatAttemptId: String,
    val attachmentId: String,
    val providerId: String,
    val modelId: String,
    val gatewayId: String,
    val sha256: String,
    val byteCount: Long,
    val gatewaySessionId: String?,
    val acknowledgedBytes: Long,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val safeErrorCode: String?,
)

/** MM-O4-C session metadata. Assistant content remains exclusively in message_nodes/blocks. */
@Entity(
    tableName = "compare_conversation_sessions",
    indices = [Index(value = ["intentId"], unique = true), Index(value = ["confirmationId"], unique = true), Index("conversationId")],
)
data class CompareConversationSessionEntity(
    @androidx.room.PrimaryKey val sessionId: String,
    val intentId: String,
    val requestFingerprint: String,
    val confirmationId: String,
    val conversationId: String,
    val parentUserMessageId: String,
    val canonicalContextId: String,
    val canonicalContextHash: String,
    val canonicalContextRevision: Long,
    val sharedCurrentLeafAtPlanning: String,
    val synthesisPolicy: String,
    val sharedContextPolicy: String,
    val createdAtEpochMs: Long,
)

/** One row per logical Compare branch; execution/reservation IDs are references, never receipts. */
@Entity(
    tableName = "compare_conversation_branches",
    primaryKeys = ["sessionId", "branchId"],
    indices = [
        Index(value = ["assistantMessageId"], unique = true), Index(value = ["invocationId"], unique = true),
        Index(value = ["attemptId"], unique = true), Index(value = ["executionId"], unique = true),
        Index(value = ["cancellationId"], unique = true), Index("sessionId"),
    ],
)
data class CompareConversationBranchEntity(
    val sessionId: String,
    val branchId: String,
    val grantId: String,
    val requestFingerprint: String,
    val contextHash: String,
    val textSha256: String,
    val logicalModelId: String,
    val deploymentId: String,
    val providerHandle: String,
    val providerModelId: String,
    val catalogVersion: String,
    val priceVersion: String,
    val currencyCode: String,
    val maximumBudgetMicros: Long,
    val confirmationScopeFingerprint: String,
    val grantExpiresAtEpochMs: Long,
    val assistantMessageId: String,
    val invocationId: String,
    val attemptId: String,
    val executionId: String,
    val cancellationId: String,
    val usageReservationReplayToken: String,
)

/**
 * Secret-free, per-branch execution receipt for Compare.  This is deliberately separate from
 * P3's per-conversation receipt because two Compare branches share one Conversation and must
 * never contend for its current leaf/runtime slot.
 */
@Entity(
    tableName = "compare_branch_execution_receipts",
    indices = [Index("sessionId"), Index(value = ["sessionId", "branchId"], unique = true), Index("invocationId"), Index("attemptId")],
)
data class CompareBranchExecutionReceiptEntity(
    @androidx.room.PrimaryKey val executionId: String,
    val sessionId: String,
    val branchId: String,
    val invocationId: String,
    val attemptId: String,
    val requestFingerprint: String,
    val state: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val terminalAtEpochMs: Long?,
    val safeErrorCode: String?,
)

/** Independent Compare runtime/checkpoint projection; unlike P3 it is not unique by conversation. */
@Entity(
    tableName = "compare_branch_runtime_states",
    primaryKeys = ["sessionId", "branchId"],
    indices = [Index(value = ["sessionId", "invocationId"], unique = true), Index("assistantMessageId")],
)
data class CompareBranchRuntimeStateEntity(
    val sessionId: String,
    val branchId: String,
    val invocationId: String,
    val assistantMessageId: String,
    val state: String,
    val nextExpectedSequence: Long,
    val lastCheckpointSequence: Long?,
    val safeErrorCode: String?,
    val updatedAtEpochMs: Long,
)

/** Append-only, content-free Compare runtime event facts. */
@Entity(
    tableName = "compare_branch_runtime_events",
    indices = [Index(value = ["sessionId", "branchId", "sequence"], unique = true), Index("invocationId")],
)
data class CompareBranchRuntimeEventEntity(
    @androidx.room.PrimaryKey val eventId: String,
    val sessionId: String,
    val branchId: String,
    val invocationId: String,
    val sequence: Long,
    val kind: String,
    val payloadFingerprint: String,
    val emittedAtEpochMs: Long,
)

/** Typed, append-only idempotency facts for future Compare actions. */
@Entity(tableName = "compare_branch_terminal_intents", indices = [Index(value = ["sessionId", "branchId"])])
data class CompareBranchTerminalIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val sessionId: String,
    val branchId: String,
    val fingerprint: String,
    val nextState: String,
    val cancellationId: String?,
    val recordedAtEpochMs: Long,
)

@Entity(tableName = "compare_branch_follow_up_intents", indices = [Index(value = ["sessionId", "branchId"])])
data class CompareBranchFollowUpIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val sessionId: String,
    val branchId: String,
    val fingerprint: String,
    val canonicalContextId: String,
    val canonicalContextHash: String,
    val canonicalContextRevision: Long,
)

@Entity(tableName = "compare_branch_adoption_intents", indices = [Index("sessionId")])
data class CompareBranchAdoptionIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val sessionId: String,
    val branchId: String,
    val fingerprint: String,
)

@Entity(tableName = "compare_synthesis_intents", indices = [Index("sessionId")])
data class CompareSynthesisIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val sessionId: String,
    val fingerprint: String,
    val sourceBranchIds: String,
    val canonicalContextId: String,
    val canonicalContextHash: String,
    val canonicalContextRevision: Long,
    val gateId: String,
    val gateScopeFingerprint: String,
    val gateCurrencyCode: String,
    val gateMaximumBudgetMicros: Long,
    val gateExpiresAtEpochMs: Long,
)

/** P3-C's content-free, append-only conversation Invocation lineage. */
@Entity(
    tableName = "conversation_attempt_lineages",
    indices = [Index(value = ["intentId"], unique = true), Index("conversationId"), Index("originMessageId")],
)
data class ConversationAttemptLineageEntity(
    @androidx.room.PrimaryKey val invocationId: String,
    val intentId: String,
    val conversationId: String,
    val actionKind: String,
    val originMessageId: String,
    val createdMessageId: String,
    val previousInvocationId: String,
    val providerId: String,
    val modelId: String,
    val harnessId: String,
    val harnessVersion: Int,
    val registrySnapshotId: String,
    val pricingVersion: String?,
    val pricingCurrencyCode: String?,
    val inputMicrosPerToken: Long?,
    val outputMicrosPerToken: Long?,
    val cachedInputMicrosPerToken: Long?,
    val createdAtEpochMs: Long,
    val requestFingerprint: String,
    val schemaVersion: Int,
)

/** P3-E idempotency fact; it contains no message body, credential, prompt or provider payload. */
@Entity(
    tableName = "conversation_management_intents",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("conversationId")],
)
data class ConversationManagementIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val conversationId: String,
    val action: String,
    val requestFingerprint: String,
    val expectedRevision: Long,
    val resultRevision: Long,
    val createdAtEpochMs: Long,
)

/** Seven-day local diagnostics only; no credential, prompt, reply, attachment, or raw payload. */
@Entity(
    tableName = "debug_call_log",
    indices = [
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["providerId", "createdAtEpochMs"]),
        Index(value = ["conversationId", "createdAtEpochMs"]),
    ],
)
data class ProviderDiagnosticEntity(
    @androidx.room.PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val conversationId: String?,
    val providerId: String,
    val endpointHost: String,
    val apiModelId: String,
    val httpStatus: Int?,
    val errorClass: String,
    val redactedBody: String?,
    val requestShape: String,
    val latencyMs: Long?,
    val timeToFirstByteMs: Long?,
)

/** P4-A project metadata; launcher assets and external paths deliberately do not belong here. */
@Entity(tableName = "projects", indices = [Index(value = ["deletedAtEpochMs", "archivedAtEpochMs", "pinnedAtEpochMs", "updatedAtEpochMs"])])
data class ProjectEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val description: String,
    val colorSemantic: String?,
    val iconSemantic: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
    val pinnedAtEpochMs: Long?,
    val deletedAtEpochMs: Long?,
    val schemaVersion: Int,
)

@Entity(
    tableName = "project_instruction_revisions",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["projectId", "revision"], unique = true), Index("projectId")],
)
data class ProjectInstructionRevisionEntity(
    @androidx.room.PrimaryKey val id: String,
    val projectId: String,
    val revision: Int,
    val content: String,
    val source: String,
    val contentHash: String,
    val createdAtEpochMs: Long,
    val schemaVersion: Int,
)

/** P4-A idempotency fact. The request fingerprint has no user instruction body. */
@Entity(tableName = "project_intents", indices = [Index("projectId"), Index("conversationId"), Index("knowledgeId")])
data class ProjectIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val projectId: String,
    val action: String,
    val requestFingerprint: String,
    val conversationId: String?,
    val knowledgeId: String?,
    val createdAtEpochMs: Long,
)

/** Scope relation only: P4-A never copies Knowledge text into a project. */
@Entity(
    tableName = "knowledge_project_scopes",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("projectId")],
)
data class KnowledgeProjectScopeEntity(
    @androidx.room.PrimaryKey val knowledgeId: String,
    val projectId: String?,
    val updatedAtEpochMs: Long,
    val schemaVersion: Int,
)

/** P4-G stores only endpoint IDs and stable relation semantics; no Knowledge body or file data is copied. */
@Entity(
    tableName = "knowledge_relationships",
    indices = [Index(value = ["relationshipKey"], unique = true), Index(value = ["status", "updatedAtEpochMs"]), Index("fromKnowledgeId"), Index("toKnowledgeId")],
)
data class KnowledgeRelationshipEntity(
    @androidx.room.PrimaryKey val id: String,
    val relationshipKey: String,
    val type: String,
    val fromKnowledgeId: String,
    val toKnowledgeId: String,
    val scopeKind: String,
    val projectId: String?,
    val status: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val createdByIntentId: String,
    val latestIntentId: String,
    val suggestionSource: String,
)

@Entity(
    tableName = "knowledge_relationship_revisions",
    indices = [Index(value = ["relationshipId", "revision"], unique = true), Index("relationshipId"), Index("intentId")],
)
data class KnowledgeRelationshipRevisionEntity(
    @androidx.room.PrimaryKey val id: String,
    val relationshipId: String,
    val revision: Int,
    val action: String,
    val status: String,
    val intentId: String,
    val createdAtEpochMs: Long,
)

/** Intent fingerprints are idempotency/audit facts only; they never contain Knowledge content. */
@Entity(tableName = "knowledge_relationship_intents", indices = [Index("relationshipId")])
data class KnowledgeRelationshipIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val relationshipId: String?,
    val action: String,
    val requestFingerprint: String,
    val createdAtEpochMs: Long,
)

/** P4-H task facts contain only a private storage key and safe metadata, never external URIs or paths. */
@Entity(tableName = "markdown_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class MarkdownImportTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val status: String,
    val storageKey: String?,
    val mimeType: String?,
    val displayName: String?,
    val byteCount: Long?,
    val sha256: String?,
    val adapterVersion: Int,
    val failure: String?,
    val retryCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "markdown_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("knowledgeId")])
data class MarkdownImportItemEntity(
    val taskId: String,
    val id: String,
    val ordinal: Int,
    val title: String,
    val body: String,
    val tags: String,
    val status: String,
    val failure: String?,
    val knowledgeId: String?,
)

/** P4-L JSON queue is intentionally not Markdown's queue: adapter format and lifecycle ownership stay distinct. */
@Entity(tableName = "json_knowledge_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class JsonKnowledgeImportTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val status: String, val storageKey: String?, val mimeType: String?, val displayName: String?, val byteCount: Long?, val sha256: String?,
    val adapterVersion: Int, val failure: String?, val retryCount: Int, val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "json_knowledge_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("knowledgeId")])
data class JsonKnowledgeImportItemEntity(
    val taskId: String, val id: String, val ordinal: Int, val sourceStableId: String, val title: String, val body: String, val tags: String,
    val sourceSummary: String, val requestedScope: String, val status: String, val failure: String?, val knowledgeId: String?,
)

/** P6-H owns a separate durable queue and provenance ledger. No URI, path, token, or provider fact is stored. */
@Entity(tableName = "chatgpt_export_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class ChatGptExportImportTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val status: String, val storageKey: String?, val mimeType: String?, val displayName: String?, val byteCount: Long?, val packageHash: String?,
    val failure: String?, val retryCount: Int, val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "chatgpt_export_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("conversationId")])
data class ChatGptExportImportItemEntity(
    val taskId: String, val id: String, val ordinal: Int, val sourceConversationId: String?, val title: String?, val createdAtEpochMs: Long?, val updatedAtEpochMs: Long?, val contentHash: String?,
    val status: String, val failure: String?, val conversationId: String?,
)
@Entity(tableName = "chatgpt_export_import_messages", primaryKeys = ["taskId", "itemId", "sourceMessageId"], indices = [Index(value = ["taskId", "itemId"])])
data class ChatGptExportImportMessageEntity(
    val taskId: String, val itemId: String, val sourceMessageId: String, val parentSourceMessageId: String?, val siblingPosition: Int, val role: String, val text: String, val createdAtEpochMs: Long, val importedModel: String?,
)
@Entity(tableName = "chatgpt_import_provenance", indices = [Index(value = ["sourceConversationId", "packageHash"], unique = true)])
data class ChatGptImportProvenanceEntity(
    @androidx.room.PrimaryKey val conversationId: String, val sourceConversationId: String, val packageHash: String, val contentHash: String, val importedAtEpochMs: Long, val adapterId: String, val adapterVersion: Int, val revokedAtEpochMs: Long?,
)
@Entity(tableName = "chatgpt_import_receipts", primaryKeys = ["sourceConversationId", "packageHash"])
data class ChatGptImportReceiptEntity(val sourceConversationId: String, val packageHash: String, val conversationId: String, val contentHash: String, val committedAtEpochMs: Long)

/** P6-I has an isolated queue and provenance ledger; no external URI, path, token, or Provider fact is stored. */
@Entity(tableName = "claude_export_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class ClaudeExportImportTaskEntity(@androidx.room.PrimaryKey val id: String, val status: String, val storageKey: String?, val mimeType: String?, val displayName: String?, val byteCount: Long?, val packageHash: String?, val failure: String?, val retryCount: Int, val createdAtEpochMs: Long, val updatedAtEpochMs: Long)
@Entity(tableName = "claude_export_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("conversationId")])
data class ClaudeExportImportItemEntity(val taskId: String, val id: String, val ordinal: Int, val sourceConversationId: String?, val title: String?, val createdAtEpochMs: Long?, val updatedAtEpochMs: Long?, val contentHash: String?, val status: String, val failure: String?, val conversationId: String?)
@Entity(tableName = "claude_export_import_messages", primaryKeys = ["taskId", "itemId", "sourceMessageId"], indices = [Index(value = ["taskId", "itemId"])])
data class ClaudeExportImportMessageEntity(val taskId: String, val itemId: String, val sourceMessageId: String, val parentSourceMessageId: String?, val siblingPosition: Int, val role: String, val text: String, val createdAtEpochMs: Long)
@Entity(tableName = "claude_import_provenance", indices = [Index(value = ["sourceConversationId", "packageHash"], unique = true)])
data class ClaudeImportProvenanceEntity(@androidx.room.PrimaryKey val conversationId: String, val sourceConversationId: String, val packageHash: String, val contentHash: String, val importedAtEpochMs: Long, val adapterId: String, val adapterVersion: Int, val revokedAtEpochMs: Long?)
@Entity(tableName = "claude_import_receipts", primaryKeys = ["sourceConversationId", "packageHash"])
data class ClaudeImportReceiptEntity(val sourceConversationId: String, val packageHash: String, val conversationId: String, val contentHash: String, val committedAtEpochMs: Long)

/** P6-J keeps knowledge-export tasks and provenance entirely separate from other adapters. */
@Entity(tableName = "nanfeng_knowledge_export_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class NanfengKnowledgeExportImportTaskEntity(@androidx.room.PrimaryKey val id: String, val status: String, val storageKey: String?, val mimeType: String?, val displayName: String?, val byteCount: Long?, val packageHash: String?, val failure: String?, val retryCount: Int, val createdAtEpochMs: Long, val updatedAtEpochMs: Long)
@Entity(tableName = "nanfeng_knowledge_export_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("conversationId")])
data class NanfengKnowledgeExportImportItemEntity(val taskId: String, val id: String, val ordinal: Int, val sourceConversationId: String?, val title: String?, val createdAtEpochMs: Long?, val updatedAtEpochMs: Long?, val contentHash: String?, val status: String, val failure: String?, val conversationId: String?)
@Entity(tableName = "nanfeng_knowledge_export_import_messages", primaryKeys = ["taskId", "itemId", "sourceMessageId"], indices = [Index(value = ["taskId", "itemId"])])
data class NanfengKnowledgeExportImportMessageEntity(val taskId: String, val itemId: String, val sourceMessageId: String, val parentSourceMessageId: String?, val siblingPosition: Int, val role: String, val text: String, val createdAtEpochMs: Long)
@Entity(tableName = "nanfeng_knowledge_import_provenance", indices = [Index(value = ["sourceConversationId", "packageHash"], unique = true)])
data class NanfengKnowledgeImportProvenanceEntity(@androidx.room.PrimaryKey val conversationId: String, val sourceConversationId: String, val packageHash: String, val contentHash: String, val importedAtEpochMs: Long, val adapterId: String, val adapterVersion: Int, val revokedAtEpochMs: Long?)
@Entity(tableName = "nanfeng_knowledge_import_receipts", primaryKeys = ["sourceConversationId", "packageHash"])
data class NanfengKnowledgeImportReceiptEntity(val sourceConversationId: String, val packageHash: String, val conversationId: String, val contentHash: String, val committedAtEpochMs: Long)

/** P6-K ZIP candidates are intentionally isolated from the JSON adapter queues and all business tables. */
@Entity(tableName = "p6k_zip_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class P6KZipImportTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val provider: String, val displayName: String, val byteCount: Long, val packageHash: String, val status: String, val failure: String?, val formatVersion: String?,
    val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "p6k_zip_asset_recovery_jobs", indices = [Index(value = ["state", "updatedAtMs"])])
data class P6KZipAssetRecoveryJobEntity(
    @androidx.room.PrimaryKey val taskId: String,
    val state: String,
    val totalOccurrences: Int,
    val linkedOccurrences: Int,
    val totalConversations: Int,
    val processedConversations: Int,
    val failedConversations: Int,
    val uniqueAssets: Int,
    val missingEntries: Int,
    val unattributedCandidates: Int,
    val sourceReferenceRecords: Int,
    val fallbackNamedAssets: Int,
    val inferredGeneratedImages: Int,
    val originLinkedLibraryImages: Int,
    val inferredLibraryImages: Int,
    val lastFailureKind: String?,
    val lastFailureAtMs: Long?,
    val indexVersion: Int,
    val updatedAtMs: Long,
)
@Entity(tableName = "p6k_zip_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId")])
data class P6KZipImportItemEntity(
    val taskId: String, val id: String, val ordinal: Int, val sourceConversationId: String?, val title: String?, val createdAtEpochMs: Long?, val updatedAtEpochMs: Long?, val contentHash: String?, val status: String, val failure: String?, val conversationId: String?,
)
@Entity(tableName = "p6k_zip_import_messages", primaryKeys = ["taskId", "itemId", "sourceMessageId"], indices = [Index(value = ["taskId", "itemId"])])
data class P6KZipImportMessageEntity(
    val taskId: String, val itemId: String, val sourceMessageId: String, val parentSourceMessageId: String?, val siblingPosition: Int, val role: String, val text: String, val createdAtEpochMs: Long, val importedModel: String?,
)
@Entity(tableName = "p6k_zip_asset_candidates", primaryKeys = ["taskId", "entryName"], indices = [Index("taskId")])
data class P6KZipAssetCandidateEntity(
    val taskId: String, val entryName: String, val sha256: String, val byteCount: Long, val mimeType: String, val role: String, val sourceConversationId: String?, val sourceMessageId: String?, val linkedConversationId: String? = null, val linkedMessageId: String? = null, val attachmentId: String? = null, val failure: String? = null,
)
@Entity(tableName = "p6k_zip_asset_link_receipts", primaryKeys = ["taskId", "entryName"], indices = [Index("attachmentId"), Index("conversationId")])
data class P6KZipAssetLinkReceiptEntity(val taskId: String, val entryName: String, val sha256: String, val attachmentId: String, val conversationId: String, val messageId: String, val committedAtEpochMs: Long)
@Entity(tableName = "p6k_zip_asset_catalog", primaryKeys = ["taskId", "entryName"], indices = [Index("attachmentId")])
data class P6KZipAssetCatalogEntity(
    val taskId: String,
    val entryName: String,
    val sha256: String?,
    val byteCount: Long?,
    val mimeType: String?,
    val displayName: String?,
    val attachmentId: String?,
    val missingInArchive: Boolean,
    val verificationState: String,
)
@Entity(
    tableName = "p6k_zip_asset_occurrence",
    primaryKeys = ["taskId", "entryName", "sourceConversationId", "sourceMessageId"],
    indices = [Index(value = ["taskId", "sourceConversationId"]), Index(value = ["taskId", "entryName"])],
)
data class P6KZipAssetOccurrenceEntity(
    val taskId: String,
    val entryName: String,
    val sourceConversationId: String,
    val sourceMessageId: String,
)
@Entity(
    tableName = "p6k_zip_asset_occurrence_receipt",
    primaryKeys = ["taskId", "entryName", "sourceConversationId", "sourceMessageId"],
    indices = [Index("attachmentId"), Index("conversationId"), Index("messageId")],
)
data class P6KZipAssetOccurrenceReceiptEntity(
    val taskId: String,
    val entryName: String,
    val sourceConversationId: String,
    val sourceMessageId: String,
    val conversationId: String,
    val messageId: String,
    val attachmentId: String,
    val linkedAtMs: Long,
)
@Entity(tableName = "p6k_zip_asset_link_provenance", primaryKeys = ["attachmentId"], indices = [Index("taskId")])
data class P6KZipAssetLinkProvenanceEntity(val attachmentId: String, val taskId: String, val entryName: String, val sha256: String, val conversationId: String, val messageId: String, val importedAtEpochMs: Long)
@Entity(tableName = "p6k_zip_profile_candidates")
data class P6KZipProfileCandidateEntity(@androidx.room.PrimaryKey val taskId: String, val status: String, val mappedFieldCount: Int)
@Entity(tableName = "p6k_zip_import_provenance", indices = [Index(value = ["sourceConversationId", "packageHash"], unique = true)])
data class P6KZipImportProvenanceEntity(@androidx.room.PrimaryKey val conversationId: String, val taskId: String, val itemId: String, val sourceConversationId: String, val packageHash: String, val contentHash: String, val importedAtEpochMs: Long, val adapterId: String, val adapterVersion: Int)
/** Stable source-message to local-node binding permits append-only updates from a newer
 * cumulative ChatGPT export without recreating a conversation or guessing node identity. */
@Entity(tableName = "p6k_zip_import_message_provenance", primaryKeys = ["conversationId", "sourceMessageId"], indices = [Index("conversationId"), Index("messageId")])
data class P6KZipImportMessageProvenanceEntity(val conversationId: String, val sourceMessageId: String, val messageId: String, val contentHash: String)
@Entity(tableName = "p6k_zip_import_receipts", primaryKeys = ["sourceConversationId", "packageHash"])
data class P6KZipImportReceiptEntity(val sourceConversationId: String, val packageHash: String, val taskId: String, val itemId: String, val conversationId: String, val contentHash: String, val committedAtEpochMs: Long)

/** The single native K6 personalization/settings projection.  It is not an account record. */
@Entity(tableName = "third_party_profile_personalization_settings")
data class ThirdPartyProfilePersonalizationSettingsEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val displayName: String?, val language: String?, val timezone: String?, val publicBio: String?,
    val customInstructions: String?, val theme: String?, val notificationsEnabled: Boolean?,
    val sourceTaskId: String, val revision: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "p6k_profile_import_provenance")
data class P6KProfileImportProvenanceEntity(
    @androidx.room.PrimaryKey val taskId: String, val provider: String, val packageHash: String,
    val canonicalHash: String, val importedAtEpochMs: Long,
)
@Entity(primaryKeys = ["provider", "packageHash"], tableName = "p6k_profile_import_receipts")
data class P6KProfileImportReceiptEntity(
    val provider: String, val packageHash: String, val taskId: String, val canonicalHash: String, val committedAtEpochMs: Long,
)

/** P4-N PDF text facts are intentionally separate from Markdown and JSON adapter queues. */
@Entity(tableName = "pdf_text_import_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class PdfTextImportTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val status: String, val storageKey: String?, val mimeType: String?, val displayName: String?, val byteCount: Long?, val sourceSha256: String?,
    val adapterVersion: Int, val pageCount: Int, val extractedPageCount: Int, val extractionSha256: String?, val failure: String?, val retryCount: Int,
    val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "pdf_text_import_pages", primaryKeys = ["taskId", "pageNumber"], indices = [Index("taskId")])
data class PdfTextImportPageEntity(val taskId: String, val pageNumber: Int, val textSha256: String, val codePointCount: Int, val extractionVersion: Int)
@Entity(tableName = "pdf_text_import_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("knowledgeId")])
data class PdfTextImportItemEntity(
    val taskId: String, val id: String, val ordinal: Int, val pageNumber: Int, val title: String, val body: String, val tags: String, val candidateSha256: String,
    val status: String, val failure: String?, val knowledgeId: String?,
)

/** P4-O owns an HTTPS snapshot queue; safe URL summaries never contain query or external IP. */
@Entity(tableName = "web_text_snapshot_tasks", indices = [Index(value = ["status", "updatedAtEpochMs"])])
data class WebTextSnapshotTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val status: String, val requestedUrl: String, val storageKey: String?, val displayUrl: String?, val host: String?, val rawHtmlSha256: String?, val byteCount: Long?, val adapterVersion: Int,
    val extractedSha256: String?, val failure: String?, val retryCount: Int, val createdAtEpochMs: Long, val updatedAtEpochMs: Long,
)
@Entity(tableName = "web_text_snapshot_items", primaryKeys = ["taskId", "id"], indices = [Index("taskId"), Index("knowledgeId")])
data class WebTextSnapshotItemEntity(
    val taskId: String, val id: String, val ordinal: Int, val title: String, val body: String, val candidateSha256: String, val status: String, val failure: String?, val knowledgeId: String?,
)

/** P4-I stores only append-only offline Eval run facts; packaged fixture bodies never enter Room. */
@Entity(tableName = "offline_eval_runs", indices = [Index(value = ["completedAtEpochMs", "id"])])
data class OfflineEvalRunEntity(
    @androidx.room.PrimaryKey val id: String,
    val datasetId: String,
    val datasetVersion: String,
    val domainVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val fixtureManifestHash: String,
    val assertionVersion: String,
    val rubricVersion: String,
    val securitySummary: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
)

@Entity(tableName = "offline_eval_case_results", primaryKeys = ["runId", "caseId"], indices = [Index("caseId")])
data class OfflineEvalCaseResultEntity(val runId: String, val caseId: String, val verdict: String)

@Entity(tableName = "offline_eval_assertions", primaryKeys = ["runId", "assertionId"], indices = [Index(value = ["runId", "caseId"])])
data class OfflineEvalAssertionEntity(val runId: String, val assertionId: String, val caseId: String, val fact: String, val verdict: String, val detail: String)

@Entity(tableName = "offline_eval_human_scores", indices = [Index(value = ["runId", "caseId"])])
data class OfflineEvalHumanScoreEntity(@androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0, val runId: String, val caseId: String, val dimension: String, val score: Int?, val reviewerAlias: String, val rubricVersion: String, val notedAtEpochMs: Long, val note: String?)

/** P4-C local-only Memory facts. No credential, provider payload, prompt, URI or path column exists. */
@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index(value = ["scopeKind", "projectId", "conversationId", "status", "updatedAtEpochMs"]), Index("projectId"), Index("conversationId"), Index(value = ["scopeKey", "contentHash"]), Index(value = ["scopeKey", "conceptHash"])],
)
data class MemoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val body: String,
    val scopeKind: String,
    val projectId: String?,
    val conversationId: String?,
    val scopeKey: String,
    val source: String,
    val sourceStableId: String,
    val sourceSummary: String,
    val status: String,
    val contentHash: String,
    val conceptHash: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastConfirmedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val schemaVersion: Int,
)

@Entity(
    tableName = "memory_revisions",
    foreignKeys = [ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index(value = ["memoryId", "revision"], unique = true), Index("memoryId")],
)
data class MemoryRevisionEntity(
    @androidx.room.PrimaryKey val id: String,
    val memoryId: String,
    val revision: Int,
    val title: String,
    val body: String,
    val scopeKind: String,
    val projectId: String?,
    val conversationId: String?,
    val source: String,
    val sourceStableId: String,
    val sourceSummary: String,
    val status: String,
    val contentHash: String,
    val createdAtEpochMs: Long,
    val schemaVersion: Int,
)

/** Intent stores a fingerprint only; it deliberately has no user body column. */
@Entity(tableName = "memory_intents", indices = [Index("memoryId"), Index("conflictId")])
data class MemoryIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val memoryId: String?,
    val action: String,
    val requestFingerprint: String,
    val conflictId: String?,
    val createdAtEpochMs: Long,
)

/** A deterministic same-concept candidate is held locally until the user explicitly resolves it. */
@Entity(
    tableName = "memory_conflicts",
    foreignKeys = [ForeignKey(entity = MemoryEntity::class, parentColumns = ["id"], childColumns = ["existingMemoryId"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("intentId"), Index("existingMemoryId"), Index("status")],
)
data class MemoryConflictEntity(
    @androidx.room.PrimaryKey val id: String,
    val intentId: String,
    val existingMemoryId: String,
    val candidateTitle: String,
    val candidateBody: String,
    val scopeKind: String,
    val projectId: String?,
    val conversationId: String?,
    val source: String,
    val sourceStableId: String,
    val sourceSummary: String,
    val candidateContentHash: String,
    val status: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long?,
)

@Dao
interface CaptureDraftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDraft(draft: CaptureDraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEvidence(evidence: List<CaptureDraftEvidenceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAttachments(attachments: List<CaptureDraftAttachmentEntity>)

    @Query("SELECT * FROM capture_drafts WHERE id = :id")
    fun findDraft(id: String): CaptureDraftEntity?

    @Query("SELECT * FROM capture_drafts ORDER BY createdAtEpochMs DESC, rowid DESC LIMIT 1")
    fun findLatestDraft(): CaptureDraftEntity?

    @Query("SELECT * FROM capture_draft_evidence WHERE draftId = :draftId ORDER BY position")
    fun evidenceFor(draftId: String): List<CaptureDraftEvidenceEntity>

    @Query("SELECT * FROM capture_draft_attachments WHERE draftId = :draftId ORDER BY position")
    fun attachmentsFor(draftId: String): List<CaptureDraftAttachmentEntity>

    @Transaction
    fun insertBundle(
        draft: CaptureDraftEntity,
        evidence: List<CaptureDraftEvidenceEntity>,
        attachments: List<CaptureDraftAttachmentEntity>,
    ) {
        insertDraft(draft)
        insertEvidence(evidence)
        insertAttachments(attachments)
    }
}

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertKnowledge(item: KnowledgeItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEvidence(evidence: List<KnowledgeEvidenceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAttachments(attachments: List<KnowledgeAttachmentEntity>)

    @Query("SELECT * FROM knowledge_items WHERE id = :id")
    fun findKnowledge(id: String): KnowledgeItemEntity?

    @Query("SELECT * FROM knowledge_items WHERE status='ACTIVE' ORDER BY createdAtEpochMs DESC, id DESC")
    fun listKnowledge(): List<KnowledgeItemEntity>

    @Query("SELECT * FROM knowledge_items ORDER BY updatedAtEpochMs DESC, id ASC")
    fun listKnowledgeIncludingHidden(): List<KnowledgeItemEntity>

    @Query("UPDATE knowledge_items SET title=:title, body=:body, status=:status, updatedAtEpochMs=:updatedAtEpochMs, archivedAtEpochMs=:archivedAtEpochMs, deletedAtEpochMs=:deletedAtEpochMs, contentHash=:contentHash WHERE id=:id")
    fun updateKnowledge(id: String, title: String, body: String, status: String, updatedAtEpochMs: Long, archivedAtEpochMs: Long?, deletedAtEpochMs: Long?, contentHash: String): Int

    @Query("SELECT * FROM knowledge_revisions WHERE knowledgeId=:knowledgeId ORDER BY revision ASC") fun revisionsFor(knowledgeId: String): List<KnowledgeRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRevision(revision: KnowledgeRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertTags(tags: List<KnowledgeTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertItemTags(tags: List<KnowledgeItemTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertRevisionTags(tags: List<KnowledgeRevisionTagEntity>)
    @Query("DELETE FROM knowledge_item_tags WHERE knowledgeId=:knowledgeId") fun clearItemTags(knowledgeId: String)
    @Query("SELECT tag FROM knowledge_item_tags WHERE knowledgeId=:knowledgeId ORDER BY tag ASC") fun tagsFor(knowledgeId: String): List<String>
    @Query("SELECT tag FROM knowledge_revision_tags WHERE revisionId=:revisionId ORDER BY tag ASC") fun tagsForRevision(revisionId: String): List<String>
    @Query("SELECT projectId FROM knowledge_project_scopes WHERE knowledgeId=:knowledgeId") fun projectScopeFor(knowledgeId: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertProjectScope(scope: KnowledgeProjectScopeEntity)

    @Query("SELECT * FROM knowledge_evidence WHERE knowledgeId = :knowledgeId ORDER BY position")
    fun evidenceFor(knowledgeId: String): List<KnowledgeEvidenceEntity>

    @Query("SELECT * FROM knowledge_attachments WHERE knowledgeId = :knowledgeId ORDER BY position")
    fun attachmentsFor(knowledgeId: String): List<KnowledgeAttachmentEntity>

    @Transaction
    fun insertBundle(
        item: KnowledgeItemEntity,
        evidence: List<KnowledgeEvidenceEntity>,
        attachments: List<KnowledgeAttachmentEntity>,
    ) {
        insertKnowledge(item)
        insertEvidence(evidence)
        insertAttachments(attachments)
    }
}

@Dao
interface KnowledgeRelationshipDao {
    @Query("SELECT * FROM knowledge_relationships ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<KnowledgeRelationshipEntity>
    @Query("SELECT * FROM knowledge_relationships WHERE id=:id") fun find(id: String): KnowledgeRelationshipEntity?
    @Query("SELECT * FROM knowledge_relationships WHERE relationshipKey=:key") fun findByKey(key: String): KnowledgeRelationshipEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(relationship: KnowledgeRelationshipEntity)
    @Query("UPDATE knowledge_relationships SET status=:status, updatedAtEpochMs=:updatedAtEpochMs, latestIntentId=:latestIntentId, suggestionSource=:suggestionSource WHERE id=:id") fun update(id: String, status: String, updatedAtEpochMs: Long, latestIntentId: String, suggestionSource: String): Int
    @Query("SELECT * FROM knowledge_relationship_revisions WHERE relationshipId=:relationshipId ORDER BY revision ASC") fun revisions(relationshipId: String): List<KnowledgeRelationshipRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRevision(revision: KnowledgeRelationshipRevisionEntity)
    @Query("SELECT * FROM knowledge_relationship_intents WHERE intentId=:intentId") fun intent(intentId: String): KnowledgeRelationshipIntentEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertIntent(intent: KnowledgeRelationshipIntentEntity)
}

@Dao
interface MarkdownImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: MarkdownImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<MarkdownImportItemEntity>)
    @Query("DELETE FROM markdown_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("SELECT * FROM markdown_import_tasks WHERE id=:id") fun task(id: String): MarkdownImportTaskEntity?
    @Query("SELECT * FROM markdown_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<MarkdownImportTaskEntity>
    @Query("SELECT * FROM markdown_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<MarkdownImportItemEntity>
}

@Dao
interface JsonKnowledgeImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: JsonKnowledgeImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<JsonKnowledgeImportItemEntity>)
    @Query("DELETE FROM json_knowledge_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("SELECT * FROM json_knowledge_import_tasks WHERE id=:id") fun task(id: String): JsonKnowledgeImportTaskEntity?
    @Query("SELECT * FROM json_knowledge_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<JsonKnowledgeImportTaskEntity>
    @Query("SELECT * FROM json_knowledge_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<JsonKnowledgeImportItemEntity>
}

@Dao
interface ChatGptExportImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: ChatGptExportImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<ChatGptExportImportItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertMessages(messages: List<ChatGptExportImportMessageEntity>)
    @Query("DELETE FROM chatgpt_export_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("DELETE FROM chatgpt_export_import_messages WHERE taskId=:taskId") fun clearMessages(taskId: String)
    @Query("SELECT * FROM chatgpt_export_import_tasks WHERE id=:id") fun task(id: String): ChatGptExportImportTaskEntity?
    @Query("SELECT * FROM chatgpt_export_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<ChatGptExportImportTaskEntity>
    @Query("SELECT * FROM chatgpt_export_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<ChatGptExportImportItemEntity>
    @Query("SELECT * FROM chatgpt_export_import_messages WHERE taskId=:taskId AND itemId=:itemId ORDER BY siblingPosition ASC, sourceMessageId ASC") fun messages(taskId: String, itemId: String): List<ChatGptExportImportMessageEntity>
    @Query("SELECT * FROM chatgpt_import_receipts WHERE sourceConversationId=:sourceConversationId AND packageHash=:packageHash") fun receipt(sourceConversationId: String, packageHash: String): ChatGptImportReceiptEntity?
    @Query("SELECT * FROM chatgpt_import_provenance WHERE sourceConversationId=:sourceConversationId ORDER BY importedAtEpochMs DESC") fun provenanceForSource(sourceConversationId: String): List<ChatGptImportProvenanceEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM chatgpt_import_provenance WHERE conversationId=:conversationId)") fun hasProvenanceForConversation(conversationId: String): Boolean
    @Query("SELECT DISTINCT conversationId FROM chatgpt_import_provenance WHERE conversationId IN (:conversationIds)") fun conversationIdsWithProvenance(conversationIds: List<String>): List<String>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProvenance(value: ChatGptImportProvenanceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: ChatGptImportReceiptEntity)
}

@Dao
interface ClaudeExportImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: ClaudeExportImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<ClaudeExportImportItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertMessages(messages: List<ClaudeExportImportMessageEntity>)
    @Query("DELETE FROM claude_export_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("DELETE FROM claude_export_import_messages WHERE taskId=:taskId") fun clearMessages(taskId: String)
    @Query("SELECT * FROM claude_export_import_tasks WHERE id=:id") fun task(id: String): ClaudeExportImportTaskEntity?
    @Query("SELECT * FROM claude_export_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<ClaudeExportImportTaskEntity>
    @Query("SELECT * FROM claude_export_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<ClaudeExportImportItemEntity>
    @Query("SELECT * FROM claude_export_import_messages WHERE taskId=:taskId AND itemId=:itemId ORDER BY siblingPosition ASC, sourceMessageId ASC") fun messages(taskId: String, itemId: String): List<ClaudeExportImportMessageEntity>
    @Query("SELECT * FROM claude_import_receipts WHERE sourceConversationId=:sourceConversationId AND packageHash=:packageHash") fun receipt(sourceConversationId: String, packageHash: String): ClaudeImportReceiptEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM claude_import_provenance WHERE conversationId=:conversationId)") fun hasProvenanceForConversation(conversationId: String): Boolean
    @Query("SELECT DISTINCT conversationId FROM claude_import_provenance WHERE conversationId IN (:conversationIds)") fun conversationIdsWithProvenance(conversationIds: List<String>): List<String>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProvenance(value: ClaudeImportProvenanceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: ClaudeImportReceiptEntity)
}

@Dao
interface NanfengKnowledgeExportImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: NanfengKnowledgeExportImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<NanfengKnowledgeExportImportItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertMessages(messages: List<NanfengKnowledgeExportImportMessageEntity>)
    @Query("DELETE FROM nanfeng_knowledge_export_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("DELETE FROM nanfeng_knowledge_export_import_messages WHERE taskId=:taskId") fun clearMessages(taskId: String)
    @Query("SELECT * FROM nanfeng_knowledge_export_import_tasks WHERE id=:id") fun task(id: String): NanfengKnowledgeExportImportTaskEntity?
    @Query("SELECT * FROM nanfeng_knowledge_export_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<NanfengKnowledgeExportImportTaskEntity>
    @Query("SELECT * FROM nanfeng_knowledge_export_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<NanfengKnowledgeExportImportItemEntity>
    @Query("SELECT * FROM nanfeng_knowledge_export_import_messages WHERE taskId=:taskId AND itemId=:itemId ORDER BY siblingPosition ASC, sourceMessageId ASC") fun messages(taskId: String, itemId: String): List<NanfengKnowledgeExportImportMessageEntity>
    @Query("SELECT * FROM nanfeng_knowledge_import_receipts WHERE sourceConversationId=:sourceConversationId AND packageHash=:packageHash") fun receipt(sourceConversationId: String, packageHash: String): NanfengKnowledgeImportReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProvenance(value: NanfengKnowledgeImportProvenanceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: NanfengKnowledgeImportReceiptEntity)
}

@Dao
interface P6KZipImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: P6KZipImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<P6KZipImportItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertMessages(messages: List<P6KZipImportMessageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertAssets(assets: List<P6KZipAssetCandidateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertProfile(profile: P6KZipProfileCandidateEntity)
    @Query("DELETE FROM p6k_zip_import_messages WHERE taskId=:taskId") fun clearMessages(taskId: String)
    @Query("DELETE FROM p6k_zip_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("DELETE FROM p6k_zip_asset_candidates WHERE taskId=:taskId") fun clearAssets(taskId: String)
    @Query("DELETE FROM p6k_zip_profile_candidates WHERE taskId=:taskId") fun clearProfile(taskId: String)
    @Query("DELETE FROM p6k_zip_import_tasks WHERE id=:taskId") fun deleteTask(taskId: String)
    @Query("SELECT * FROM p6k_zip_import_tasks WHERE id=:id") fun task(id: String): P6KZipImportTaskEntity?
    @Query("SELECT * FROM p6k_zip_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<P6KZipImportTaskEntity>
    @Query("SELECT * FROM p6k_zip_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<P6KZipImportItemEntity>
    @Query("SELECT * FROM p6k_zip_import_messages WHERE taskId=:taskId AND itemId=:itemId ORDER BY siblingPosition ASC, sourceMessageId ASC") fun messages(taskId: String, itemId: String): List<P6KZipImportMessageEntity>
    @Query("SELECT * FROM p6k_zip_asset_candidates WHERE taskId=:taskId ORDER BY entryName ASC") fun assets(taskId: String): List<P6KZipAssetCandidateEntity>
    @Query("SELECT * FROM p6k_zip_asset_candidates WHERE taskId=:taskId AND entryName=:entryName") fun asset(taskId: String, entryName: String): P6KZipAssetCandidateEntity?
    @Query("SELECT * FROM p6k_zip_profile_candidates WHERE taskId=:taskId") fun profile(taskId: String): P6KZipProfileCandidateEntity?
    @Query("UPDATE p6k_zip_profile_candidates SET status=:status WHERE taskId=:taskId") fun updateProfileStatus(taskId: String, status: String): Int
    @Query("SELECT * FROM p6k_zip_import_receipts WHERE sourceConversationId=:sourceConversationId AND packageHash=:packageHash") fun receipt(sourceConversationId: String, packageHash: String): P6KZipImportReceiptEntity?
    @Query("SELECT * FROM p6k_zip_import_provenance WHERE sourceConversationId=:sourceConversationId ORDER BY importedAtEpochMs DESC, conversationId ASC") fun provenanceForSource(sourceConversationId: String): List<P6KZipImportProvenanceEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM p6k_zip_import_provenance WHERE conversationId=:conversationId)") fun hasProvenanceForConversation(conversationId: String): Boolean
    @Query("SELECT DISTINCT conversationId FROM p6k_zip_import_provenance WHERE conversationId IN (:conversationIds)") fun conversationIdsWithProvenance(conversationIds: List<String>): List<String>
    @Query("SELECT * FROM p6k_zip_import_provenance WHERE taskId=:taskId ORDER BY conversationId ASC") fun provenanceForTask(taskId: String): List<P6KZipImportProvenanceEntity>
    @Query("SELECT * FROM p6k_zip_import_message_provenance WHERE conversationId=:conversationId ORDER BY sourceMessageId ASC") fun messageProvenanceForConversation(conversationId: String): List<P6KZipImportMessageProvenanceEntity>
    @Query("DELETE FROM p6k_zip_import_receipts WHERE taskId=:taskId") fun deleteReceiptsForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_import_provenance WHERE taskId=:taskId") fun deleteProvenanceForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_import_message_provenance WHERE conversationId=:conversationId") fun deleteMessageProvenanceForConversation(conversationId: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertProvenance(value: P6KZipImportProvenanceEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertMessageProvenance(values: List<P6KZipImportMessageProvenanceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: P6KZipImportReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAssetLinkReceipt(value: P6KZipAssetLinkReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertAssetCatalog(value: P6KZipAssetCatalogEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertAssetOccurrence(value: P6KZipAssetOccurrenceEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAssetOccurrenceReceipt(value: P6KZipAssetOccurrenceReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAssetLinkProvenance(value: P6KZipAssetLinkProvenanceEntity)
    @Query("SELECT * FROM p6k_zip_asset_link_receipts WHERE taskId=:taskId AND entryName=:entryName") fun assetLinkReceipt(taskId: String, entryName: String): P6KZipAssetLinkReceiptEntity?
    @Query("SELECT * FROM p6k_zip_asset_occurrence_receipt WHERE taskId=:taskId AND entryName=:entryName AND sourceConversationId=:sourceConversationId AND sourceMessageId=:sourceMessageId")
    fun assetOccurrenceReceipt(taskId: String, entryName: String, sourceConversationId: String, sourceMessageId: String): P6KZipAssetOccurrenceReceiptEntity?
    @Query("SELECT COUNT(*) FROM p6k_zip_asset_catalog WHERE taskId=:taskId") fun assetCatalogCount(taskId: String): Int
    @Query("SELECT COUNT(*) FROM p6k_zip_asset_occurrence WHERE taskId=:taskId") fun assetOccurrenceCount(taskId: String): Int
    @Query("SELECT COUNT(*) FROM p6k_zip_asset_occurrence_receipt WHERE taskId=:taskId") fun assetOccurrenceReceiptCount(taskId: String): Int
    @Query("SELECT COUNT(*) FROM p6k_zip_asset_occurrence_receipt WHERE attachmentId=:attachmentId") fun assetOccurrenceReceiptCountForAttachment(attachmentId: String): Int
    @Query("DELETE FROM p6k_zip_asset_occurrence_receipt WHERE taskId=:taskId") fun deleteAssetOccurrenceReceiptsForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_asset_occurrence_receipt WHERE conversationId=:conversationId AND messageId=:messageId AND attachmentId=:attachmentId")
    fun deleteAssetOccurrenceReceiptsForMessageAttachment(conversationId: String, messageId: String, attachmentId: String): Int
    @Query("DELETE FROM p6k_zip_asset_link_receipts WHERE conversationId=:conversationId AND messageId=:messageId AND attachmentId=:attachmentId")
    fun deleteAssetLinkReceiptsForMessageAttachment(conversationId: String, messageId: String, attachmentId: String): Int
    @Query("DELETE FROM p6k_zip_asset_link_provenance WHERE conversationId=:conversationId AND messageId=:messageId AND attachmentId=:attachmentId")
    fun deleteAssetLinkProvenanceForMessageAttachment(conversationId: String, messageId: String, attachmentId: String): Int
    @Query("DELETE FROM p6k_zip_asset_occurrence WHERE taskId=:taskId") fun deleteAssetOccurrencesForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_asset_catalog WHERE taskId=:taskId") fun deleteAssetCatalogForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_asset_link_receipts WHERE taskId=:taskId") fun deleteAssetLinkReceiptsForTask(taskId: String)
    @Query("DELETE FROM p6k_zip_asset_link_provenance WHERE taskId=:taskId") fun deleteAssetLinkProvenanceForTask(taskId: String)
    @Query("UPDATE p6k_zip_import_items SET status=:status, conversationId=:conversationId, failure=:failure WHERE taskId=:taskId AND id=:itemId") fun decideItem(taskId: String, itemId: String, status: String, conversationId: String?, failure: String?): Int
    @Query("UPDATE p6k_zip_import_tasks SET status=:status, updatedAtEpochMs=:updatedAtEpochMs WHERE id=:taskId") fun updateTaskStatus(taskId: String, status: String, updatedAtEpochMs: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertAssetRecoveryJob(job: P6KZipAssetRecoveryJobEntity)
    @Query("SELECT * FROM p6k_zip_asset_recovery_jobs WHERE taskId=:taskId") fun assetRecoveryJob(taskId: String): P6KZipAssetRecoveryJobEntity?
    @Query("SELECT * FROM p6k_zip_asset_recovery_jobs ORDER BY updatedAtMs DESC, taskId ASC") fun assetRecoveryJobs(): List<P6KZipAssetRecoveryJobEntity>
    @Query("SELECT * FROM p6k_zip_asset_recovery_jobs WHERE state IN ('PENDING','INDEXING','MAPPING','LINKING','PARTIAL') ORDER BY updatedAtMs ASC, taskId ASC") fun resumableAssetRecoveryJobs(): List<P6KZipAssetRecoveryJobEntity>
    @Query("DELETE FROM p6k_zip_asset_recovery_jobs WHERE taskId=:taskId") fun deleteAssetRecoveryJob(taskId: String): Int
}

@Dao
interface P6KProfilePersonalizationSettingsDao {
    @Query("SELECT * FROM third_party_profile_personalization_settings WHERE id=1") fun current(): ThirdPartyProfilePersonalizationSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(value: ThirdPartyProfilePersonalizationSettingsEntity)
    @Query("DELETE FROM third_party_profile_personalization_settings WHERE id=1 AND sourceTaskId=:taskId") fun revokeCurrentForTask(taskId: String): Int
    @Query("SELECT * FROM p6k_profile_import_receipts WHERE provider=:provider AND packageHash=:packageHash") fun receipt(provider: String, packageHash: String): P6KProfileImportReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: P6KProfileImportReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProvenance(value: P6KProfileImportProvenanceEntity)
    @Query("DELETE FROM p6k_profile_import_receipts WHERE taskId=:taskId") fun deleteReceiptsForTask(taskId: String)
    @Query("DELETE FROM p6k_profile_import_provenance WHERE taskId=:taskId") fun deleteProvenanceForTask(taskId: String)
}

@Dao
interface PdfTextImportTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: PdfTextImportTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertPages(pages: List<PdfTextImportPageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<PdfTextImportItemEntity>)
    @Query("DELETE FROM pdf_text_import_pages WHERE taskId=:taskId") fun clearPages(taskId: String)
    @Query("DELETE FROM pdf_text_import_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("SELECT * FROM pdf_text_import_tasks WHERE id=:id") fun task(id: String): PdfTextImportTaskEntity?
    @Query("SELECT * FROM pdf_text_import_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<PdfTextImportTaskEntity>
    @Query("SELECT * FROM pdf_text_import_pages WHERE taskId=:taskId ORDER BY pageNumber ASC") fun pages(taskId: String): List<PdfTextImportPageEntity>
    @Query("SELECT * FROM pdf_text_import_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<PdfTextImportItemEntity>
}

@Dao
interface WebTextSnapshotTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTask(task: WebTextSnapshotTaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertItems(items: List<WebTextSnapshotItemEntity>)
    @Query("DELETE FROM web_text_snapshot_items WHERE taskId=:taskId") fun clearItems(taskId: String)
    @Query("SELECT * FROM web_text_snapshot_tasks WHERE id=:id") fun task(id: String): WebTextSnapshotTaskEntity?
    @Query("SELECT * FROM web_text_snapshot_tasks ORDER BY updatedAtEpochMs DESC, id ASC") fun all(): List<WebTextSnapshotTaskEntity>
    @Query("SELECT * FROM web_text_snapshot_items WHERE taskId=:taskId ORDER BY ordinal ASC") fun items(taskId: String): List<WebTextSnapshotItemEntity>
}

@Dao
interface OfflineEvalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRun(run: OfflineEvalRunEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertResults(results: List<OfflineEvalCaseResultEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAssertions(assertions: List<OfflineEvalAssertionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertScore(score: OfflineEvalHumanScoreEntity)
    @Query("SELECT * FROM offline_eval_runs ORDER BY completedAtEpochMs DESC, id ASC") fun runs(): List<OfflineEvalRunEntity>
    @Query("SELECT * FROM offline_eval_case_results WHERE runId=:runId ORDER BY caseId ASC") fun results(runId: String): List<OfflineEvalCaseResultEntity>
    @Query("SELECT * FROM offline_eval_assertions WHERE runId=:runId AND caseId=:caseId ORDER BY assertionId ASC") fun assertions(runId: String, caseId: String): List<OfflineEvalAssertionEntity>
    @Query("SELECT * FROM offline_eval_human_scores WHERE runId=:runId ORDER BY notedAtEpochMs ASC, id ASC") fun scores(runId: String): List<OfflineEvalHumanScoreEntity>
}

@Dao
interface InvocationLedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRecord(record: InvocationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTaskRun(taskRun: InvocationTaskRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAttempts(attempts: List<ProviderAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertGenerations(generations: List<GenerationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertValidations(validations: List<GenerationValidationEntity>)

    @Query("SELECT * FROM invocation_records WHERE id = :id")
    fun findRecord(id: String): InvocationRecordEntity?

    @Query("SELECT * FROM invocation_records ORDER BY completedAtEpochMs DESC, id DESC")
    fun listRecordsNewestFirst(): List<InvocationRecordEntity>

    @Query("SELECT * FROM invocation_task_runs WHERE invocationId = :invocationId")
    fun taskRunFor(invocationId: String): InvocationTaskRunEntity?

    @Query("SELECT * FROM provider_attempts WHERE taskRunId = :taskRunId ORDER BY position")
    fun attemptsFor(taskRunId: String): List<ProviderAttemptEntity>

    @Query("SELECT * FROM generations WHERE attemptId = :attemptId")
    fun generationFor(attemptId: String): GenerationEntity?

    @Query("SELECT * FROM generation_validations WHERE generationId = :generationId")
    fun validationFor(generationId: String): GenerationValidationEntity?

    @Transaction
    fun insertBundle(
        record: InvocationRecordEntity,
        taskRun: InvocationTaskRunEntity,
        attempts: List<ProviderAttemptEntity>,
        generations: List<GenerationEntity>,
        validations: List<GenerationValidationEntity>,
    ) {
        insertRecord(record)
        insertTaskRun(taskRun)
        insertAttempts(attempts)
        insertGenerations(generations)
        insertValidations(validations)
    }
}

@Dao
interface ConversationRealTextExecutionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(value: ConversationRealTextExecutionEntity)

    @Query("SELECT * FROM conversation_real_text_executions WHERE executionId=:executionId")
    fun findById(executionId: String): ConversationRealTextExecutionEntity?

    @Query("SELECT * FROM conversation_real_text_executions WHERE idempotencyKey=:idempotencyKey")
    fun findByIdempotencyKey(idempotencyKey: String): ConversationRealTextExecutionEntity?

    @Query("UPDATE conversation_real_text_executions SET state=:state, updatedAtEpochMs=:updatedAtEpochMs, terminalAtEpochMs=:terminalAtEpochMs, safeErrorCode=:safeErrorCode WHERE executionId=:executionId AND state=:expectedState")
    fun transition(executionId: String, expectedState: String, state: String, updatedAtEpochMs: Long, terminalAtEpochMs: Long?, safeErrorCode: String?): Int
}

@Dao
interface UsageLedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(value: UsageLedgerEntryEntity)

    @Query("SELECT * FROM usage_ledger_entries WHERE entryId=:entryId")
    fun findById(entryId: String): UsageLedgerEntryEntity?

    @Query("SELECT * FROM usage_ledger_entries WHERE replayToken=:replayToken")
    fun findByReplayToken(replayToken: String): UsageLedgerEntryEntity?

    @Query("SELECT * FROM usage_ledger_entries WHERE executionId=:executionId ORDER BY occurredAtEpochMs ASC, entryId ASC")
    fun entriesForExecution(executionId: String): List<UsageLedgerEntryEntity>
}

@Dao
interface LocalExactReuseEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: LocalExactReuseEntryEntity)
    @Query("SELECT * FROM local_exact_reuse_entries WHERE canonicalRequestHash=:canonicalRequestHash") fun find(canonicalRequestHash: String): LocalExactReuseEntryEntity?
    @Query("UPDATE local_exact_reuse_entries SET revoked=1 WHERE canonicalRequestHash=:canonicalRequestHash AND revoked=0") fun revoke(canonicalRequestHash: String): Int
    @Query("DELETE FROM local_exact_reuse_entries WHERE revoked=1 OR expiresAtEpochMs<=:nowEpochMs") fun purge(nowEpochMs: Long): Int
}

@Dao
interface CompareConversationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertSession(value: CompareConversationSessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertBranches(values: List<CompareConversationBranchEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRuntimeStates(values: List<CompareBranchRuntimeStateEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRuntimeEvent(value: CompareBranchRuntimeEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertTerminalIntent(value: CompareBranchTerminalIntentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertFollowUpIntent(value: CompareBranchFollowUpIntentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAdoptionIntent(value: CompareBranchAdoptionIntentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertSynthesisIntent(value: CompareSynthesisIntentEntity)

    @Query("SELECT * FROM compare_conversation_sessions WHERE sessionId=:sessionId") fun session(sessionId: String): CompareConversationSessionEntity?
    @Query("SELECT * FROM compare_conversation_sessions WHERE intentId=:intentId") fun sessionForIntent(intentId: String): CompareConversationSessionEntity?
    @Query("SELECT * FROM compare_conversation_sessions WHERE confirmationId=:confirmationId") fun sessionForConfirmation(confirmationId: String): CompareConversationSessionEntity?
    @Query("SELECT * FROM compare_conversation_branches WHERE sessionId=:sessionId ORDER BY branchId ASC") fun branches(sessionId: String): List<CompareConversationBranchEntity>
    @Query("SELECT * FROM compare_conversation_branches WHERE executionId=:executionId") fun branchForExecution(executionId: String): CompareConversationBranchEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertExecutionReceipt(value: CompareBranchExecutionReceiptEntity)
    @Query("SELECT * FROM compare_branch_execution_receipts WHERE executionId=:executionId") fun executionReceipt(executionId: String): CompareBranchExecutionReceiptEntity?
    @Query("UPDATE compare_branch_execution_receipts SET state=:state, updatedAtEpochMs=:updatedAtEpochMs, terminalAtEpochMs=:terminalAtEpochMs, safeErrorCode=:safeErrorCode WHERE executionId=:executionId AND state=:expectedState")
    fun transitionExecutionReceipt(executionId: String, expectedState: String, state: String, updatedAtEpochMs: Long, terminalAtEpochMs: Long?, safeErrorCode: String?): Int
    @Query("SELECT * FROM compare_branch_runtime_states WHERE sessionId=:sessionId ORDER BY branchId ASC") fun runtimeStates(sessionId: String): List<CompareBranchRuntimeStateEntity>
    @Query("SELECT * FROM compare_branch_runtime_states WHERE sessionId=:sessionId AND branchId=:branchId") fun runtimeState(sessionId: String, branchId: String): CompareBranchRuntimeStateEntity?
    @Query("SELECT * FROM compare_branch_runtime_events WHERE sessionId=:sessionId ORDER BY emittedAtEpochMs ASC, eventId ASC") fun runtimeEvents(sessionId: String): List<CompareBranchRuntimeEventEntity>
    @Query("UPDATE compare_branch_runtime_states SET state=:state, nextExpectedSequence=:nextExpectedSequence, lastCheckpointSequence=:lastCheckpointSequence, safeErrorCode=:safeErrorCode, updatedAtEpochMs=:updatedAtEpochMs WHERE sessionId=:sessionId AND branchId=:branchId")
    fun updateRuntimeState(sessionId: String, branchId: String, state: String, nextExpectedSequence: Long, lastCheckpointSequence: Long?, safeErrorCode: String?, updatedAtEpochMs: Long): Int
    @Query("SELECT * FROM compare_branch_terminal_intents WHERE intentId=:intentId") fun terminalIntent(intentId: String): CompareBranchTerminalIntentEntity?
    @Query("SELECT * FROM compare_branch_follow_up_intents WHERE intentId=:intentId") fun followUpIntent(intentId: String): CompareBranchFollowUpIntentEntity?
    @Query("SELECT * FROM compare_branch_adoption_intents WHERE intentId=:intentId") fun adoptionIntent(intentId: String): CompareBranchAdoptionIntentEntity?
    @Query("SELECT * FROM compare_branch_adoption_intents WHERE sessionId=:sessionId LIMIT 1") fun anyAdoption(sessionId: String): CompareBranchAdoptionIntentEntity?
    @Query("SELECT * FROM compare_synthesis_intents WHERE intentId=:intentId") fun synthesisIntent(intentId: String): CompareSynthesisIntentEntity?
    @Query("SELECT * FROM compare_synthesis_intents WHERE sessionId=:sessionId LIMIT 1") fun anySynthesis(sessionId: String): CompareSynthesisIntentEntity?
}

@Dao
interface GeneratedCandidateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(candidate: GeneratedCandidateEntity)

    @Query("SELECT * FROM generated_candidates WHERE id = :id")
    fun findById(id: String): GeneratedCandidateEntity?

    @Query("SELECT * FROM generated_candidates WHERE status = :status ORDER BY updatedAtEpochMs DESC, id DESC LIMIT 1")
    fun findLatestByStatus(status: String): GeneratedCandidateEntity?

    @Query("UPDATE generated_candidates SET status = :status, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    fun updateStatus(id: String, status: String, updatedAtEpochMs: Long): Int
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, surface = :surface, projectId = :projectId, currentLeafMessageId = :currentLeafMessageId, updatedAtEpochMs = :updatedAtEpochMs, defaultProviderId = :defaultProviderId, defaultModelId = :defaultModelId, harnessId = :harnessId, harnessVersion = :harnessVersion, contextPolicyVersion = :contextPolicyVersion, archivedAtEpochMs = :archivedAtEpochMs, pinnedAtEpochMs = :pinnedAtEpochMs, deletedAtEpochMs = :deletedAtEpochMs, favoritedAtEpochMs = :favoritedAtEpochMs, revision = :revision, autoTitlePending = :autoTitlePending, schemaVersion = :schemaVersion WHERE id = :id")
    fun updateConversation(
        id: String,
        title: String,
        surface: String,
        projectId: String?,
        currentLeafMessageId: String?,
        updatedAtEpochMs: Long,
        defaultProviderId: String?,
        defaultModelId: String?,
        harnessId: String?,
        harnessVersion: Int?,
        contextPolicyVersion: Int,
        archivedAtEpochMs: Long?,
        pinnedAtEpochMs: Long?,
        deletedAtEpochMs: Long?,
        favoritedAtEpochMs: Long?,
        revision: Long,
        autoTitlePending: Boolean,
        schemaVersion: Int,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertNode(node: MessageNodeEntity)

    @Query("UPDATE message_nodes SET parentMessageId=:parentMessageId, siblingPosition=:siblingPosition WHERE id=:messageId AND conversationId=:conversationId")
    fun updateImportedMessageStructure(
        messageId: String,
        conversationId: String,
        parentMessageId: String?,
        siblingPosition: Int,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBlocks(blocks: List<MessageContentBlockEntity>)

    @Query("DELETE FROM message_content_blocks WHERE messageId=:messageId")
    fun deleteBlocks(messageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDraft(draft: ConversationDraftEntity)

    @Query("DELETE FROM conversation_draft_attachments WHERE conversationId = :conversationId")
    fun deleteDraftAttachments(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDraftAttachments(attachments: List<ConversationDraftAttachmentEntity>)

    @Query("DELETE FROM conversation_memory_sources WHERE conversationId = :conversationId")
    fun deleteMemorySources(conversationId: String)

    @Query("DELETE FROM message_content_blocks WHERE messageId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId)")
    fun deleteBlocksForConversation(conversationId: String)

    @Query("DELETE FROM message_nodes WHERE conversationId = :conversationId")
    fun deleteNodesForConversation(conversationId: String)

    @Query("DELETE FROM conversation_drafts WHERE conversationId = :conversationId")
    fun deleteDraft(conversationId: String)

    @Query("DELETE FROM conversation_management_intents WHERE conversationId = :conversationId")
    fun deleteManagementIntentsForConversation(conversationId: String)

    @Query("UPDATE memories SET conversationId = NULL WHERE conversationId = :conversationId")
    fun detachMemoriesForConversation(conversationId: String)

    @Query("DELETE FROM conversation_runtime_states WHERE conversationId = :conversationId")
    fun deleteRuntimeStatesForConversation(conversationId: String)

    @Query("DELETE FROM ai_runtime_events WHERE conversationId = :conversationId")
    fun deleteRuntimeEventsForConversation(conversationId: String)

    @Query("DELETE FROM conversation_attempt_lineages WHERE conversationId = :conversationId")
    fun deleteAttemptLineagesForConversation(conversationId: String)

    @Query("DELETE FROM normal_chat_send_attempts WHERE conversationId = :conversationId")
    fun deleteNormalChatAttemptsForConversation(conversationId: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    fun deleteConversation(conversationId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMemorySources(sources: List<ConversationMemorySourceEntity>)

    @Query("DELETE FROM local_search_index WHERE conversationId = :conversationId")
    fun deleteSearchIndexForConversation(conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSearchIndex(entries: List<LocalSearchIndexEntity>)

    @Query("""
        SELECT s.* FROM local_search_index s
        JOIN conversations c ON c.id = s.conversationId
        WHERE s.normalizedText LIKE '%' || :query || '%' AND c.surface = 'CHAT' AND (
            (:scope = 'ACTIVE' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NULL) OR
            (:scope = 'FAVORITES' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NULL AND c.favoritedAtEpochMs IS NOT NULL) OR
            (:scope = 'ARCHIVED' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NOT NULL) OR
            (:scope = 'DELETED' AND c.deletedAtEpochMs IS NOT NULL) OR
            (:scope = 'ALL' AND c.deletedAtEpochMs IS NULL)
        )
    """)
    fun searchLocalIndex(query: String, scope: String): List<LocalSearchIndexEntity>

    @Query("""
        SELECT s.* FROM local_search_index s
        JOIN conversations c ON c.id = s.conversationId
        WHERE s.messageNodeId IS NOT NULL AND s.contentKind = 'TEXT' AND c.surface = 'CHAT' AND (
            (:scope = 'ACTIVE' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NULL) OR
            (:scope = 'FAVORITES' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NULL AND c.favoritedAtEpochMs IS NOT NULL) OR
            (:scope = 'ARCHIVED' AND c.deletedAtEpochMs IS NULL AND c.archivedAtEpochMs IS NOT NULL) OR
            (:scope = 'DELETED' AND c.deletedAtEpochMs IS NOT NULL) OR
            (:scope = 'ALL' AND c.deletedAtEpochMs IS NULL)
        )
        ORDER BY s.timestampEpochMs DESC, s.conversationId ASC, s.messageNodeId ASC, s.id ASC
    """)
    fun browseLocalTextIndex(scope: String): List<LocalSearchIndexEntity>

    @Query("""
        SELECT c.id AS conversationId,
               c.title AS title,
               COALESCE((
                   SELECT s.snippet FROM local_search_index s
                   WHERE s.conversationId = c.id AND s.messageNodeId IS NOT NULL AND s.contentKind = 'TEXT'
                   ORDER BY s.timestampEpochMs DESC, s.id DESC LIMIT 1
               ), '本地对话') AS snippet,
               c.updatedAtEpochMs AS timestampEpochMs
        FROM conversations c
        WHERE c.surface = 'CHAT' AND c.deletedAtEpochMs IS NULL AND (
            (:scope = 'ACTIVE' AND c.archivedAtEpochMs IS NULL) OR
            (:scope = 'FAVORITES' AND c.archivedAtEpochMs IS NULL AND c.favoritedAtEpochMs IS NOT NULL) OR
            (:scope = 'ARCHIVED' AND c.archivedAtEpochMs IS NOT NULL) OR
            :scope = 'ALL'
        )
        ORDER BY CASE WHEN :scope = 'ACTIVE' AND c.pinnedAtEpochMs IS NULL THEN 1 ELSE 0 END ASC,
                 c.updatedAtEpochMs DESC,
                 c.id ASC
        LIMIT :limit
    """)
    fun browseConversationSearchRows(scope: String, limit: Int): List<ConversationSearchBrowseRow>

    @Query("""
        WITH RECURSIVE current_path(conversationId, messageId) AS (
            SELECT c.id, c.currentLeafMessageId
            FROM conversations c
            WHERE c.surface = 'CHAT' AND c.deletedAtEpochMs IS NULL AND c.currentLeafMessageId IS NOT NULL AND (
                (:scope = 'ACTIVE' AND c.archivedAtEpochMs IS NULL) OR
                (:scope = 'FAVORITES' AND c.archivedAtEpochMs IS NULL AND c.favoritedAtEpochMs IS NOT NULL) OR
                (:scope = 'ARCHIVED' AND c.archivedAtEpochMs IS NOT NULL) OR
                :scope = 'ALL'
            )
            UNION ALL
            SELECT p.conversationId, n.parentMessageId
            FROM current_path p
            JOIN message_nodes n ON n.id = p.messageId
            WHERE n.parentMessageId IS NOT NULL
        )
        SELECT p.conversationId AS conversationId,
               n.id AS messageNodeId,
               c.title AS title,
               GROUP_CONCAT(b.textContent, char(10)) AS text,
               n.createdAtEpochMs AS timestampEpochMs
        FROM current_path p
        JOIN conversations c ON c.id = p.conversationId
        JOIN message_nodes n ON n.id = p.messageId
        JOIN message_content_blocks b ON b.messageId = n.id AND b.kind = 'TEXT' AND b.textContent IS NOT NULL
        GROUP BY p.conversationId, n.id, c.title, n.createdAtEpochMs
        HAVING LOWER(GROUP_CONCAT(b.textContent, char(10))) LIKE '%' || :query || '%'
        ORDER BY n.createdAtEpochMs DESC, n.id ASC
        LIMIT :limit
    """)
    fun searchCurrentPathTextRows(query: String, scope: String, limit: Int): List<ConversationPathTextSearchRow>

    @Query("""
        WITH RECURSIVE current_path(conversationId, messageId) AS (
            SELECT c.id, c.currentLeafMessageId
            FROM conversations c
            WHERE c.surface = 'CHAT' AND c.deletedAtEpochMs IS NULL AND c.currentLeafMessageId IS NOT NULL AND (
                (:scope = 'ACTIVE' AND c.archivedAtEpochMs IS NULL) OR
                (:scope = 'FAVORITES' AND c.archivedAtEpochMs IS NULL AND c.favoritedAtEpochMs IS NOT NULL) OR
                (:scope = 'ARCHIVED' AND c.archivedAtEpochMs IS NOT NULL) OR
                :scope = 'ALL'
            )
            UNION ALL
            SELECT p.conversationId, n.parentMessageId
            FROM current_path p
            JOIN message_nodes n ON n.id = p.messageId
            WHERE n.parentMessageId IS NOT NULL
        )
        SELECT p.conversationId AS conversationId,
               n.id AS messageNodeId,
               c.title AS title,
               n.createdAtEpochMs AS createdAtEpochMs,
               b.attachmentId AS attachmentId,
               b.storageKey AS storageKey,
               b.mimeType AS mimeType,
               b.displayName AS displayName,
               b.byteCount AS byteCount,
               b.sha256 AS sha256,
               COALESCE((
                   SELECT GROUP_CONCAT(t.textContent, char(10))
                   FROM message_content_blocks t
                   WHERE t.messageId = n.id AND t.kind = 'TEXT' AND t.textContent IS NOT NULL
               ), '') AS messageText
        FROM current_path p
        JOIN conversations c ON c.id = p.conversationId
        JOIN message_nodes n ON n.id = p.messageId
        JOIN message_content_blocks b ON b.messageId = n.id AND b.kind = 'ATTACHMENT'
        WHERE :query = '' OR LOWER(c.title) LIKE '%' || :query || '%'
            OR LOWER(COALESCE(b.displayName, '')) LIKE '%' || :query || '%'
            OR EXISTS(
                SELECT 1 FROM message_content_blocks t
                WHERE t.messageId = n.id AND t.kind = 'TEXT' AND t.textContent IS NOT NULL
                  AND LOWER(t.textContent) LIKE '%' || :query || '%'
            )
        ORDER BY n.createdAtEpochMs DESC, n.id ASC, b.position ASC
    """)
    fun currentPathAttachmentRows(query: String, scope: String): List<ConversationPathAttachmentRow>

    @Query("SELECT COUNT(*) FROM local_search_index")
    fun searchIndexCount(): Int

    /** Finds old partial indexes without reading any message body into the caller. */
    @Query("""
        SELECT c.id FROM conversations c
        WHERE NOT EXISTS (
            SELECT 1 FROM local_search_index title
            WHERE title.id = c.id || ':title'
        ) OR (
            SELECT COUNT(*)
            FROM message_nodes n
            JOIN message_content_blocks b ON b.messageId = n.id
            WHERE n.conversationId = c.id
              AND n.role IN ('USER', 'ASSISTANT')
              AND b.kind = 'TEXT'
              AND b.textContent IS NOT NULL
              AND TRIM(b.textContent) != ''
        ) != (
            SELECT COUNT(*) FROM local_search_index s
            WHERE s.conversationId = c.id
              AND s.messageNodeId IS NOT NULL
              AND s.contentKind = 'TEXT'
        )
        ORDER BY c.updatedAtEpochMs DESC, c.id ASC
    """)
    fun conversationIdsWithIncompleteTextSearchIndex(): List<String>

    @Query("UPDATE message_nodes SET deliveryState = :deliveryState, lastPersistedSequence = :lastPersistedSequence, resumableFromSequence = :resumableFromSequence WHERE id = :id")
    fun updateRuntimeNode(id: String, deliveryState: String, lastPersistedSequence: Long?, resumableFromSequence: Long?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRuntimeEvent(event: AiRuntimeEventEntity)

    @Query("SELECT * FROM ai_runtime_events WHERE eventId = :eventId")
    fun runtimeEventById(eventId: String): AiRuntimeEventEntity?

    @Query("SELECT * FROM ai_runtime_events WHERE invocationId = :invocationId AND sequence = :sequence")
    fun runtimeEventAt(invocationId: String, sequence: Long): AiRuntimeEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRuntimeState(state: ConversationRuntimeStateEntity)

    @Query("SELECT * FROM conversation_runtime_states WHERE conversationId = :conversationId")
    fun runtimeStateForConversation(conversationId: String): ConversationRuntimeStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAttemptLineage(lineage: ConversationAttemptLineageEntity)

    @Query("SELECT * FROM conversation_attempt_lineages WHERE intentId = :intentId")
    fun attemptLineageForIntent(intentId: String): ConversationAttemptLineageEntity?

    @Query("SELECT * FROM conversation_attempt_lineages WHERE invocationId = :invocationId")
    fun attemptLineageForInvocation(invocationId: String): ConversationAttemptLineageEntity?

    @Query("SELECT * FROM conversation_attempt_lineages WHERE conversationId = :conversationId ORDER BY createdAtEpochMs DESC, invocationId ASC")
    fun attemptLineagesForConversation(conversationId: String): List<ConversationAttemptLineageEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun findConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NULL ORDER BY CASE WHEN pinnedAtEpochMs IS NULL THEN 1 ELSE 0 END, pinnedAtEpochMs DESC, updatedAtEpochMs DESC, id DESC")
    fun listActiveConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NULL ORDER BY CASE WHEN pinnedAtEpochMs IS NULL THEN 1 ELSE 0 END, updatedAtEpochMs DESC, id ASC")
    fun listVisibleActiveConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NOT NULL ORDER BY updatedAtEpochMs DESC, id ASC")
    fun listArchivedConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NULL AND favoritedAtEpochMs IS NOT NULL ORDER BY favoritedAtEpochMs DESC, id ASC")
    fun listFavoriteConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NOT NULL ORDER BY updatedAtEpochMs DESC, id ASC")
    fun listDeletedConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'CHAT' AND deletedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC, id ASC")
    fun listAllNonDeletedConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE surface = 'WORK' AND deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NULL ORDER BY updatedAtEpochMs DESC, id ASC")
    fun listActiveWorkConversations(): List<ConversationEntity>

    /** P7-E reads typed conversation truth for semantic mapping; it never serializes Room rows. */
    @Query("SELECT * FROM conversations ORDER BY id ASC")
    fun listAllForP7ESemanticSnapshot(): List<ConversationEntity>

    @Query("SELECT * FROM conversation_management_intents WHERE intentId = :intentId")
    fun managementIntent(intentId: String): ConversationManagementIntentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertManagementIntent(intent: ConversationManagementIntentEntity)

    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId ORDER BY parentMessageId, siblingPosition")
    fun nodesFor(conversationId: String): List<MessageNodeEntity>

    @Query("SELECT * FROM message_nodes WHERE id = :id")
    fun findNode(id: String): MessageNodeEntity?

    @Query("SELECT * FROM message_content_blocks WHERE messageId = :messageId ORDER BY position")
    fun blocksFor(messageId: String): List<MessageContentBlockEntity>

    @Query("SELECT * FROM message_content_blocks WHERE messageId IN (SELECT id FROM message_nodes WHERE conversationId = :conversationId) ORDER BY messageId, position")
    fun blocksForConversation(conversationId: String): List<MessageContentBlockEntity>

    @Query("SELECT * FROM conversation_drafts WHERE conversationId = :conversationId")
    fun draftFor(conversationId: String): ConversationDraftEntity?

    @Query("SELECT * FROM conversation_draft_attachments WHERE conversationId = :conversationId ORDER BY position")
    fun draftAttachmentsFor(conversationId: String): List<ConversationDraftAttachmentEntity>

    @Query("SELECT * FROM conversation_memory_sources WHERE conversationId = :conversationId ORDER BY position")
    fun memorySourcesFor(conversationId: String): List<ConversationMemorySourceEntity>

    @Query("SELECT * FROM conversation_memory_sources WHERE conversationId IN (:conversationIds) ORDER BY conversationId, position")
    fun memorySourcesForConversations(conversationIds: List<String>): List<ConversationMemorySourceEntity>
}

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProject(project: ProjectEntity)
    @Query("UPDATE projects SET title=:title, description=:description, colorSemantic=:colorSemantic, iconSemantic=:iconSemantic, updatedAtEpochMs=:updatedAtEpochMs, archivedAtEpochMs=:archivedAtEpochMs, pinnedAtEpochMs=:pinnedAtEpochMs, deletedAtEpochMs=:deletedAtEpochMs, schemaVersion=:schemaVersion WHERE id=:id")
    fun updateProject(id: String, title: String, description: String, colorSemantic: String?, iconSemantic: String?, updatedAtEpochMs: Long, archivedAtEpochMs: Long?, pinnedAtEpochMs: Long?, deletedAtEpochMs: Long?, schemaVersion: Int): Int
    @Query("SELECT * FROM projects WHERE id=:id") fun findProject(id: String): ProjectEntity?
    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NULL ORDER BY CASE WHEN pinnedAtEpochMs IS NULL THEN 1 ELSE 0 END, updatedAtEpochMs DESC, id ASC") fun listActive(): List<ProjectEntity>
    @Query("SELECT * FROM projects WHERE deletedAtEpochMs IS NULL AND archivedAtEpochMs IS NOT NULL ORDER BY updatedAtEpochMs DESC, id ASC") fun listArchived(): List<ProjectEntity>
    /** P7-E includes archived/deleted state explicitly instead of silently omitting it. */
    @Query("SELECT * FROM projects ORDER BY id ASC") fun listAllForP7ESemanticSnapshot(): List<ProjectEntity>
    @Query("SELECT * FROM project_instruction_revisions WHERE projectId=:projectId ORDER BY revision ASC") fun revisionsFor(projectId: String): List<ProjectInstructionRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRevision(revision: ProjectInstructionRevisionEntity)
    @Query("SELECT * FROM project_intents WHERE intentId=:intentId") fun intent(intentId: String): ProjectIntentEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertIntent(intent: ProjectIntentEntity)
    @Query("UPDATE conversations SET projectId=:projectId, updatedAtEpochMs=:updatedAtEpochMs WHERE id=:conversationId") fun setConversationProject(conversationId: String, projectId: String?, updatedAtEpochMs: Long): Int
    @Query("SELECT projectId FROM conversations WHERE id=:conversationId") fun projectForConversation(conversationId: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertKnowledgeScope(scope: KnowledgeProjectScopeEntity)
    @Query("SELECT projectId FROM knowledge_project_scopes WHERE knowledgeId=:knowledgeId") fun scopeForKnowledge(knowledgeId: String): String?
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertMemory(memory: MemoryEntity)
    @Query("UPDATE memories SET title=:title, body=:body, scopeKind=:scopeKind, projectId=:projectId, conversationId=:conversationId, scopeKey=:scopeKey, source=:source, sourceStableId=:sourceStableId, sourceSummary=:sourceSummary, status=:status, contentHash=:contentHash, conceptHash=:conceptHash, updatedAtEpochMs=:updatedAtEpochMs, lastConfirmedAtEpochMs=:lastConfirmedAtEpochMs, deletedAtEpochMs=:deletedAtEpochMs, schemaVersion=:schemaVersion WHERE id=:id")
    fun updateMemory(id: String, title: String, body: String, scopeKind: String, projectId: String?, conversationId: String?, scopeKey: String, source: String, sourceStableId: String, sourceSummary: String, status: String, contentHash: String, conceptHash: String, updatedAtEpochMs: Long, lastConfirmedAtEpochMs: Long, deletedAtEpochMs: Long?, schemaVersion: Int): Int
    @Query("SELECT * FROM memories WHERE id=:id") fun findMemory(id: String): MemoryEntity?
    @Query("SELECT * FROM memories WHERE (:scopeKind IS NULL OR scopeKind=:scopeKind) AND (:status IS NULL OR status=:status) ORDER BY updatedAtEpochMs DESC, id ASC") fun listMemories(scopeKind: String?, status: String?): List<MemoryEntity>
    @Query("SELECT * FROM memories WHERE scopeKey=:scopeKey AND contentHash=:contentHash AND status!='DELETED' LIMIT 1") fun findLiveByContent(scopeKey: String, contentHash: String): MemoryEntity?
    @Query("SELECT * FROM memories WHERE scopeKey=:scopeKey AND conceptHash=:conceptHash AND status!='DELETED' ORDER BY updatedAtEpochMs DESC, id ASC LIMIT 1") fun findLiveByConcept(scopeKey: String, conceptHash: String): MemoryEntity?
    @Query("SELECT * FROM memory_revisions WHERE memoryId=:memoryId ORDER BY revision ASC") fun revisionsFor(memoryId: String): List<MemoryRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRevision(revision: MemoryRevisionEntity)
    @Query("SELECT * FROM memory_intents WHERE intentId=:intentId") fun intent(intentId: String): MemoryIntentEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertIntent(intent: MemoryIntentEntity)
    @Query("SELECT * FROM memory_conflicts WHERE id=:id") fun conflict(id: String): MemoryConflictEntity?
    @Query("SELECT * FROM memory_conflicts WHERE intentId=:intentId LIMIT 1") fun conflictForIntent(intentId: String): MemoryConflictEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertConflict(conflict: MemoryConflictEntity)
    @Query("UPDATE memory_conflicts SET status=:status, resolvedAtEpochMs=:resolvedAtEpochMs WHERE id=:id") fun resolveConflict(id: String, status: String, resolvedAtEpochMs: Long): Int
}

@Dao
interface PrivateAttachmentAssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(asset: PrivateAttachmentAssetEntity)

    @Query("SELECT * FROM private_attachment_assets WHERE attachmentId = :attachmentId")
    fun findById(attachmentId: String): PrivateAttachmentAssetEntity?

    @Query("SELECT * FROM private_attachment_assets WHERE sha256 = :sha256 LIMIT 1")
    fun findBySha256(sha256: String): PrivateAttachmentAssetEntity?

    @Query("SELECT COUNT(*) FROM private_attachment_assets") fun assetCount(): Int

    @Query("SELECT * FROM private_attachment_assets WHERE storageKey LIKE 'p6k-zip-assets/v1/%' ORDER BY attachmentId ASC")
    fun archiveBackedAssets(): List<PrivateAttachmentAssetEntity>

    @Query("UPDATE private_attachment_assets SET storageKey = :managedStorageKey WHERE attachmentId = :attachmentId AND storageKey = :archiveStorageKey")
    fun materializeArchiveBackedAsset(attachmentId: String, archiveStorageKey: String, managedStorageKey: String): Int

    @Query("DELETE FROM private_attachment_assets WHERE attachmentId = :attachmentId")
    fun deleteById(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM conversation_draft_attachments WHERE attachmentId = :attachmentId")
    fun normalDraftReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM capture_draft_attachments WHERE attachmentId = :attachmentId")
    fun captureDraftReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM knowledge_attachments WHERE attachmentId = :attachmentId")
    fun knowledgeReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM message_content_blocks WHERE attachmentId = :attachmentId")
    fun normalMessageReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM (SELECT r.conversationId FROM p6k_zip_asset_occurrence_receipt r INNER JOIN p6k_zip_import_tasks t ON t.id=r.taskId WHERE r.attachmentId=:attachmentId AND t.status NOT IN ('COMPLETED','FAILED','CANCELLED') UNION ALL SELECT p.conversationId FROM p6k_zip_asset_link_provenance p INNER JOIN p6k_zip_import_tasks t ON t.id=p.taskId WHERE p.attachmentId=:attachmentId AND t.status NOT IN ('COMPLETED','FAILED','CANCELLED'))")
    fun zipOccurrenceReceiptReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM temporary_conversation_attachments WHERE attachmentId = :attachmentId")
    fun temporaryConversationReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM resumable_attachment_uploads WHERE attachmentId = :attachmentId AND status IN ('PENDING','UPLOADING','FAILED','UNKNOWN')")
    fun resumableUploadReferences(attachmentId: String): Int

    @Query("SELECT COUNT(*) FROM glm_ocr_tasks WHERE sourceAttachmentId = :attachmentId OR resultAttachmentId = :attachmentId")
    fun glmOcrTaskReferences(attachmentId: String): Int
}

@Dao
interface GlmOcrTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(task: GlmOcrTaskEntity)
    @Query("SELECT * FROM glm_ocr_tasks WHERE taskId=:taskId") fun find(taskId: String): GlmOcrTaskEntity?
    @Query("SELECT * FROM glm_ocr_tasks ORDER BY createdAtEpochMs DESC, taskId DESC") fun listNewestFirst(): List<GlmOcrTaskEntity>
    @Query("DELETE FROM glm_ocr_tasks WHERE taskId=:taskId") fun delete(taskId: String): Int
}

@Dao
interface TemporaryConversationRecoveryDao {
    @Query("SELECT * FROM temporary_conversation_recovery LIMIT 1") fun active(): TemporaryConversationRecoveryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsert(record: TemporaryConversationRecoveryEntity)
    @Query("SELECT * FROM temporary_conversation_messages WHERE temporaryId=:temporaryId ORDER BY position") fun messages(temporaryId: String): List<TemporaryConversationMessageEntity>
    @Query("SELECT * FROM temporary_conversation_attachments WHERE temporaryId=:temporaryId ORDER BY ownerKind, ownerId, position") fun attachments(temporaryId: String): List<TemporaryConversationAttachmentEntity>
    @Query("DELETE FROM temporary_conversation_messages WHERE temporaryId=:temporaryId") fun deleteMessages(temporaryId: String)
    @Query("DELETE FROM temporary_conversation_attachments WHERE temporaryId=:temporaryId") fun deleteAttachments(temporaryId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertMessages(values: List<TemporaryConversationMessageEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertAttachments(values: List<TemporaryConversationAttachmentEntity>)
    @Query("DELETE FROM temporary_conversation_recovery WHERE temporaryId=:temporaryId") fun deleteRecord(temporaryId: String): Int
}

/** P7-B stores only opaque account refs, encrypted-key references and state; never tokens, recovery code or data key. */
@Entity(tableName = "sync_account_metadata")
data class SyncAccountMetadataEntity(
    @androidx.room.PrimaryKey val accountRef: String,
    val state: String,
    val revision: Long,
    val keyAliasRef: String?,
    val wrappedKeyRef: String?,
    val wrappedKeySha256: String?,
    val directionFact: String?,
    val lastError: String?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "sync_intents", indices = [Index("accountRef")])
data class SyncIntentEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val accountRef: String,
    val expectedRevision: Long?,
    val resultingRevision: Long,
    val resultingState: String,
    val createdAtEpochMs: Long,
)

@Dao
interface SyncAccountMetadataDao {
    @Query("SELECT * FROM sync_account_metadata WHERE accountRef = :accountRef") fun find(accountRef: String): SyncAccountMetadataEntity?
    @Query("SELECT * FROM sync_intents WHERE intentId = :intentId") fun receipt(intentId: String): SyncIntentEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(metadata: SyncAccountMetadataEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun saveReceipt(receipt: SyncIntentEntity)
}

/** P7-D ledger: no envelope bytes, business text, paths, credentials or session material. */
@Entity(tableName = "sync_jobs")
data class SyncJobEntity(
    @androidx.room.PrimaryKey val accountRef: String,
    val generation: Long,
    val completedGeneration: Long,
    val stage: String,
    val lastLocalRevision: Long?,
    val lastLocalHash: String?,
    val lastRemoteRevision: Long?,
    val lastRemoteHash: String?,
    val stagingRef: String?,
    val stagingHash: String?,
    val stagingBytes: Long?,
    val lastErrorCode: String?,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "sync_job_receipts", indices = [Index("accountRef")])
data class SyncJobReceiptEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val accountRef: String,
    val generation: Long,
    val stage: String,
    val createdAtEpochMs: Long,
)

@Dao
interface SyncJobDao {
    @Query("SELECT * FROM sync_jobs WHERE accountRef = :accountRef") fun job(accountRef: String): SyncJobEntity?
    @Query("SELECT * FROM sync_job_receipts WHERE intentId = :intentId") fun receipt(intentId: String): SyncJobReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun save(job: SyncJobEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun saveReceipt(receipt: SyncJobReceiptEntity)
}

/** Manual-only per-conversation cloud receipt. It contains no title, message text or token. */
@Entity(
    tableName = "manual_conversation_sync_state",
    primaryKeys = ["accountRef", "conversationId"],
    indices = [Index("conversationId"), Index("lastSyncedAtEpochMs")],
)
data class ManualConversationSyncStateEntity(
    val accountRef: String,
    val conversationId: String,
    val documentId: String,
    val remoteRevision: Long,
    val payloadHash: String,
    val localContentHash: String,
    val lastSyncedAtEpochMs: Long,
)

@Dao
interface ManualConversationSyncStateDao {
    @Query("SELECT * FROM manual_conversation_sync_state WHERE accountRef=:accountRef AND conversationId=:conversationId")
    fun find(accountRef: String, conversationId: String): ManualConversationSyncStateEntity?

    @Query("SELECT * FROM manual_conversation_sync_state WHERE accountRef=:accountRef ORDER BY lastSyncedAtEpochMs DESC")
    fun listForAccount(accountRef: String): List<ManualConversationSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(state: ManualConversationSyncStateEntity)
}

/** P8-A durable facts deliberately exclude tool input/output bodies, credentials, paths and URIs. */
@Entity(tableName = "agent_runs", indices = [Index(value = ["idempotencyKey"], unique = true), Index(value = ["status", "updatedAtEpochMs"])])
data class AgentRunEntity(
    @androidx.room.PrimaryKey val id: String,
    val idempotencyKey: String,
    val toolSchemaVersion: Int,
    val status: String,
    val riskCeiling: String,
    val permissionGrant: String,
    val maxSteps: Long,
    val maxToolCalls: Long,
    val maxSideEffects: Long,
    val usedSteps: Long,
    val usedToolCalls: Long,
    val usedSideEffects: Long,
    val lastCheckpointSequence: Long?,
    val safeErrorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "agent_steps", primaryKeys = ["runId", "sequence"], indices = [Index(value = ["runId", "idempotencyKey"], unique = true)])
data class AgentStepEntity(
    val runId: String,
    val sequence: Long,
    val idempotencyKey: String,
    val toolId: String,
    val inputHash: String,
    val status: String,
    val resultHash: String?,
    val safeErrorCode: String?,
    val sideEffectClass: String,
    val riskLevel: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_events", primaryKeys = ["runId", "sequence"], indices = [Index(value = ["runId", "eventId"], unique = true)])
data class AgentEventEntity(
    val runId: String,
    val sequence: Long,
    val eventId: String,
    val kind: String,
    val fingerprint: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_checkpoints", primaryKeys = ["runId", "sequence"])
data class AgentCheckpointEntity(
    val runId: String,
    val sequence: Long,
    val status: String,
    val nextStepSequence: Long,
    val safeStateHash: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "agent_side_effect_receipts", indices = [Index("runId")])
data class AgentSideEffectReceiptEntity(
    @androidx.room.PrimaryKey val idempotencyKey: String,
    val runId: String,
    val stepSequence: Long,
    val toolId: String,
    val sideEffectClass: String,
    val outcome: String,
    val resultHash: String,
    val rollbackAvailable: Boolean,
    val createdAtEpochMs: Long,
)

/** P9-B stores only opaque contract and audit metadata; it never stores target content or access handles. */
@Entity(tableName = "p9b_integration_sessions", indices = [Index(value = ["idempotencyKey"], unique = true), Index(value = ["state"])])
data class P9BIntegrationSessionEntity(
    @androidx.room.PrimaryKey val requestId: String,
    val idempotencyKey: String,
    val appHandle: String,
    val subjectHandle: String,
    val state: String,
    val capability: String,
    val permission: String,
    val classification: String,
    val provenanceSource: String,
    val sourceRevision: Long,
    val sourceHash: String,
    val pageLimit: Int,
    val pageCursor: String?,
    val expiresAtEpochMs: Long?,
    val previewRevision: Long?,
    val previewHash: String?,
    val previewItemCount: Int?,
    val previewNextCursor: String?,
    val resultHash: String?,
    val errorCode: String?,
)

@Entity(tableName = "p9b_integration_events", primaryKeys = ["requestId", "sequence"])
data class P9BIntegrationEventEntity(val requestId: String, val sequence: Long, val kind: String, val fingerprint: String)

@Entity(tableName = "p9b_integration_receipts")
data class P9BIntegrationReceiptEntity(@androidx.room.PrimaryKey val idempotencyKey: String, val requestId: String, val resultHash: String)

@Dao
interface P9BIntegrationLedgerDao {
    @Query("SELECT * FROM p9b_integration_sessions WHERE requestId = :requestId") fun session(requestId: String): P9BIntegrationSessionEntity?
    @Query("SELECT * FROM p9b_integration_sessions WHERE idempotencyKey = :key") fun sessionByIdempotency(key: String): P9BIntegrationSessionEntity?
    @Query("SELECT * FROM p9b_integration_events WHERE requestId = :requestId ORDER BY sequence ASC") fun events(requestId: String): List<P9BIntegrationEventEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertSession(value: P9BIntegrationSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun updateSession(value: P9BIntegrationSessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertEvent(value: P9BIntegrationEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: P9BIntegrationReceiptEntity)
}

@Dao
interface AgentLedgerDao {
    @Query("SELECT * FROM agent_runs WHERE id = :runId") fun run(runId: String): AgentRunEntity?
    @Query("SELECT * FROM agent_runs WHERE idempotencyKey = :key") fun runByKey(key: String): AgentRunEntity?
    @Query("SELECT * FROM agent_runs ORDER BY createdAtEpochMs DESC, id ASC") fun runs(): List<AgentRunEntity>
    @Query("SELECT * FROM agent_side_effect_receipts WHERE idempotencyKey = :key") fun receipt(key: String): AgentSideEffectReceiptEntity?
    @Query("SELECT * FROM agent_events WHERE runId = :runId AND eventId = :eventId") fun event(runId: String, eventId: String): AgentEventEntity?
    @Query("SELECT * FROM agent_steps WHERE runId = :runId ORDER BY sequence ASC") fun steps(runId: String): List<AgentStepEntity>
    @Query("SELECT * FROM agent_events WHERE runId = :runId ORDER BY sequence ASC") fun events(runId: String): List<AgentEventEntity>
    @Query("SELECT * FROM agent_checkpoints WHERE runId = :runId ORDER BY sequence ASC") fun checkpoints(runId: String): List<AgentCheckpointEntity>
    @Query("SELECT COUNT(*) FROM agent_runs") fun runCount(): Long
    @Query("SELECT COUNT(*) FROM agent_steps") fun stepCount(): Long
    @Query("SELECT COUNT(*) FROM agent_events") fun eventCount(): Long
    @Query("SELECT COUNT(*) FROM agent_checkpoints") fun checkpointCount(): Long
    @Query("SELECT COUNT(*) FROM agent_side_effect_receipts") fun receiptCount(): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertRun(value: AgentRunEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertStep(value: AgentStepEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertEvent(value: AgentEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertCheckpoint(value: AgentCheckpointEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: AgentSideEffectReceiptEntity)
    @Query("UPDATE agent_runs SET status=:status, usedSteps=:usedSteps, usedToolCalls=:usedToolCalls, usedSideEffects=:usedSideEffects, lastCheckpointSequence=:checkpoint, safeErrorCode=:error, updatedAtEpochMs=:updatedAt WHERE id=:runId")
    fun updateRun(runId: String, status: String, usedSteps: Long, usedToolCalls: Long, usedSideEffects: Long, checkpoint: Long?, error: String?, updatedAt: Long): Int
}

/** P6 v2 restore receipt/provenance are content-free; workspace bodies remain in their owners. */
@Entity(tableName = "workspace_exchange_v2_restore_receipts")
data class WorkspaceExchangeV2RestoreReceiptEntity(
    @androidx.room.PrimaryKey val intentId: String,
    val packageHash: String,
    val semanticHash: String,
    val origin: String,
    val sensitivity: String,
    val projectCount: Int,
    val conversationCount: Int,
    val knowledgeCount: Int,
    val memoryCount: Int,
    val relationCount: Int,
    val assetCount: Int,
    val assetBytes: Long,
    val importedAtEpochMs: Long,
    val importerVersion: Int,
)

@Entity(tableName = "workspace_exchange_v2_restore_provenance", primaryKeys = ["ownerKind", "ownerId"])
data class WorkspaceExchangeV2RestoreProvenanceEntity(
    val ownerKind: String,
    val ownerId: String,
    val packageHash: String,
    val semanticHash: String,
    val ownerFieldHash: String,
    val importedAtEpochMs: Long,
)

@Entity(tableName = "workspace_exchange_v2_restore_settings")
data class WorkspaceExchangeV2RestoreSettingsEntity(
    @androidx.room.PrimaryKey val id: String = "root",
    val uiLanguage: String,
    val theme: String,
    val packageHash: String,
    val updatedAtEpochMs: Long,
)

@Dao
interface WorkspaceExchangeV2RestoreDao {
    @Query("SELECT * FROM workspace_exchange_v2_restore_receipts WHERE intentId=:intentId") fun receipt(intentId: String): WorkspaceExchangeV2RestoreReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertReceipt(value: WorkspaceExchangeV2RestoreReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertProvenance(values: List<WorkspaceExchangeV2RestoreProvenanceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insertSettings(value: WorkspaceExchangeV2RestoreSettingsEntity)
    @Query("SELECT COUNT(*) FROM workspace_exchange_v2_restore_receipts") fun receiptCount(): Int
    @Query("SELECT COUNT(*) FROM workspace_exchange_v2_restore_provenance") fun provenanceCount(): Int
    @Query("SELECT * FROM workspace_exchange_v2_restore_provenance ORDER BY ownerKind, ownerId") fun provenance(): List<WorkspaceExchangeV2RestoreProvenanceEntity>
    @Query("SELECT COUNT(*) FROM workspace_exchange_v2_restore_settings") fun settingsCount(): Int
}

@Dao
interface ProviderDiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: ProviderDiagnosticEntity)

    @Query("DELETE FROM debug_call_log WHERE createdAtEpochMs < :cutoffEpochMs")
    fun deleteBefore(cutoffEpochMs: Long): Int

    @Query("SELECT * FROM debug_call_log ORDER BY createdAtEpochMs DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): List<ProviderDiagnosticEntity>

    /** Keep the newest rows without ever reading a body outside this isolated DAO. */
    @Query("DELETE FROM debug_call_log WHERE id IN (SELECT id FROM debug_call_log ORDER BY createdAtEpochMs DESC, id DESC LIMIT -1 OFFSET :keep)")
    fun trimTo(keep: Int): Int
}

@Dao
interface NormalChatSendAttemptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: NormalChatSendAttemptEntity)
    @Query("SELECT * FROM normal_chat_send_attempts WHERE attemptId=:attemptId") fun find(attemptId: String): NormalChatSendAttemptEntity?
    @Query("SELECT * FROM normal_chat_send_attempts WHERE conversationId=:conversationId ORDER BY createdAtEpochMs DESC LIMIT 1") fun latestForConversation(conversationId: String): NormalChatSendAttemptEntity?
    @Query("SELECT attemptId, createdAtEpochMs, updatedAtEpochMs FROM normal_chat_send_attempts WHERE status='COMPLETED' AND attemptId IN (:attemptIds)") fun completedDurations(attemptIds: List<String>): List<CompletedNormalChatAttemptDuration>
    @Query("UPDATE normal_chat_send_attempts SET status=:next, updatedAtEpochMs=:updatedAtEpochMs, safeErrorCode=:safeErrorCode WHERE attemptId=:attemptId AND status IN (:expected)") fun transition(attemptId: String, expected: List<String>, next: String, updatedAtEpochMs: Long, safeErrorCode: String?): Int
    @Query("UPDATE normal_chat_send_attempts SET status='UNKNOWN', updatedAtEpochMs=:updatedAtEpochMs, safeErrorCode='PROCESS_INTERRUPTED' WHERE status IN ('PENDING','SENDING','ACCEPTED','STREAMING')") fun markInterruptedAsUnknown(updatedAtEpochMs: Long): Int
}

@Dao
interface AssistantResponseModelAttributionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: AssistantResponseModelAttributionEntity)
    @Query("SELECT * FROM assistant_response_model_attributions WHERE assistantMessageId=:assistantMessageId AND attemptId=:attemptId") fun find(assistantMessageId: String, attemptId: String): AssistantResponseModelAttributionEntity?
    @Query("SELECT * FROM assistant_response_model_attributions WHERE assistantMessageId IN (:assistantMessageIds) ORDER BY recordedAtEpochMs ASC, attemptId ASC") fun forMessages(assistantMessageIds: List<String>): List<AssistantResponseModelAttributionEntity>
    @Query("SELECT * FROM assistant_response_model_attributions WHERE inputTokens IS NOT NULL OR outputTokens IS NOT NULL OR costTotalMicros IS NOT NULL ORDER BY recordedAtEpochMs DESC, attemptId DESC") fun listCostedNewestFirst(): List<AssistantResponseModelAttributionEntity>
    @Query("UPDATE assistant_response_model_attributions SET inputTokens=:inputTokens, outputTokens=:outputTokens, totalTokens=:totalTokens, cachedInputTokens=:cachedInputTokens, reasoningTokens=:reasoningTokens, costPriceVersion=:costPriceVersion, costCurrencyCode=:costCurrencyCode, costTotalMicros=:costTotalMicros, costSource=:costSource WHERE assistantMessageId=:assistantMessageId AND attemptId=:attemptId") fun enrichAccounting(assistantMessageId: String, attemptId: String, inputTokens: Long?, outputTokens: Long?, totalTokens: Long?, cachedInputTokens: Long?, reasoningTokens: Long?, costPriceVersion: String?, costCurrencyCode: String?, costTotalMicros: Long?, costSource: String?): Int
}

@Dao
interface ResumableAttachmentUploadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: ResumableAttachmentUploadEntity)
    @Query("SELECT * FROM resumable_attachment_uploads WHERE uploadId=:uploadId") fun find(uploadId: String): ResumableAttachmentUploadEntity?
    @Query("SELECT * FROM resumable_attachment_uploads WHERE normalChatAttemptId=:attemptId AND attachmentId=:attachmentId") fun findForAttemptAndAttachment(attemptId: String, attachmentId: String): ResumableAttachmentUploadEntity?
    @Query("UPDATE resumable_attachment_uploads SET status=:next, gatewaySessionId=:gatewaySessionId, acknowledgedBytes=:acknowledgedBytes, updatedAtEpochMs=:updatedAtEpochMs, safeErrorCode=:safeErrorCode WHERE uploadId=:uploadId AND status IN (:expected)") fun transition(uploadId: String, expected: List<String>, next: String, gatewaySessionId: String?, acknowledgedBytes: Long, updatedAtEpochMs: Long, safeErrorCode: String?): Int
    @Query("UPDATE resumable_attachment_uploads SET status='UNKNOWN', updatedAtEpochMs=:updatedAtEpochMs, safeErrorCode='PROCESS_INTERRUPTED' WHERE status IN ('PENDING','UPLOADING')") fun markInterruptedAsUnknown(updatedAtEpochMs: Long): Int
}

@Database(
    entities = [
        CaptureDraftEntity::class,
        CaptureDraftEvidenceEntity::class,
        CaptureDraftAttachmentEntity::class,
        KnowledgeItemEntity::class,
        KnowledgeRevisionEntity::class,
        KnowledgeTagEntity::class,
        KnowledgeItemTagEntity::class,
        KnowledgeRevisionTagEntity::class,
        KnowledgeEvidenceEntity::class,
        KnowledgeAttachmentEntity::class,
        PrivateAttachmentAssetEntity::class,
        GlmOcrTaskEntity::class,
        TemporaryConversationRecoveryEntity::class,
        TemporaryConversationMessageEntity::class,
        TemporaryConversationAttachmentEntity::class,
        InvocationRecordEntity::class,
        InvocationTaskRunEntity::class,
        ProviderAttemptEntity::class,
        GenerationEntity::class,
        GenerationValidationEntity::class,
        ConversationRealTextExecutionEntity::class,
        UsageLedgerEntryEntity::class,
        LocalExactReuseEntryEntity::class,
        GeneratedCandidateEntity::class,
        ConversationEntity::class,
        MessageNodeEntity::class,
        MessageContentBlockEntity::class,
        LocalSearchIndexEntity::class,
        ConversationDraftEntity::class,
        ConversationDraftAttachmentEntity::class,
        ConversationMemorySourceEntity::class,
        AiRuntimeEventEntity::class,
        ConversationRuntimeStateEntity::class,
        NormalChatSendAttemptEntity::class,
        ResumableAttachmentUploadEntity::class,
        AssistantResponseModelAttributionEntity::class,
        CompareConversationSessionEntity::class,
        CompareConversationBranchEntity::class,
        CompareBranchExecutionReceiptEntity::class,
        CompareBranchRuntimeStateEntity::class,
        CompareBranchRuntimeEventEntity::class,
        CompareBranchTerminalIntentEntity::class,
        CompareBranchFollowUpIntentEntity::class,
        CompareBranchAdoptionIntentEntity::class,
        CompareSynthesisIntentEntity::class,
        ConversationAttemptLineageEntity::class,
        ProviderDiagnosticEntity::class,
        ConversationManagementIntentEntity::class,
        ProjectEntity::class,
        ProjectInstructionRevisionEntity::class,
        ProjectIntentEntity::class,
        KnowledgeProjectScopeEntity::class,
        KnowledgeRelationshipEntity::class,
        KnowledgeRelationshipRevisionEntity::class,
        KnowledgeRelationshipIntentEntity::class,
        MarkdownImportTaskEntity::class,
        MarkdownImportItemEntity::class,
        JsonKnowledgeImportTaskEntity::class,
        JsonKnowledgeImportItemEntity::class,
        ChatGptExportImportTaskEntity::class,
        ChatGptExportImportItemEntity::class,
        ChatGptExportImportMessageEntity::class,
        ChatGptImportProvenanceEntity::class,
        ChatGptImportReceiptEntity::class,
        ClaudeExportImportTaskEntity::class,
        ClaudeExportImportItemEntity::class,
        ClaudeExportImportMessageEntity::class,
        ClaudeImportProvenanceEntity::class,
        ClaudeImportReceiptEntity::class,
        NanfengKnowledgeExportImportTaskEntity::class,
        NanfengKnowledgeExportImportItemEntity::class,
        NanfengKnowledgeExportImportMessageEntity::class,
        NanfengKnowledgeImportProvenanceEntity::class,
        NanfengKnowledgeImportReceiptEntity::class,
        P6KZipImportTaskEntity::class,
        P6KZipAssetRecoveryJobEntity::class,
        P6KZipImportItemEntity::class,
        P6KZipImportMessageEntity::class,
        P6KZipAssetCandidateEntity::class,
        P6KZipAssetLinkReceiptEntity::class,
        P6KZipAssetCatalogEntity::class,
        P6KZipAssetOccurrenceEntity::class,
        P6KZipAssetOccurrenceReceiptEntity::class,
        P6KZipAssetLinkProvenanceEntity::class,
        P6KZipProfileCandidateEntity::class,
        P6KZipImportProvenanceEntity::class,
        P6KZipImportMessageProvenanceEntity::class,
        P6KZipImportReceiptEntity::class,
        ThirdPartyProfilePersonalizationSettingsEntity::class,
        P6KProfileImportProvenanceEntity::class,
        P6KProfileImportReceiptEntity::class,
        PdfTextImportTaskEntity::class,
        PdfTextImportPageEntity::class,
        PdfTextImportItemEntity::class,
        WebTextSnapshotTaskEntity::class,
        WebTextSnapshotItemEntity::class,
        OfflineEvalRunEntity::class,
        OfflineEvalCaseResultEntity::class,
        OfflineEvalAssertionEntity::class,
        OfflineEvalHumanScoreEntity::class,
        MemoryEntity::class,
        MemoryRevisionEntity::class,
        MemoryIntentEntity::class,
        MemoryConflictEntity::class,
        SyncAccountMetadataEntity::class,
        SyncIntentEntity::class,
        SyncJobEntity::class,
        SyncJobReceiptEntity::class,
        ManualConversationSyncStateEntity::class,
        AgentRunEntity::class,
        AgentStepEntity::class,
        AgentEventEntity::class,
        AgentCheckpointEntity::class,
        AgentSideEffectReceiptEntity::class,
        P9BIntegrationSessionEntity::class,
        P9BIntegrationEventEntity::class,
        P9BIntegrationReceiptEntity::class,
        WorkspaceExchangeV2RestoreReceiptEntity::class,
        WorkspaceExchangeV2RestoreProvenanceEntity::class,
        WorkspaceExchangeV2RestoreSettingsEntity::class,
        ScheduledMonitorTaskEntity::class,
        ScheduledMonitorRunEntity::class,
        ReminderDraftGenerationRecordEntity::class,
        ConversationTitleGenerationRecordEntity::class,
    ],
    version = 63,
    exportSchema = true,
)
abstract class NanfengAiDatabase : RoomDatabase() {
    abstract fun captureDraftDao(): CaptureDraftDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun knowledgeRelationshipDao(): KnowledgeRelationshipDao
    abstract fun markdownImportTaskDao(): MarkdownImportTaskDao
    abstract fun jsonKnowledgeImportTaskDao(): JsonKnowledgeImportTaskDao
    abstract fun chatGptExportImportTaskDao(): ChatGptExportImportTaskDao
    abstract fun claudeExportImportTaskDao(): ClaudeExportImportTaskDao
    abstract fun nanfengKnowledgeExportImportTaskDao(): NanfengKnowledgeExportImportTaskDao
    abstract fun p6kZipImportTaskDao(): P6KZipImportTaskDao
    abstract fun p6kProfilePersonalizationSettingsDao(): P6KProfilePersonalizationSettingsDao
    abstract fun pdfTextImportTaskDao(): PdfTextImportTaskDao
    abstract fun webTextSnapshotTaskDao(): WebTextSnapshotTaskDao
    abstract fun offlineEvalDao(): OfflineEvalDao
    abstract fun invocationLedgerDao(): InvocationLedgerDao
    abstract fun providerDiagnosticDao(): ProviderDiagnosticDao
    abstract fun normalChatSendAttemptDao(): NormalChatSendAttemptDao
    abstract fun assistantResponseModelAttributionDao(): AssistantResponseModelAttributionDao
    abstract fun resumableAttachmentUploadDao(): ResumableAttachmentUploadDao
    abstract fun conversationRealTextExecutionDao(): ConversationRealTextExecutionDao
    abstract fun usageLedgerDao(): UsageLedgerDao
    abstract fun localExactReuseEntryDao(): LocalExactReuseEntryDao
    abstract fun compareConversationDao(): CompareConversationDao
    abstract fun generatedCandidateDao(): GeneratedCandidateDao
    abstract fun conversationDao(): ConversationDao
    abstract fun projectDao(): ProjectDao
    abstract fun memoryDao(): MemoryDao
    abstract fun privateAttachmentAssetDao(): PrivateAttachmentAssetDao
    abstract fun glmOcrTaskDao(): GlmOcrTaskDao
    abstract fun temporaryConversationRecoveryDao(): TemporaryConversationRecoveryDao
    abstract fun syncAccountMetadataDao(): SyncAccountMetadataDao
    abstract fun syncJobDao(): SyncJobDao
    abstract fun manualConversationSyncStateDao(): ManualConversationSyncStateDao
    abstract fun agentLedgerDao(): AgentLedgerDao
    abstract fun p9bIntegrationLedgerDao(): P9BIntegrationLedgerDao
    abstract fun workspaceExchangeV2RestoreDao(): WorkspaceExchangeV2RestoreDao
    abstract fun scheduledMonitorDao(): ScheduledMonitorDao
    abstract fun reminderDraftGenerationRecordDao(): ReminderDraftGenerationRecordDao
    abstract fun conversationTitleGenerationRecordDao(): ConversationTitleGenerationRecordDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val database = db
                database.execSQL("CREATE TABLE IF NOT EXISTS `invocation_records` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `harnessVersion` INTEGER NOT NULL, `completedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorCode` TEXT, `registrySnapshotId` TEXT, `pricingVersion` TEXT, `inputTokens` INTEGER, `outputTokens` INTEGER, `totalTokens` INTEGER, `cachedInputTokens` INTEGER, `costPriceVersion` TEXT, `costCurrencyCode` TEXT, `costTotalMicros` INTEGER, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `invocation_task_runs` (`id` TEXT NOT NULL, `invocationId` TEXT NOT NULL, `taskId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `completedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`invocationId`) REFERENCES `invocation_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_invocation_task_runs_invocationId` ON `invocation_task_runs` (`invocationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invocation_task_runs_completedAtEpochMs` ON `invocation_task_runs` (`completedAtEpochMs`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `provider_attempts` (`id` TEXT NOT NULL, `taskRunId` TEXT NOT NULL, `position` INTEGER NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `registrySnapshotId` TEXT, `startedAtEpochMs` INTEGER NOT NULL, `completedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorCode` TEXT, `inputTokens` INTEGER, `outputTokens` INTEGER, `totalTokens` INTEGER, `cachedInputTokens` INTEGER, `costPriceVersion` TEXT, `costCurrencyCode` TEXT, `costTotalMicros` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`taskRunId`) REFERENCES `invocation_task_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_attempts_taskRunId` ON `provider_attempts` (`taskRunId`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_provider_attempts_taskRunId_position` ON `provider_attempts` (`taskRunId`, `position`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `generations` (`id` TEXT NOT NULL, `attemptId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`attemptId`) REFERENCES `provider_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_generations_attemptId` ON `generations` (`attemptId`)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `generation_validations` (`id` TEXT NOT NULL, `generationId` TEXT NOT NULL, `completedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `outputContractVersion` INTEGER NOT NULL, `errorCode` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`generationId`) REFERENCES `generations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_generation_validations_generationId` ON `generation_validations` (`generationId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `generated_candidates` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `draftId` TEXT NOT NULL, `invocationId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `harnessVersion` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `generatedAtEpochMs` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `status` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_generated_candidates_invocationId` ON `generated_candidates` (`invocationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_generated_candidates_status_updatedAtEpochMs` ON `generated_candidates` (`status`, `updatedAtEpochMs`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `projectId` TEXT, `currentLeafMessageId` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `defaultProviderId` TEXT, `defaultModelId` TEXT, `harnessId` TEXT, `harnessVersion` INTEGER, `contextPolicyVersion` INTEGER NOT NULL, `archivedAtEpochMs` INTEGER, `pinnedAtEpochMs` INTEGER, `deletedAtEpochMs` INTEGER, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_deletedAtEpochMs_pinnedAtEpochMs_updatedAtEpochMs` ON `conversations` (`deletedAtEpochMs`, `pinnedAtEpochMs`, `updatedAtEpochMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_nodes` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `parentMessageId` TEXT, `siblingPosition` INTEGER NOT NULL, `role` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `deliveryState` TEXT NOT NULL, `revision` INTEGER NOT NULL, `revisesMessageId` TEXT, `invocationId` TEXT, `lastPersistedSequence` INTEGER, `resumableFromSequence` INTEGER, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_nodes_conversationId` ON `message_nodes` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_nodes_parentMessageId` ON `message_nodes` (`parentMessageId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_message_nodes_conversationId_parentMessageId_siblingPosition` ON `message_nodes` (`conversationId`, `parentMessageId`, `siblingPosition`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `message_content_blocks` (`messageId` TEXT NOT NULL, `position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `textContent` TEXT, `attachmentId` TEXT, `storageKey` TEXT, `mimeType` TEXT, `displayName` TEXT, `byteCount` INTEGER, `sha256` TEXT, `toolName` TEXT, `toolSafeSummary` TEXT, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`messageId`, `position`), FOREIGN KEY(`messageId`) REFERENCES `message_nodes`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_content_blocks_messageId` ON `message_content_blocks` (`messageId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_drafts` (`conversationId` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`conversationId`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_draft_attachments` (`conversationId` TEXT NOT NULL, `position` INTEGER NOT NULL, `attachmentId` TEXT NOT NULL, `storageKey` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `displayName` TEXT, `byteCount` INTEGER, `sha256` TEXT, PRIMARY KEY(`conversationId`, `position`), FOREIGN KEY(`conversationId`) REFERENCES `conversation_drafts`(`conversationId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_draft_attachments_conversationId` ON `conversation_draft_attachments` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_draft_attachments_attachmentId` ON `conversation_draft_attachments` (`attachmentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_memory_sources` (`conversationId` TEXT NOT NULL, `position` INTEGER NOT NULL, `memoryId` TEXT NOT NULL, `sourceKind` TEXT NOT NULL, `sourceVersion` INTEGER NOT NULL, PRIMARY KEY(`conversationId`, `position`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_memory_sources_conversationId` ON `conversation_memory_sources` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_memory_sources_memoryId` ON `conversation_memory_sources` (`memoryId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_runtime_events` (`eventId` TEXT NOT NULL, `invocationId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `kind` TEXT NOT NULL, `emittedAtEpochMs` INTEGER NOT NULL, `payloadFingerprint` TEXT NOT NULL, `source` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`eventId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_runtime_events_invocationId_sequence` ON `ai_runtime_events` (`invocationId`, `sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_runtime_events_conversationId` ON `ai_runtime_events` (`conversationId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_runtime_states` (`invocationId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `nextExpectedSequence` INTEGER NOT NULL, `status` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `lastCheckpointSequence` INTEGER, `resumableFromSequence` INTEGER, `inputTokens` INTEGER, `outputTokens` INTEGER, `safeErrorCode` TEXT, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`invocationId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversation_runtime_states_conversationId` ON `conversation_runtime_states` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_runtime_states_messageId` ON `conversation_runtime_states` (`messageId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_attempt_lineages` (`invocationId` TEXT NOT NULL, `intentId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `actionKind` TEXT NOT NULL, `originMessageId` TEXT NOT NULL, `createdMessageId` TEXT NOT NULL, `previousInvocationId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `harnessId` TEXT NOT NULL, `harnessVersion` INTEGER NOT NULL, `registrySnapshotId` TEXT NOT NULL, `pricingVersion` TEXT, `pricingCurrencyCode` TEXT, `inputMicrosPerToken` INTEGER, `outputMicrosPerToken` INTEGER, `cachedInputMicrosPerToken` INTEGER, `createdAtEpochMs` INTEGER NOT NULL, `requestFingerprint` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`invocationId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversation_attempt_lineages_intentId` ON `conversation_attempt_lineages` (`intentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_attempt_lineages_conversationId` ON `conversation_attempt_lineages` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_attempt_lineages_originMessageId` ON `conversation_attempt_lineages` (`originMessageId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_management_intents` (`intentId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `action` TEXT NOT NULL, `requestFingerprint` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`intentId`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_management_intents_conversationId` ON `conversation_management_intents` (`conversationId`)")
            }
        }

        /**
         * P3-G has one migration-only reason: relocate private storage ownership out of
         * Conversation rows. Existing P2 assets are catalogued first; all user rows survive.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `private_attachment_assets` (`attachmentId` TEXT NOT NULL, `storageKey` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `displayName` TEXT, `byteCount` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`attachmentId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_private_attachment_assets_sha256` ON `private_attachment_assets` (`sha256`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_private_attachment_assets_storageKey` ON `private_attachment_assets` (`storageKey`)")
                db.execSQL("INSERT OR IGNORE INTO private_attachment_assets (attachmentId, storageKey, mimeType, displayName, byteCount, sha256, schemaVersion) SELECT attachmentId, storageKey, mimeType, displayName, byteCount, sha256, 1 FROM capture_draft_attachments WHERE byteCount IS NOT NULL AND sha256 IS NOT NULL")
                db.execSQL("INSERT OR IGNORE INTO private_attachment_assets (attachmentId, storageKey, mimeType, displayName, byteCount, sha256, schemaVersion) SELECT attachmentId, storageKey, mimeType, displayName, byteCount, sha256, 1 FROM knowledge_attachments")
                db.execSQL("INSERT OR IGNORE INTO private_attachment_assets (attachmentId, storageKey, mimeType, displayName, byteCount, sha256, schemaVersion) SELECT attachmentId, storageKey, mimeType, displayName, byteCount, sha256, 1 FROM message_content_blocks WHERE kind = 'ATTACHMENT' AND storageKey LIKE 'attachments/v1/%' AND byteCount IS NOT NULL AND sha256 IS NOT NULL")
                db.execSQL("INSERT OR IGNORE INTO private_attachment_assets (attachmentId, storageKey, mimeType, displayName, byteCount, sha256, schemaVersion) SELECT attachmentId, storageKey, mimeType, displayName, byteCount, sha256, 1 FROM conversation_draft_attachments WHERE storageKey LIKE 'attachments/v1/%' AND byteCount IS NOT NULL AND sha256 IS NOT NULL")
                db.execSQL("UPDATE message_content_blocks SET storageKey = attachmentId WHERE kind = 'ATTACHMENT' AND attachmentId IS NOT NULL")
                db.execSQL("UPDATE conversation_draft_attachments SET storageKey = attachmentId")
            }
        }

        /** P4-A adds only local Project facts and nullable scope relations; no existing data is rewritten. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `colorSemantic` TEXT, `iconSemantic` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `archivedAtEpochMs` INTEGER, `pinnedAtEpochMs` INTEGER, `deletedAtEpochMs` INTEGER, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_deletedAtEpochMs_archivedAtEpochMs_pinnedAtEpochMs_updatedAtEpochMs` ON `projects` (`deletedAtEpochMs`, `archivedAtEpochMs`, `pinnedAtEpochMs`, `updatedAtEpochMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `project_instruction_revisions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `content` TEXT NOT NULL, `source` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_instruction_revisions_projectId_revision` ON `project_instruction_revisions` (`projectId`, `revision`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_instruction_revisions_projectId` ON `project_instruction_revisions` (`projectId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `project_intents` (`intentId` TEXT NOT NULL, `projectId` TEXT NOT NULL, `action` TEXT NOT NULL, `requestFingerprint` TEXT NOT NULL, `conversationId` TEXT, `knowledgeId` TEXT, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`intentId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_intents_projectId` ON `project_intents` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_intents_conversationId` ON `project_intents` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_intents_knowledgeId` ON `project_intents` (`knowledgeId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_project_scopes` (`knowledgeId` TEXT NOT NULL, `projectId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`knowledgeId`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_project_scopes_projectId` ON `knowledge_project_scopes` (`projectId`)")
            }
        }

        /** P4-C adds only explicit local Memory governance; P1–P4-B rows are not rewritten. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `memories` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `scopeKind` TEXT NOT NULL, `projectId` TEXT, `conversationId` TEXT, `scopeKey` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceStableId` TEXT NOT NULL, `sourceSummary` TEXT NOT NULL, `status` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `conceptHash` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `lastConfirmedAtEpochMs` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_scopeKind_projectId_conversationId_status_updatedAtEpochMs` ON `memories` (`scopeKind`, `projectId`, `conversationId`, `status`, `updatedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_projectId` ON `memories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_conversationId` ON `memories` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_scopeKey_contentHash` ON `memories` (`scopeKey`, `contentHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_scopeKey_conceptHash` ON `memories` (`scopeKey`, `conceptHash`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_revisions` (`id` TEXT NOT NULL, `memoryId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `scopeKind` TEXT NOT NULL, `projectId` TEXT, `conversationId` TEXT, `source` TEXT NOT NULL, `sourceStableId` TEXT NOT NULL, `sourceSummary` TEXT NOT NULL, `status` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`memoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_revisions_memoryId_revision` ON `memory_revisions` (`memoryId`, `revision`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_revisions_memoryId` ON `memory_revisions` (`memoryId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_intents` (`intentId` TEXT NOT NULL, `memoryId` TEXT, `action` TEXT NOT NULL, `requestFingerprint` TEXT NOT NULL, `conflictId` TEXT, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`intentId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_intents_memoryId` ON `memory_intents` (`memoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_intents_conflictId` ON `memory_intents` (`conflictId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `memory_conflicts` (`id` TEXT NOT NULL, `intentId` TEXT NOT NULL, `existingMemoryId` TEXT NOT NULL, `candidateTitle` TEXT NOT NULL, `candidateBody` TEXT NOT NULL, `scopeKind` TEXT NOT NULL, `projectId` TEXT, `conversationId` TEXT, `source` TEXT NOT NULL, `sourceStableId` TEXT NOT NULL, `sourceSummary` TEXT NOT NULL, `candidateContentHash` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `resolvedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`existingMemoryId`) REFERENCES `memories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_conflicts_intentId` ON `memory_conflicts` (`intentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_conflicts_existingMemoryId` ON `memory_conflicts` (`existingMemoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_conflicts_status` ON `memory_conflicts` (`status`)")
            }
        }

        /** P4-E adds lifecycle/revisions/tags without rebuilding or wiping established Knowledge. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN archivedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL("ALTER TABLE knowledge_items ADD COLUMN contentHash TEXT NOT NULL DEFAULT ''")
                // Android SQLite has no portable SHA-256 scalar. Legacy blank hashes are recomputed by
                // Knowledge Domain on first read; no user row is rewritten merely for indexing.
                db.execSQL("UPDATE knowledge_items SET updatedAtEpochMs=createdAtEpochMs WHERE updatedAtEpochMs=0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_items_status_updatedAtEpochMs ON knowledge_items (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_revisions (id TEXT NOT NULL, knowledgeId TEXT NOT NULL, revision INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, status TEXT NOT NULL, projectId TEXT, contentHash TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_revisions_knowledgeId_revision ON knowledge_revisions (knowledgeId, revision)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_revisions_knowledgeId ON knowledge_revisions (knowledgeId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_tags (name TEXT NOT NULL, PRIMARY KEY(name))")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_item_tags (knowledgeId TEXT NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(knowledgeId, tag))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_item_tags_tag ON knowledge_item_tags (tag)")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_revision_tags (revisionId TEXT NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(revisionId, tag))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_revision_tags_tag ON knowledge_revision_tags (tag)")
                db.execSQL("INSERT OR IGNORE INTO knowledge_revisions (id, knowledgeId, revision, title, body, status, projectId, contentHash, createdAtEpochMs) SELECT id || ':revision:1', id, 1, title, body, status, (SELECT projectId FROM knowledge_project_scopes s WHERE s.knowledgeId=knowledge_items.id), contentHash, updatedAtEpochMs FROM knowledge_items")
            }
        }

        /** P4-G appends local Knowledge relationship/audit facts and leaves every earlier table untouched. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_relationships (id TEXT NOT NULL, relationshipKey TEXT NOT NULL, type TEXT NOT NULL, fromKnowledgeId TEXT NOT NULL, toKnowledgeId TEXT NOT NULL, scopeKind TEXT NOT NULL, projectId TEXT, status TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, createdByIntentId TEXT NOT NULL, latestIntentId TEXT NOT NULL, suggestionSource TEXT NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_relationships_relationshipKey ON knowledge_relationships (relationshipKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationships_status_updatedAtEpochMs ON knowledge_relationships (status, updatedAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationships_fromKnowledgeId ON knowledge_relationships (fromKnowledgeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationships_toKnowledgeId ON knowledge_relationships (toKnowledgeId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_relationship_revisions (id TEXT NOT NULL, relationshipId TEXT NOT NULL, revision INTEGER NOT NULL, action TEXT NOT NULL, status TEXT NOT NULL, intentId TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_relationship_revisions_relationshipId_revision ON knowledge_relationship_revisions (relationshipId, revision)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationship_revisions_relationshipId ON knowledge_relationship_revisions (relationshipId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationship_revisions_intentId ON knowledge_relationship_revisions (intentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS knowledge_relationship_intents (intentId TEXT NOT NULL, relationshipId TEXT, action TEXT NOT NULL, requestFingerprint TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relationship_intents_relationshipId ON knowledge_relationship_intents (relationshipId)")
            }
        }

        /** P4-H appends recoverable Markdown import task facts. It does not touch prior rows or assets. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS markdown_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, sha256 TEXT, adapterVersion INTEGER NOT NULL, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_markdown_import_tasks_status_updatedAtEpochMs ON markdown_import_tasks (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS markdown_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL, status TEXT NOT NULL, failure TEXT, knowledgeId TEXT, PRIMARY KEY(taskId, id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_markdown_import_items_taskId ON markdown_import_items (taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_markdown_import_items_knowledgeId ON markdown_import_items (knowledgeId)")
            }
        }

        /** P4-I appends local run/result/score facts only. Fixture and production bodies remain outside these tables. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_eval_runs (id TEXT NOT NULL, datasetId TEXT NOT NULL, datasetVersion TEXT NOT NULL, domainVersion INTEGER NOT NULL, appVersion TEXT NOT NULL, schemaVersion INTEGER NOT NULL, fixtureManifestHash TEXT NOT NULL, assertionVersion TEXT NOT NULL, rubricVersion TEXT NOT NULL, securitySummary TEXT NOT NULL, startedAtEpochMs INTEGER NOT NULL, completedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_eval_runs_completedAtEpochMs_id ON offline_eval_runs (completedAtEpochMs, id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_eval_case_results (runId TEXT NOT NULL, caseId TEXT NOT NULL, verdict TEXT NOT NULL, PRIMARY KEY(runId, caseId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_eval_case_results_caseId ON offline_eval_case_results (caseId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_eval_assertions (runId TEXT NOT NULL, assertionId TEXT NOT NULL, caseId TEXT NOT NULL, fact TEXT NOT NULL, verdict TEXT NOT NULL, detail TEXT NOT NULL, PRIMARY KEY(runId, assertionId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_eval_assertions_runId_caseId ON offline_eval_assertions (runId, caseId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS offline_eval_human_scores (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, runId TEXT NOT NULL, caseId TEXT NOT NULL, dimension TEXT NOT NULL, score INTEGER, reviewerAlias TEXT NOT NULL, rubricVersion TEXT NOT NULL, notedAtEpochMs INTEGER NOT NULL, note TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_eval_human_scores_runId_caseId ON offline_eval_human_scores (runId, caseId)")
            }
        }
        /** P4-L adds only JSON-specific portability task facts. Earlier queues and Knowledge rows are untouched. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS json_knowledge_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, sha256 TEXT, adapterVersion INTEGER NOT NULL, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_json_knowledge_import_tasks_status_updatedAtEpochMs ON json_knowledge_import_tasks (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS json_knowledge_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceStableId TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL, sourceSummary TEXT NOT NULL, requestedScope TEXT NOT NULL, status TEXT NOT NULL, failure TEXT, knowledgeId TEXT, PRIMARY KEY(taskId, id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_json_knowledge_import_items_taskId ON json_knowledge_import_items (taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_json_knowledge_import_items_knowledgeId ON json_knowledge_import_items (knowledgeId)")
            }
        }
        /** P4-N adds only PDF-text adapter facts; existing queues and formal Knowledge remain unchanged. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pdf_text_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, sourceSha256 TEXT, adapterVersion INTEGER NOT NULL, pageCount INTEGER NOT NULL, extractedPageCount INTEGER NOT NULL, extractionSha256 TEXT, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_text_import_tasks_status_updatedAtEpochMs ON pdf_text_import_tasks (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pdf_text_import_pages (taskId TEXT NOT NULL, pageNumber INTEGER NOT NULL, textSha256 TEXT NOT NULL, codePointCount INTEGER NOT NULL, extractionVersion INTEGER NOT NULL, PRIMARY KEY(taskId, pageNumber))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_text_import_pages_taskId ON pdf_text_import_pages (taskId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS pdf_text_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, pageNumber INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL, candidateSha256 TEXT NOT NULL, status TEXT NOT NULL, failure TEXT, knowledgeId TEXT, PRIMARY KEY(taskId, id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_text_import_items_taskId ON pdf_text_import_items (taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pdf_text_import_items_knowledgeId ON pdf_text_import_items (knowledgeId)")
            }
        }
        /** P4-O appends only isolated web snapshot facts; no legacy row is rewritten. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS web_text_snapshot_tasks (id TEXT NOT NULL, status TEXT NOT NULL, requestedUrl TEXT NOT NULL, storageKey TEXT, displayUrl TEXT, host TEXT, rawHtmlSha256 TEXT, byteCount INTEGER, adapterVersion INTEGER NOT NULL, extractedSha256 TEXT, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_web_text_snapshot_tasks_status_updatedAtEpochMs ON web_text_snapshot_tasks (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS web_text_snapshot_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, candidateSha256 TEXT NOT NULL, status TEXT NOT NULL, failure TEXT, knowledgeId TEXT, PRIMARY KEY(taskId, id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_web_text_snapshot_items_taskId ON web_text_snapshot_items (taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_web_text_snapshot_items_knowledgeId ON web_text_snapshot_items (knowledgeId)")
            }
        }
        /** P7-B only appends secret-free metadata; no legacy business row is rewritten. */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_account_metadata (accountRef TEXT NOT NULL, state TEXT NOT NULL, revision INTEGER NOT NULL, keyAliasRef TEXT, wrappedKeyRef TEXT, wrappedKeySha256 TEXT, directionFact TEXT, lastError TEXT, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(accountRef))")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_intents (intentId TEXT NOT NULL, accountRef TEXT NOT NULL, expectedRevision INTEGER, resultingRevision INTEGER NOT NULL, resultingState TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_intents_accountRef ON sync_intents (accountRef)")
            }
        }
        /** P7-D appends only secret-free orchestration facts and never touches business rows. */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_jobs (accountRef TEXT NOT NULL, generation INTEGER NOT NULL, completedGeneration INTEGER NOT NULL, stage TEXT NOT NULL, lastLocalRevision INTEGER, lastLocalHash TEXT, lastRemoteRevision INTEGER, lastRemoteHash TEXT, stagingRef TEXT, stagingHash TEXT, stagingBytes INTEGER, lastErrorCode TEXT, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(accountRef))")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_job_receipts (intentId TEXT NOT NULL, accountRef TEXT NOT NULL, generation INTEGER NOT NULL, stage TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_job_receipts_accountRef ON sync_job_receipts (accountRef)")
            }
        }
        /** P8-A appends a secret-free durable Agent ledger; existing business facts remain untouched. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_runs (id TEXT NOT NULL, idempotencyKey TEXT NOT NULL, toolSchemaVersion INTEGER NOT NULL, status TEXT NOT NULL, riskCeiling TEXT NOT NULL, permissionGrant TEXT NOT NULL, maxSteps INTEGER NOT NULL, maxToolCalls INTEGER NOT NULL, maxSideEffects INTEGER NOT NULL, usedSteps INTEGER NOT NULL, usedToolCalls INTEGER NOT NULL, usedSideEffects INTEGER NOT NULL, lastCheckpointSequence INTEGER, safeErrorCode TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_runs_idempotencyKey ON agent_runs (idempotencyKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_runs_status_updatedAtEpochMs ON agent_runs (status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_steps (runId TEXT NOT NULL, sequence INTEGER NOT NULL, idempotencyKey TEXT NOT NULL, toolId TEXT NOT NULL, inputHash TEXT NOT NULL, status TEXT NOT NULL, resultHash TEXT, safeErrorCode TEXT, sideEffectClass TEXT NOT NULL, riskLevel TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(runId, sequence))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_steps_runId_idempotencyKey ON agent_steps (runId, idempotencyKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_events (runId TEXT NOT NULL, sequence INTEGER NOT NULL, eventId TEXT NOT NULL, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(runId, sequence))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_agent_events_runId_eventId ON agent_events (runId, eventId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_checkpoints (runId TEXT NOT NULL, sequence INTEGER NOT NULL, status TEXT NOT NULL, nextStepSequence INTEGER NOT NULL, safeStateHash TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(runId, sequence))")
                db.execSQL("CREATE TABLE IF NOT EXISTS agent_side_effect_receipts (idempotencyKey TEXT NOT NULL, runId TEXT NOT NULL, stepSequence INTEGER NOT NULL, toolId TEXT NOT NULL, sideEffectClass TEXT NOT NULL, outcome TEXT NOT NULL, resultHash TEXT NOT NULL, rollbackAvailable INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(idempotencyKey))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_side_effect_receipts_runId ON agent_side_effect_receipts (runId)")
            }
        }
        /** P9-B adds a private, secret-free local contract audit only; it is not a cross-app adapter. */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS p9b_integration_sessions (requestId TEXT NOT NULL, idempotencyKey TEXT NOT NULL, appHandle TEXT NOT NULL, subjectHandle TEXT NOT NULL, state TEXT NOT NULL, capability TEXT NOT NULL, permission TEXT NOT NULL, classification TEXT NOT NULL, provenanceSource TEXT NOT NULL, sourceRevision INTEGER NOT NULL, sourceHash TEXT NOT NULL, pageLimit INTEGER NOT NULL, pageCursor TEXT, expiresAtEpochMs INTEGER, previewRevision INTEGER, previewHash TEXT, previewItemCount INTEGER, previewNextCursor TEXT, resultHash TEXT, errorCode TEXT, PRIMARY KEY(requestId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_p9b_integration_sessions_idempotencyKey ON p9b_integration_sessions (idempotencyKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p9b_integration_sessions_state ON p9b_integration_sessions (state)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p9b_integration_events (requestId TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(requestId, sequence))")
                db.execSQL("CREATE TABLE IF NOT EXISTS p9b_integration_receipts (idempotencyKey TEXT NOT NULL, requestId TEXT NOT NULL, resultHash TEXT NOT NULL, PRIMARY KEY(idempotencyKey))")
            }
        }
        /** P6-D shared Conversation lifecycle adds only optimistic revision and safe receipts. */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversation_management_intents ADD COLUMN expectedRevision INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversation_management_intents ADD COLUMN resultRevision INTEGER NOT NULL DEFAULT 1")
            }
        }
        /** P6-E adds an isolated, expiring recovery owner; normal conversation tables are untouched. */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS temporary_conversation_recovery (temporaryId TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, draftText TEXT NOT NULL, modelOverrideId TEXT, schemaVersion INTEGER NOT NULL, PRIMARY KEY(temporaryId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS temporary_conversation_messages (messageId TEXT NOT NULL, temporaryId TEXT NOT NULL, position INTEGER NOT NULL, text TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(messageId), FOREIGN KEY(temporaryId) REFERENCES temporary_conversation_recovery(temporaryId) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_conversation_messages_temporaryId ON temporary_conversation_messages (temporaryId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS temporary_conversation_attachments (temporaryId TEXT NOT NULL, ownerKind TEXT NOT NULL, ownerId TEXT NOT NULL, position INTEGER NOT NULL, attachmentId TEXT NOT NULL, scope TEXT NOT NULL, PRIMARY KEY(temporaryId, ownerKind, ownerId, position), FOREIGN KEY(temporaryId) REFERENCES temporary_conversation_recovery(temporaryId) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_conversation_attachments_temporaryId ON temporary_conversation_attachments (temporaryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_temporary_conversation_attachments_attachmentId ON temporary_conversation_attachments (attachmentId)")
            }
        }
        /** P6-F2-A appends an owner-maintained safe search projection; source rows are unchanged. */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS local_search_index (id TEXT NOT NULL, conversationId TEXT NOT NULL, messageNodeId TEXT, contentKind TEXT NOT NULL, title TEXT NOT NULL, normalizedText TEXT NOT NULL, snippet TEXT NOT NULL, timestampEpochMs INTEGER NOT NULL, archivedAtEpochMs INTEGER, deletedAtEpochMs INTEGER, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_search_index_conversationId ON local_search_index (conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_search_index_normalizedText_deletedAtEpochMs_archivedAtEpochMs ON local_search_index (normalizedText, deletedAtEpochMs, archivedAtEpochMs)")
            }
        }
        /** P6-H appends an isolated ChatGPT-import queue, candidate tree, receipt and provenance facts only. */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS chatgpt_export_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, packageHash TEXT, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chatgpt_export_import_tasks_status_updatedAtEpochMs ON chatgpt_export_import_tasks(status, updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS chatgpt_export_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceConversationId TEXT, title TEXT, createdAtEpochMs INTEGER, updatedAtEpochMs INTEGER, contentHash TEXT, status TEXT NOT NULL, failure TEXT, conversationId TEXT, PRIMARY KEY(taskId,id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chatgpt_export_import_items_taskId ON chatgpt_export_import_items(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chatgpt_export_import_items_conversationId ON chatgpt_export_import_items(conversationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS chatgpt_export_import_messages (taskId TEXT NOT NULL, itemId TEXT NOT NULL, sourceMessageId TEXT NOT NULL, parentSourceMessageId TEXT, siblingPosition INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, importedModel TEXT, PRIMARY KEY(taskId,itemId,sourceMessageId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chatgpt_export_import_messages_taskId_itemId ON chatgpt_export_import_messages(taskId,itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS chatgpt_import_provenance (conversationId TEXT NOT NULL, sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, contentHash TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, adapterId TEXT NOT NULL, adapterVersion INTEGER NOT NULL, revokedAtEpochMs INTEGER, PRIMARY KEY(conversationId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chatgpt_import_provenance_sourceConversationId_packageHash ON chatgpt_import_provenance(sourceConversationId,packageHash)")
                db.execSQL("CREATE TABLE IF NOT EXISTS chatgpt_import_receipts (sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, conversationId TEXT NOT NULL, contentHash TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceConversationId,packageHash))")
            }
        }
        /** P6-I marks only future, empty local conversations as eligible for a first-message title. */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN autoTitlePending INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** Existing persisted conversations retain their normal-chat identity after the split. */
        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN surface TEXT NOT NULL DEFAULT 'CHAT'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_surface_deletedAtEpochMs_archivedAtEpochMs_updatedAtEpochMs ON conversations(surface, deletedAtEpochMs, archivedAtEpochMs, updatedAtEpochMs)")
            }
        }
        /** P6-I appends an adapter-owned Claude import queue and receipt/provenance facts only. */
        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS claude_export_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, packageHash TEXT, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_claude_export_import_tasks_status_updatedAtEpochMs ON claude_export_import_tasks(status,updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS claude_export_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceConversationId TEXT, title TEXT, createdAtEpochMs INTEGER, updatedAtEpochMs INTEGER, contentHash TEXT, status TEXT NOT NULL, failure TEXT, conversationId TEXT, PRIMARY KEY(taskId,id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_claude_export_import_items_taskId ON claude_export_import_items(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_claude_export_import_items_conversationId ON claude_export_import_items(conversationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS claude_export_import_messages (taskId TEXT NOT NULL, itemId TEXT NOT NULL, sourceMessageId TEXT NOT NULL, parentSourceMessageId TEXT, siblingPosition INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(taskId,itemId,sourceMessageId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_claude_export_import_messages_taskId_itemId ON claude_export_import_messages(taskId,itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS claude_import_provenance (conversationId TEXT NOT NULL, sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, contentHash TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, adapterId TEXT NOT NULL, adapterVersion INTEGER NOT NULL, revokedAtEpochMs INTEGER, PRIMARY KEY(conversationId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_claude_import_provenance_sourceConversationId_packageHash ON claude_import_provenance(sourceConversationId,packageHash)")
                db.execSQL("CREATE TABLE IF NOT EXISTS claude_import_receipts (sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, conversationId TEXT NOT NULL, contentHash TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceConversationId,packageHash))")
            }
        }
        /** P6-J appends its own static knowledge-export queue, receipts and provenance facts. */
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS nanfeng_knowledge_export_import_tasks (id TEXT NOT NULL, status TEXT NOT NULL, storageKey TEXT, mimeType TEXT, displayName TEXT, byteCount INTEGER, packageHash TEXT, failure TEXT, retryCount INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nanfeng_knowledge_export_import_tasks_status_updatedAtEpochMs ON nanfeng_knowledge_export_import_tasks(status,updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS nanfeng_knowledge_export_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceConversationId TEXT, title TEXT, createdAtEpochMs INTEGER, updatedAtEpochMs INTEGER, contentHash TEXT, status TEXT NOT NULL, failure TEXT, conversationId TEXT, PRIMARY KEY(taskId,id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nanfeng_knowledge_export_import_items_taskId ON nanfeng_knowledge_export_import_items(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nanfeng_knowledge_export_import_items_conversationId ON nanfeng_knowledge_export_import_items(conversationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS nanfeng_knowledge_export_import_messages (taskId TEXT NOT NULL, itemId TEXT NOT NULL, sourceMessageId TEXT NOT NULL, parentSourceMessageId TEXT, siblingPosition INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(taskId,itemId,sourceMessageId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nanfeng_knowledge_export_import_messages_taskId_itemId ON nanfeng_knowledge_export_import_messages(taskId,itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS nanfeng_knowledge_import_provenance (conversationId TEXT NOT NULL, sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, contentHash TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, adapterId TEXT NOT NULL, adapterVersion INTEGER NOT NULL, revokedAtEpochMs INTEGER, PRIMARY KEY(conversationId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_nanfeng_knowledge_import_provenance_sourceConversationId_packageHash ON nanfeng_knowledge_import_provenance(sourceConversationId,packageHash)")
                db.execSQL("CREATE TABLE IF NOT EXISTS nanfeng_knowledge_import_receipts (sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, conversationId TEXT NOT NULL, contentHash TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceConversationId,packageHash))")
            }
        }
        /** P3-I appends only secret-free real-text execution receipts. */
        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversation_real_text_executions (executionId TEXT NOT NULL, idempotencyKey TEXT NOT NULL, requestFingerprint TEXT NOT NULL, conversationId TEXT NOT NULL, userMessageId TEXT NOT NULL, assistantMessageId TEXT NOT NULL, invocationId TEXT NOT NULL, attemptId TEXT NOT NULL, state TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, terminalAtEpochMs INTEGER, safeErrorCode TEXT, PRIMARY KEY(executionId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_conversation_real_text_executions_idempotencyKey ON conversation_real_text_executions (idempotencyKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_real_text_executions_conversationId ON conversation_real_text_executions (conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_real_text_executions_invocationId ON conversation_real_text_executions (invocationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_real_text_executions_attemptId ON conversation_real_text_executions (attemptId)")
            }
        }
        /** P0 appends immutable quantitative facts and derives the read model in memory. */
        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS usage_ledger_entries (entryId TEXT NOT NULL, replayToken TEXT NOT NULL, executionId TEXT NOT NULL, conversationId TEXT NOT NULL, branchLeafMessageId TEXT NOT NULL, invocationId TEXT NOT NULL, attemptId TEXT NOT NULL, kind TEXT NOT NULL, factGrade TEXT NOT NULL, requestedModelId TEXT NOT NULL, actualModelId TEXT, inputTokens INTEGER, outputTokens INTEGER, cachedInputTokens INTEGER, chargeMicros INTEGER, budgetMicros INTEGER, adjustmentMicros INTEGER, currencyCode TEXT, reconciliationFingerprint TEXT, reconcilesEntryId TEXT, source TEXT NOT NULL, occurredAtEpochMs INTEGER NOT NULL, PRIMARY KEY(entryId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_usage_ledger_entries_replayToken ON usage_ledger_entries (replayToken)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_ledger_entries_executionId ON usage_ledger_entries (executionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_ledger_entries_conversationId ON usage_ledger_entries (conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_ledger_entries_reconcilesEntryId ON usage_ledger_entries (reconcilesEntryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_usage_ledger_entries_reconciliationFingerprint ON usage_ledger_entries (reconciliationFingerprint)")
            }
        }
        /** MM-O4-C appends Compare owner/branch/runtime/intent metadata without touching existing P3 tables. */
        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_conversation_sessions (sessionId TEXT NOT NULL, intentId TEXT NOT NULL, requestFingerprint TEXT NOT NULL, confirmationId TEXT NOT NULL, conversationId TEXT NOT NULL, parentUserMessageId TEXT NOT NULL, canonicalContextId TEXT NOT NULL, canonicalContextHash TEXT NOT NULL, canonicalContextRevision INTEGER NOT NULL, sharedCurrentLeafAtPlanning TEXT NOT NULL, synthesisPolicy TEXT NOT NULL, sharedContextPolicy TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sessionId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_conversation_sessions_intentId ON compare_conversation_sessions (intentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_conversation_sessions_confirmationId ON compare_conversation_sessions (confirmationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_conversation_sessions_conversationId ON compare_conversation_sessions (conversationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_conversation_branches (sessionId TEXT NOT NULL, branchId TEXT NOT NULL, grantId TEXT NOT NULL, requestFingerprint TEXT NOT NULL, contextHash TEXT NOT NULL, textSha256 TEXT NOT NULL, logicalModelId TEXT NOT NULL, deploymentId TEXT NOT NULL, providerHandle TEXT NOT NULL, providerModelId TEXT NOT NULL, catalogVersion TEXT NOT NULL, priceVersion TEXT NOT NULL, currencyCode TEXT NOT NULL, maximumBudgetMicros INTEGER NOT NULL, confirmationScopeFingerprint TEXT NOT NULL, grantExpiresAtEpochMs INTEGER NOT NULL, assistantMessageId TEXT NOT NULL, invocationId TEXT NOT NULL, attemptId TEXT NOT NULL, executionId TEXT NOT NULL, cancellationId TEXT NOT NULL, usageReservationReplayToken TEXT NOT NULL, PRIMARY KEY(sessionId, branchId))")
                listOf("assistantMessageId", "invocationId", "attemptId", "executionId", "cancellationId").forEach { column -> db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_conversation_branches_$column ON compare_conversation_branches ($column)") }
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_conversation_branches_sessionId ON compare_conversation_branches (sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_runtime_states (sessionId TEXT NOT NULL, branchId TEXT NOT NULL, invocationId TEXT NOT NULL, assistantMessageId TEXT NOT NULL, state TEXT NOT NULL, nextExpectedSequence INTEGER NOT NULL, lastCheckpointSequence INTEGER, safeErrorCode TEXT, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sessionId, branchId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_branch_runtime_states_sessionId_invocationId ON compare_branch_runtime_states (sessionId, invocationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_runtime_states_assistantMessageId ON compare_branch_runtime_states (assistantMessageId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_runtime_events (eventId TEXT NOT NULL, sessionId TEXT NOT NULL, branchId TEXT NOT NULL, invocationId TEXT NOT NULL, sequence INTEGER NOT NULL, kind TEXT NOT NULL, payloadFingerprint TEXT NOT NULL, emittedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(eventId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_branch_runtime_events_sessionId_branchId_sequence ON compare_branch_runtime_events (sessionId, branchId, sequence)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_runtime_events_invocationId ON compare_branch_runtime_events (invocationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_terminal_intents (intentId TEXT NOT NULL, sessionId TEXT NOT NULL, branchId TEXT NOT NULL, fingerprint TEXT NOT NULL, nextState TEXT NOT NULL, cancellationId TEXT, recordedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_terminal_intents_sessionId_branchId ON compare_branch_terminal_intents (sessionId, branchId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_follow_up_intents (intentId TEXT NOT NULL, sessionId TEXT NOT NULL, branchId TEXT NOT NULL, fingerprint TEXT NOT NULL, canonicalContextId TEXT NOT NULL, canonicalContextHash TEXT NOT NULL, canonicalContextRevision INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_follow_up_intents_sessionId_branchId ON compare_branch_follow_up_intents (sessionId, branchId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_adoption_intents (intentId TEXT NOT NULL, sessionId TEXT NOT NULL, branchId TEXT NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_adoption_intents_sessionId ON compare_branch_adoption_intents (sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_synthesis_intents (intentId TEXT NOT NULL, sessionId TEXT NOT NULL, fingerprint TEXT NOT NULL, sourceBranchIds TEXT NOT NULL, canonicalContextId TEXT NOT NULL, canonicalContextHash TEXT NOT NULL, canonicalContextRevision INTEGER NOT NULL, gateId TEXT NOT NULL, gateScopeFingerprint TEXT NOT NULL, gateCurrencyCode TEXT NOT NULL, gateMaximumBudgetMicros INTEGER NOT NULL, gateExpiresAtEpochMs INTEGER NOT NULL, PRIMARY KEY(intentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_synthesis_intents_sessionId ON compare_synthesis_intents (sessionId)")
            }
        }
        /** MM-O4-F appends only Compare branch receipt metadata; it does not mutate P3 tables. */
        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS compare_branch_execution_receipts (executionId TEXT NOT NULL, sessionId TEXT NOT NULL, branchId TEXT NOT NULL, invocationId TEXT NOT NULL, attemptId TEXT NOT NULL, requestFingerprint TEXT NOT NULL, state TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, terminalAtEpochMs INTEGER, safeErrorCode TEXT, PRIMARY KEY(executionId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_execution_receipts_sessionId ON compare_branch_execution_receipts (sessionId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_compare_branch_execution_receipts_sessionId_branchId ON compare_branch_execution_receipts (sessionId, branchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_execution_receipts_invocationId ON compare_branch_execution_receipts (invocationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compare_branch_execution_receipts_attemptId ON compare_branch_execution_receipts (attemptId)")
            }
        }
        /** P6-K moves the preflight journal into an adapter-owned durable Room queue; no business rows are touched. */
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_import_tasks (id TEXT NOT NULL, provider TEXT NOT NULL, displayName TEXT NOT NULL, byteCount INTEGER NOT NULL, packageHash TEXT NOT NULL, status TEXT NOT NULL, failure TEXT, formatVersion TEXT, createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_import_tasks_status_updatedAtEpochMs ON p6k_zip_import_tasks(status,updatedAtEpochMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_import_items (taskId TEXT NOT NULL, id TEXT NOT NULL, ordinal INTEGER NOT NULL, sourceConversationId TEXT, title TEXT, createdAtEpochMs INTEGER, updatedAtEpochMs INTEGER, contentHash TEXT, failure TEXT, PRIMARY KEY(taskId,id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_import_items_taskId ON p6k_zip_import_items(taskId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_import_messages (taskId TEXT NOT NULL, itemId TEXT NOT NULL, sourceMessageId TEXT NOT NULL, parentSourceMessageId TEXT, siblingPosition INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, importedModel TEXT, PRIMARY KEY(taskId,itemId,sourceMessageId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_import_messages_taskId_itemId ON p6k_zip_import_messages(taskId,itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_asset_candidates (taskId TEXT NOT NULL, entryName TEXT NOT NULL, sha256 TEXT NOT NULL, byteCount INTEGER NOT NULL, mimeType TEXT NOT NULL, role TEXT NOT NULL, sourceConversationId TEXT, sourceMessageId TEXT, PRIMARY KEY(taskId,entryName))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_asset_candidates_taskId ON p6k_zip_asset_candidates(taskId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_profile_candidates (taskId TEXT NOT NULL, status TEXT NOT NULL, mappedFieldCount INTEGER NOT NULL, PRIMARY KEY(taskId))")
            }
        }
        /** P6-K2 adds only decision receipts/provenance and item status to the existing candidate queue. */
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p6k_zip_import_items ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING_CONFIRMATION'")
                db.execSQL("ALTER TABLE p6k_zip_import_items ADD COLUMN conversationId TEXT")
                db.execSQL("UPDATE p6k_zip_import_items SET status='FAILED' WHERE failure IS NOT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_import_provenance (conversationId TEXT NOT NULL, taskId TEXT NOT NULL, itemId TEXT NOT NULL, sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, contentHash TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, adapterId TEXT NOT NULL, adapterVersion INTEGER NOT NULL, PRIMARY KEY(conversationId))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_p6k_zip_import_provenance_sourceConversationId_packageHash ON p6k_zip_import_provenance(sourceConversationId,packageHash)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_import_receipts (sourceConversationId TEXT NOT NULL, packageHash TEXT NOT NULL, taskId TEXT NOT NULL, itemId TEXT NOT NULL, conversationId TEXT NOT NULL, contentHash TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(sourceConversationId,packageHash))")
            }
        }
        /** K6 adds one local, non-account personalization owner and its reversible P6-K audit rows. */
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS third_party_profile_personalization_settings (id INTEGER NOT NULL, displayName TEXT, language TEXT, timezone TEXT, publicBio TEXT, customInstructions TEXT, theme TEXT, notificationsEnabled INTEGER, sourceTaskId TEXT NOT NULL, revision INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_profile_import_provenance (taskId TEXT NOT NULL, provider TEXT NOT NULL, packageHash TEXT NOT NULL, canonicalHash TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(taskId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_profile_import_receipts (provider TEXT NOT NULL, packageHash TEXT NOT NULL, taskId TEXT NOT NULL, canonicalHash TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(provider,packageHash))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_profile_import_receipts_taskId ON p6k_profile_import_receipts(taskId)")
            }
        }
        /** K8 stores only the explicit local asset/message receipt; archive bytes remain in ZIP staging. */
        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE p6k_zip_asset_candidates ADD COLUMN linkedConversationId TEXT")
                db.execSQL("ALTER TABLE p6k_zip_asset_candidates ADD COLUMN linkedMessageId TEXT")
                db.execSQL("ALTER TABLE p6k_zip_asset_candidates ADD COLUMN attachmentId TEXT")
                db.execSQL("ALTER TABLE p6k_zip_asset_candidates ADD COLUMN failure TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_asset_link_receipts (taskId TEXT NOT NULL, entryName TEXT NOT NULL, sha256 TEXT NOT NULL, attachmentId TEXT NOT NULL, conversationId TEXT NOT NULL, messageId TEXT NOT NULL, committedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(taskId,entryName))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_asset_link_receipts_attachmentId ON p6k_zip_asset_link_receipts(attachmentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_asset_link_receipts_conversationId ON p6k_zip_asset_link_receipts(conversationId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS p6k_zip_asset_link_provenance (attachmentId TEXT NOT NULL, taskId TEXT NOT NULL, entryName TEXT NOT NULL, sha256 TEXT NOT NULL, conversationId TEXT NOT NULL, messageId TEXT NOT NULL, importedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(attachmentId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_p6k_zip_asset_link_provenance_taskId ON p6k_zip_asset_link_provenance(taskId)")
            }
        }
        /** P6-L2 appends only content-free exact reuse facts; it does not alter messages or usage. */
        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS local_exact_reuse_entries (canonicalRequestHash TEXT NOT NULL, scopeId TEXT NOT NULL, providerId TEXT NOT NULL, modelSnapshotId TEXT NOT NULL, endpointMode TEXT NOT NULL, generationParametersHash TEXT NOT NULL, toolSchemaHash TEXT NOT NULL, contextManifestHash TEXT NOT NULL, messageTreeHash TEXT NOT NULL, attachmentHash TEXT NOT NULL, templateVersion TEXT NOT NULL, policyVersion INTEGER NOT NULL, sensitivity TEXT NOT NULL, keyVersion TEXT NOT NULL, responseMessageId TEXT NOT NULL, createdAtEpochMs INTEGER NOT NULL, expiresAtEpochMs INTEGER NOT NULL, revoked INTEGER NOT NULL, PRIMARY KEY(canonicalRequestHash))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_exact_reuse_entries_expiresAtEpochMs ON local_exact_reuse_entries(expiresAtEpochMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_exact_reuse_entries_revoked ON local_exact_reuse_entries(revoked)")
            }
        }
        /** P6 v2 Android restore keeps only content-free receipt/provenance and safe settings metadata. */
        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_exchange_v2_restore_receipts` (`intentId` TEXT NOT NULL, `packageHash` TEXT NOT NULL, `semanticHash` TEXT NOT NULL, `origin` TEXT NOT NULL, `sensitivity` TEXT NOT NULL, `projectCount` INTEGER NOT NULL, `conversationCount` INTEGER NOT NULL, `knowledgeCount` INTEGER NOT NULL, `memoryCount` INTEGER NOT NULL, `relationCount` INTEGER NOT NULL, `assetCount` INTEGER NOT NULL, `assetBytes` INTEGER NOT NULL, `importedAtEpochMs` INTEGER NOT NULL, `importerVersion` INTEGER NOT NULL, PRIMARY KEY(`intentId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_exchange_v2_restore_provenance` (`ownerKind` TEXT NOT NULL, `ownerId` TEXT NOT NULL, `packageHash` TEXT NOT NULL, `semanticHash` TEXT NOT NULL, `ownerFieldHash` TEXT NOT NULL, `importedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`ownerKind`, `ownerId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `workspace_exchange_v2_restore_settings` (`id` TEXT NOT NULL, `uiLanguage` TEXT NOT NULL, `theme` TEXT NOT NULL, `packageHash` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        /** Independent, bounded diagnostic lane; normal invocation/audit tables stay content-free. */
        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `debug_call_log` (`id` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `providerId` TEXT NOT NULL, `endpointHost` TEXT NOT NULL, `apiModelId` TEXT NOT NULL, `httpStatus` INTEGER, `errorClass` TEXT NOT NULL, `redactedBody` TEXT, `requestShape` TEXT NOT NULL, `latencyMs` INTEGER, `timeToFirstByteMs` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debug_call_log_createdAtEpochMs` ON `debug_call_log` (`createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debug_call_log_providerId_createdAtEpochMs` ON `debug_call_log` (`providerId`, `createdAtEpochMs`)")
            }
        }
        /** Durable ordinary-chat request facts for manual retry/recovery; no content columns. */
        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `normal_chat_send_attempts` (`attemptId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `idempotencyKey` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `safeErrorCode` TEXT, PRIMARY KEY(`attemptId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_normal_chat_send_attempts_idempotencyKey` ON `normal_chat_send_attempts` (`idempotencyKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_normal_chat_send_attempts_conversationId` ON `normal_chat_send_attempts` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_normal_chat_send_attempts_messageId` ON `normal_chat_send_attempts` (`messageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_normal_chat_send_attempts_status_updatedAtEpochMs` ON `normal_chat_send_attempts` (`status`, `updatedAtEpochMs`)")
            }
        }
        /** FTS5 is a rebuildable local acceleration structure, maintained by base-table triggers. */
        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) { ContextIndexSchema.ensure(db) }
        }
        /** Rebuild the derived index to add CJK two-character terms; business tables are untouched. */
        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) { ContextIndexSchema.rebuild(db) }
        }
        /** Attachment resumability persists only opaque session/cursor facts, never a capability URL or bytes. */
        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `resumable_attachment_uploads` (`uploadId` TEXT NOT NULL, `normalChatAttemptId` TEXT NOT NULL, `attachmentId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `gatewayId` TEXT NOT NULL, `sha256` TEXT NOT NULL, `byteCount` INTEGER NOT NULL, `gatewaySessionId` TEXT, `acknowledgedBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `safeErrorCode` TEXT, PRIMARY KEY(`uploadId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_resumable_attachment_uploads_normalChatAttemptId_attachmentId` ON `resumable_attachment_uploads` (`normalChatAttemptId`, `attachmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_resumable_attachment_uploads_status_updatedAtEpochMs` ON `resumable_attachment_uploads` (`status`, `updatedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_resumable_attachment_uploads_gatewaySessionId` ON `resumable_attachment_uploads` (`gatewaySessionId`)")
            }
        }
        /** Visible model labels use the exact saved Attempt route, never the current Composer choice. */
        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `normal_chat_send_attempts` ADD COLUMN `egressProviderId` TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `assistant_response_model_attributions` (`assistantMessageId` TEXT NOT NULL, `attemptId` TEXT NOT NULL, `providerId` TEXT NOT NULL, `receiverProviderId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `modelDisplayName` TEXT NOT NULL, `recordedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`assistantMessageId`, `attemptId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_response_model_attributions_assistantMessageId` ON `assistant_response_model_attributions` (`assistantMessageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_response_model_attributions_attemptId` ON `assistant_response_model_attributions` (`attemptId`)")
            }
        }
        /** User-authorized recurring monitoring persists explicit instructions and results only. */
        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_monitor_tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `instruction` TEXT NOT NULL, `sourceConversationId` TEXT, `cadence` TEXT NOT NULL, `modelPresetId` TEXT NOT NULL, `status` TEXT NOT NULL, `nextRunAtEpochMs` INTEGER NOT NULL, `lastRunAtEpochMs` INTEGER, `latestResult` TEXT, `lastProviderId` TEXT, `lastModelId` TEXT, `lastInputTokens` INTEGER, `lastOutputTokens` INTEGER, `lastSafeErrorCode` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_monitor_tasks_status_nextRunAtEpochMs` ON `scheduled_monitor_tasks` (`status`, `nextRunAtEpochMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_monitor_runs` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `scheduledAtEpochMs` INTEGER NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `completedAtEpochMs` INTEGER, `status` TEXT NOT NULL, `safeErrorCode` TEXT, `result` TEXT, `providerId` TEXT, `modelId` TEXT, `inputTokens` INTEGER, `outputTokens` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `scheduled_monitor_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_monitor_runs_taskId_startedAtEpochMs` ON `scheduled_monitor_runs` (`taskId`, `startedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_monitor_runs_status_startedAtEpochMs` ON `scheduled_monitor_runs` (`status`, `startedAtEpochMs`)")
            }
        }
        /** Response-owned Token and cost facts enrich the existing model attribution without content storage. */
        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `inputTokens` INTEGER")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `outputTokens` INTEGER")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `totalTokens` INTEGER")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `cachedInputTokens` INTEGER")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `costPriceVersion` TEXT")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `costCurrencyCode` TEXT")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `costTotalMicros` INTEGER")
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `costSource` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_response_model_attributions_costTotalMicros_recordedAtEpochMs` ON `assistant_response_model_attributions` (`costTotalMicros`, `recordedAtEpochMs`)")
            }
        }
        /** Links new chat failures to their local conversation without rewriting retained diagnostics. */
        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `debug_call_log` ADD COLUMN `conversationId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_debug_call_log_conversationId_createdAtEpochMs` ON `debug_call_log` (`conversationId`, `createdAtEpochMs`)")
            }
        }
        /** Keeps only Qwen reminder-refinement accounting, never its source conversation text. */
        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_draft_generation_records` (`id` TEXT NOT NULL, `sourceConversationId` TEXT NOT NULL, `requestedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `providerId` TEXT, `modelId` TEXT, `inputTokens` INTEGER, `outputTokens` INTEGER, `cachedInputTokens` INTEGER, `costPriceVersion` TEXT, `costCurrencyCode` TEXT, `costTotalMicros` INTEGER, `costSource` TEXT, `safeErrorCode` TEXT, PRIMARY KEY(`id`))")
            }
        }
        /** Keeps Qwen title-refinement accounting separate from ordinary replies and reminders. */
        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_title_generation_records` (`id` TEXT NOT NULL, `sourceConversationId` TEXT NOT NULL, `requestedAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `providerId` TEXT, `modelId` TEXT, `inputTokens` INTEGER, `outputTokens` INTEGER, `cachedInputTokens` INTEGER, `costPriceVersion` TEXT, `costCurrencyCode` TEXT, `costTotalMicros` INTEGER, `costSource` TEXT, `safeErrorCode` TEXT, PRIMARY KEY(`id`))")
            }
        }
        /** Historical schema bridge only; the execution-mode column is inert after gateway rollback. */
        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `normal_chat_send_attempts` ADD COLUMN `executionMode` TEXT NOT NULL DEFAULT 'DIRECT_LOCAL'")
            }
        }
        /** Historical schema bridge only; terminal gateway metadata is inert after rollback. */
        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `normal_chat_send_attempts` ADD COLUMN `finalGatewaySequence` INTEGER")
                db.execSQL("ALTER TABLE `normal_chat_send_attempts` ADD COLUMN `gatewayFinalAcknowledgedAtEpochMs` INTEGER")
            }
        }
        /** Historical schema bridge only; no server task is created or resumed by this app. */
        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `normal_chat_send_attempts` ADD COLUMN `gatewayTaskId` TEXT")
            }
        }
        /** Favorites are a local, reversible conversation-management marker. */
        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `favoritedAtEpochMs` INTEGER")
            }
        }
        /** Preserves source-message identity for safe old-export then newer-export append merges. */
        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `p6k_zip_import_message_provenance` (`conversationId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `contentHash` TEXT NOT NULL, PRIMARY KEY(`conversationId`, `sourceMessageId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_import_message_provenance_conversationId` ON `p6k_zip_import_message_provenance` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_import_message_provenance_messageId` ON `p6k_zip_import_message_provenance` (`messageId`)")
            }
        }
        /** Durable, content-free checkpoints for resumable ChatGPT ZIP attachment restoration. */
        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `p6k_zip_asset_recovery_jobs` (`taskId` TEXT NOT NULL, `state` TEXT NOT NULL, `totalOccurrences` INTEGER NOT NULL, `linkedOccurrences` INTEGER NOT NULL, `totalConversations` INTEGER NOT NULL, `processedConversations` INTEGER NOT NULL, `failedConversations` INTEGER NOT NULL, `uniqueAssets` INTEGER NOT NULL, `missingEntries` INTEGER NOT NULL, `unattributedCandidates` INTEGER NOT NULL, `lastFailureKind` TEXT, `lastFailureAtMs` INTEGER, `indexVersion` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`taskId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_recovery_jobs_state_updatedAtMs` ON `p6k_zip_asset_recovery_jobs` (`state`, `updatedAtMs`)")
            }
        }
        /** Adds explicit source-record and fallback-name counters for honest ZIP result reporting. */
        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `p6k_zip_asset_recovery_jobs` ADD COLUMN `sourceReferenceRecords` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `p6k_zip_asset_recovery_jobs` ADD COLUMN `fallbackNamedAssets` INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** Records image-gen originals recovered from OpenAI's library manifest by bounded time. */
        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `p6k_zip_asset_recovery_jobs` ADD COLUMN `inferredGeneratedImages` INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** Distinguishes exact library-origin recovery from bounded anonymous image inference. */
        val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `p6k_zip_asset_recovery_jobs` ADD COLUMN `originLinkedLibraryImages` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `p6k_zip_asset_recovery_jobs` ADD COLUMN `inferredLibraryImages` INTEGER NOT NULL DEFAULT 0")
            }
        }
        /** Splits unique ZIP bytes from message occurrences and one-to-one link receipts. */
        val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `p6k_zip_asset_catalog` (`taskId` TEXT NOT NULL, `entryName` TEXT NOT NULL, `sha256` TEXT, `byteCount` INTEGER, `mimeType` TEXT, `displayName` TEXT, `attachmentId` TEXT, `missingInArchive` INTEGER NOT NULL, `verificationState` TEXT NOT NULL, PRIMARY KEY(`taskId`, `entryName`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_catalog_attachmentId` ON `p6k_zip_asset_catalog` (`attachmentId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `p6k_zip_asset_occurrence` (`taskId` TEXT NOT NULL, `entryName` TEXT NOT NULL, `sourceConversationId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, PRIMARY KEY(`taskId`, `entryName`, `sourceConversationId`, `sourceMessageId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_occurrence_taskId_sourceConversationId` ON `p6k_zip_asset_occurrence` (`taskId`, `sourceConversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_occurrence_taskId_entryName` ON `p6k_zip_asset_occurrence` (`taskId`, `entryName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `p6k_zip_asset_occurrence_receipt` (`taskId` TEXT NOT NULL, `entryName` TEXT NOT NULL, `sourceConversationId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `messageId` TEXT NOT NULL, `attachmentId` TEXT NOT NULL, `linkedAtMs` INTEGER NOT NULL, PRIMARY KEY(`taskId`, `entryName`, `sourceConversationId`, `sourceMessageId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_occurrence_receipt_attachmentId` ON `p6k_zip_asset_occurrence_receipt` (`attachmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_occurrence_receipt_conversationId` ON `p6k_zip_asset_occurrence_receipt` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_p6k_zip_asset_occurrence_receipt_messageId` ON `p6k_zip_asset_occurrence_receipt` (`messageId`)")
                db.execSQL("INSERT OR IGNORE INTO p6k_zip_asset_catalog (taskId,entryName,sha256,byteCount,mimeType,displayName,attachmentId,missingInArchive,verificationState) SELECT c.taskId,c.entryName,NULLIF(c.sha256,''),c.byteCount,c.mimeType,p.displayName,c.attachmentId,0,CASE WHEN c.sourceConversationId IS NOT NULL AND c.sourceMessageId IS NOT NULL THEN 'LEGACY_BACKFILLED' ELSE 'NEEDS_REVERIFY' END FROM p6k_zip_asset_candidates c LEFT JOIN private_attachment_assets p ON p.attachmentId=c.attachmentId")
                db.execSQL("INSERT OR IGNORE INTO p6k_zip_asset_occurrence (taskId,entryName,sourceConversationId,sourceMessageId) SELECT r.taskId,r.entryName,c.sourceConversationId,c.sourceMessageId FROM p6k_zip_asset_link_receipts r JOIN p6k_zip_asset_candidates c ON c.taskId=r.taskId AND c.entryName=r.entryName WHERE c.sourceConversationId IS NOT NULL AND c.sourceMessageId IS NOT NULL")
                db.execSQL("INSERT OR IGNORE INTO p6k_zip_asset_occurrence_receipt (taskId,entryName,sourceConversationId,sourceMessageId,conversationId,messageId,attachmentId,linkedAtMs) SELECT r.taskId,r.entryName,c.sourceConversationId,c.sourceMessageId,r.conversationId,r.messageId,r.attachmentId,r.committedAtEpochMs FROM p6k_zip_asset_link_receipts r JOIN p6k_zip_asset_candidates c ON c.taskId=r.taskId AND c.entryName=r.entryName WHERE c.sourceConversationId IS NOT NULL AND c.sourceMessageId IS NOT NULL")
            }
        }
        /** Adds content-free receipts for explicit single-conversation cloud sync only. */
        val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `manual_conversation_sync_state` (`accountRef` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `remoteRevision` INTEGER NOT NULL, `payloadHash` TEXT NOT NULL, `localContentHash` TEXT NOT NULL, `lastSyncedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`accountRef`, `conversationId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_conversation_sync_state_conversationId` ON `manual_conversation_sync_state` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_conversation_sync_state_lastSyncedAtEpochMs` ON `manual_conversation_sync_state` (`lastSyncedAtEpochMs`)")
            }
        }
        /** Adds source-to-Markdown lineage for explicit GLM-OCR document conversion tasks. */
        val MIGRATION_61_62 = object : Migration(61, 62) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `glm_ocr_tasks` (`taskId` TEXT NOT NULL, `sourceAttachmentId` TEXT NOT NULL, `resultAttachmentId` TEXT, `sourceDisplayName` TEXT NOT NULL, `sourceMimeType` TEXT NOT NULL, `sourceByteCount` INTEGER NOT NULL, `sourceSha256` TEXT NOT NULL, `status` TEXT NOT NULL, `requestId` TEXT, `pageCount` INTEGER, `inputTokens` INTEGER, `outputTokens` INTEGER, `costCnyMicros` INTEGER, `safeErrorCode` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`taskId`), FOREIGN KEY(`sourceAttachmentId`) REFERENCES `private_attachment_assets`(`attachmentId`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`resultAttachmentId`) REFERENCES `private_attachment_assets`(`attachmentId`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glm_ocr_tasks_sourceAttachmentId` ON `glm_ocr_tasks` (`sourceAttachmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glm_ocr_tasks_resultAttachmentId` ON `glm_ocr_tasks` (`resultAttachmentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_glm_ocr_tasks_status_updatedAtEpochMs` ON `glm_ocr_tasks` (`status`, `updatedAtEpochMs`)")
            }
        }
        /** Persists provider-reported reasoning usage separately from visible answer tokens. */
        val MIGRATION_62_63 = object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `assistant_response_model_attributions` ADD COLUMN `reasoningTokens` INTEGER")
            }
        }
    }
}
