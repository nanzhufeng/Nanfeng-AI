package com.nanzhufeng.ai.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationTitleGenerationId
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecord
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationTitleGenerationStatus
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import java.time.Instant

@Entity(tableName = "conversation_title_generation_records")
data class ConversationTitleGenerationRecordEntity(
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
interface ConversationTitleGenerationRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(value: ConversationTitleGenerationRecordEntity)
    @Query("SELECT * FROM conversation_title_generation_records ORDER BY requestedAtEpochMs DESC, id DESC") fun listNewestFirst(): List<ConversationTitleGenerationRecordEntity>
}

class RoomConversationTitleGenerationRecordStore(private val database: NanfengAiDatabase) : ConversationTitleGenerationRecordStore {
    override fun record(value: ConversationTitleGenerationRecord) = database.conversationTitleGenerationRecordDao().insert(value.toEntity())
    override fun listNewestFirst() = database.conversationTitleGenerationRecordDao().listNewestFirst().map(ConversationTitleGenerationRecordEntity::toDomain)
}

private fun ConversationTitleGenerationRecord.toEntity() = ConversationTitleGenerationRecordEntity(
    id.value, sourceConversationId.value, requestedAt.toEpochMilli(), status.name, providerId?.name, modelId,
    usage.inputTokens, usage.outputTokens, usage.cachedInputTokens, cost.priceVersion, cost.currencyCode,
    cost.totalMicros, costSource?.name, safeErrorCode,
)

private fun ConversationTitleGenerationRecordEntity.toDomain() = ConversationTitleGenerationRecord(
    ConversationTitleGenerationId(id), ConversationId(sourceConversationId), Instant.ofEpochMilli(requestedAtEpochMs),
    ConversationTitleGenerationStatus.valueOf(status), providerId?.let(ProviderId::valueOf), modelId,
    ProviderUsage(inputTokens, outputTokens, cachedInputTokens = cachedInputTokens),
    ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros), costSource?.let(ConversationCostSource::valueOf), safeErrorCode,
)
