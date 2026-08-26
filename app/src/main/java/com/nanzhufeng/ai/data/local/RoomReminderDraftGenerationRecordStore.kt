package com.nanzhufeng.ai.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.ReminderDraftGenerationId
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecord
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.domain.ReminderDraftGenerationStatus
import java.time.Instant

@Entity(tableName = "reminder_draft_generation_records")
data class ReminderDraftGenerationRecordEntity(
    @PrimaryKey val id: String,
    val sourceConversationId: String,
    val requestedAtEpochMs: Long,
    val status: String,
    val providerId: String?,
    val modelId: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val cachedInputTokens: Long?,
    val costPriceVersion: String?,
    val costCurrencyCode: String?,
    val costTotalMicros: Long?,
    val costSource: String?,
    val safeErrorCode: String?,
)

@Dao
interface ReminderDraftGenerationRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: ReminderDraftGenerationRecordEntity)
    @Query("SELECT * FROM reminder_draft_generation_records ORDER BY requestedAtEpochMs DESC, id DESC") fun listNewestFirst(): List<ReminderDraftGenerationRecordEntity>
}

class RoomReminderDraftGenerationRecordStore(private val database: NanfengAiDatabase) : ReminderDraftGenerationRecordStore {
    override fun record(value: ReminderDraftGenerationRecord) = database.reminderDraftGenerationRecordDao().insert(value.toEntity())
    override fun listNewestFirst() = database.reminderDraftGenerationRecordDao().listNewestFirst().map(ReminderDraftGenerationRecordEntity::toDomain)
}

private fun ReminderDraftGenerationRecord.toEntity() = ReminderDraftGenerationRecordEntity(
    id.value, sourceConversationId.value, requestedAt.toEpochMilli(), status.name, providerId?.name, modelId,
    usage.inputTokens, usage.outputTokens, usage.cachedInputTokens, cost.priceVersion, cost.currencyCode,
    cost.totalMicros, costSource?.name, safeErrorCode,
)

private fun ReminderDraftGenerationRecordEntity.toDomain() = ReminderDraftGenerationRecord(
    ReminderDraftGenerationId(id), ConversationId(sourceConversationId), Instant.ofEpochMilli(requestedAtEpochMs),
    ReminderDraftGenerationStatus.valueOf(status), providerId?.let(ProviderId::valueOf), modelId,
    ProviderUsage(inputTokens, outputTokens, cachedInputTokens = cachedInputTokens),
    ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros), costSource?.let(ConversationCostSource::valueOf), safeErrorCode,
)
