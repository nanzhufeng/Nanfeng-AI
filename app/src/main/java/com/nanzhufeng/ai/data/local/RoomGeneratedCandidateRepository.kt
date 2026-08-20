package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.CandidateId
import com.nanzhufeng.ai.domain.CandidateReviewStatus
import com.nanzhufeng.ai.domain.CaptureDraftId
import com.nanzhufeng.ai.domain.GeneratedCandidate
import com.nanzhufeng.ai.domain.GeneratedCandidateRepository
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.StoredGeneratedCandidate
import com.nanzhufeng.ai.domain.AiTaskId
import java.time.Instant
import java.util.concurrent.Callable

class RoomGeneratedCandidateRepository(private val database: NanfengAiDatabase) : GeneratedCandidateRepository {
    override fun save(candidate: StoredGeneratedCandidate): StoredGeneratedCandidate = database.inTransaction {
        val dao = database.generatedCandidateDao()
        val existing = dao.findById(candidate.candidate.id.value)
        if (existing == null) {
            dao.insert(candidate.toEntity())
            candidate
        } else {
            val restored = existing.toDomain()
            require(restored == candidate) { "候选 ID 冲突且内容不同。" }
            restored
        }
    }

    override fun findById(id: CandidateId): StoredGeneratedCandidate? =
        database.generatedCandidateDao().findById(id.value)?.toDomain()

    override fun findLatestPendingReview(): StoredGeneratedCandidate? = database.generatedCandidateDao()
        .findLatestByStatus(CandidateReviewStatus.PENDING_REVIEW.name)
        ?.toDomain()

    override fun updateStatus(id: CandidateId, status: CandidateReviewStatus, updatedAt: Instant): StoredGeneratedCandidate =
        database.inTransaction {
            val dao = database.generatedCandidateDao()
            require(dao.updateStatus(id.value, status.name, updatedAt.toEpochMilli()) == 1) { "找不到候选：${id.value}" }
            requireNotNull(dao.findById(id.value)).toDomain()
        }
}

private fun StoredGeneratedCandidate.toEntity() = GeneratedCandidateEntity(
    id = candidate.id.value,
    taskId = candidate.taskId.value,
    draftId = draftId.value,
    invocationId = invocationId.value,
    providerId = providerId.name,
    modelId = modelId,
    harnessVersion = harnessVersion,
    title = candidate.title,
    body = candidate.body,
    generatedAtEpochMs = candidate.generatedAt.toEpochMilli(),
    schemaVersion = candidate.schemaVersion,
    status = status.name,
    updatedAtEpochMs = updatedAt.toEpochMilli(),
)

private fun GeneratedCandidateEntity.toDomain() = StoredGeneratedCandidate(
    candidate = GeneratedCandidate(
        id = CandidateId(id),
        taskId = AiTaskId(taskId),
        title = title,
        body = body,
        generatedAt = Instant.ofEpochMilli(generatedAtEpochMs),
        schemaVersion = schemaVersion,
    ),
    draftId = CaptureDraftId(draftId),
    invocationId = InvocationId(invocationId),
    providerId = ProviderId.valueOf(providerId),
    modelId = modelId,
    harnessVersion = harnessVersion,
    status = CandidateReviewStatus.valueOf(status),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

private fun <T> RoomDatabase.inTransaction(action: () -> T): T = runInTransaction(Callable { action() })
