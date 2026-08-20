package com.nanzhufeng.ai.data.local

import androidx.room.RoomDatabase
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.AiTaskId
import com.nanzhufeng.ai.domain.GenerationId
import com.nanzhufeng.ai.domain.GenerationRecord
import com.nanzhufeng.ai.domain.GenerationStatus
import com.nanzhufeng.ai.domain.GenerationValidation
import com.nanzhufeng.ai.domain.InvocationId
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ModelRegistrySnapshotId
import com.nanzhufeng.ai.domain.ProviderAttempt
import com.nanzhufeng.ai.domain.ProviderAttemptId
import com.nanzhufeng.ai.domain.ProviderAttemptStatus
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.TaskRun
import com.nanzhufeng.ai.domain.TaskRunId
import com.nanzhufeng.ai.domain.TaskRunStatus
import com.nanzhufeng.ai.domain.ValidationId
import com.nanzhufeng.ai.domain.ValidationStatus
import java.time.Instant
import java.util.concurrent.Callable

/** Room implementation of the append-only, content-free Invocation Ledger. */
class RoomInvocationRepository(private val database: NanfengAiDatabase) : InvocationRepository {
    override fun save(record: InvocationRecord): InvocationRecord = database.inTransaction {
        val dao = database.invocationLedgerDao()
        val existing = dao.findRecord(record.id.value)
        if (existing != null) {
            val restored = dao.load(record.id) ?: error("调用记录层级不完整：${record.id.value}")
            require(restored == record) { "调用记录 ID 冲突且内容不同。" }
            return@inTransaction restored
        }
        dao.insertBundle(
            record = record.toEntity(),
            taskRun = record.taskRun.toEntity(record.id),
            attempts = record.taskRun.attempts.mapIndexed { position, attempt -> attempt.toEntity(record.taskRun.id, position) },
            generations = record.taskRun.attempts.mapNotNull { attempt -> attempt.generation?.toEntity(attempt.id) },
            validations = record.taskRun.attempts.mapNotNull { attempt ->
                attempt.generation?.validation?.toEntity(attempt.generation.id)
            },
        )
        record
    }

    override fun findById(id: InvocationId): InvocationRecord? = database.invocationLedgerDao().load(id)

    override fun listNewestFirst(): List<InvocationRecord> = database.invocationLedgerDao()
        .listRecordsNewestFirst()
        .map { entity -> database.invocationLedgerDao().load(InvocationId(entity.id)) ?: error("调用记录层级不完整：${entity.id}") }
}

private fun InvocationRecord.toEntity() = InvocationRecordEntity(
    id = id.value,
    taskId = taskId.value,
    providerId = providerId.name,
    modelId = modelId,
    harnessVersion = harnessVersion,
    completedAtEpochMs = completedAt.toEpochMilli(),
    status = status.name,
    errorCode = error.toCode(),
    registrySnapshotId = registrySnapshotId?.value,
    pricingVersion = pricingVersion,
    inputTokens = usage.inputTokens,
    outputTokens = usage.outputTokens,
    totalTokens = usage.totalTokens,
    cachedInputTokens = usage.cachedInputTokens,
    costPriceVersion = cost.priceVersion,
    costCurrencyCode = cost.currencyCode,
    costTotalMicros = cost.totalMicros,
)

private fun TaskRun.toEntity(invocationId: InvocationId) = InvocationTaskRunEntity(
    id = id.value,
    invocationId = invocationId.value,
    taskId = taskId.value,
    startedAtEpochMs = startedAt.toEpochMilli(),
    completedAtEpochMs = completedAt.toEpochMilli(),
    status = status.name,
)

private fun ProviderAttempt.toEntity(taskRunId: TaskRunId, position: Int) = ProviderAttemptEntity(
    id = id.value,
    taskRunId = taskRunId.value,
    position = position,
    providerId = providerId.name,
    modelId = modelId,
    registrySnapshotId = registrySnapshotId?.value,
    startedAtEpochMs = startedAt.toEpochMilli(),
    completedAtEpochMs = completedAt.toEpochMilli(),
    status = status.name,
    errorCode = error.toCode(),
    inputTokens = usage.inputTokens,
    outputTokens = usage.outputTokens,
    totalTokens = usage.totalTokens,
    cachedInputTokens = usage.cachedInputTokens,
    costPriceVersion = cost.priceVersion,
    costCurrencyCode = cost.currencyCode,
    costTotalMicros = cost.totalMicros,
)

private fun GenerationRecord.toEntity(attemptId: ProviderAttemptId) = GenerationEntity(
    id = id.value,
    attemptId = attemptId.value,
    createdAtEpochMs = createdAt.toEpochMilli(),
    status = status.name,
)

private fun GenerationValidation.toEntity(generationId: GenerationId) = GenerationValidationEntity(
    id = id.value,
    generationId = generationId.value,
    completedAtEpochMs = completedAt.toEpochMilli(),
    status = status.name,
    outputContractVersion = outputContractVersion,
    errorCode = error.toCode(),
)

private fun InvocationLedgerDao.load(id: InvocationId): InvocationRecord? = findRecord(id.value)?.let { record ->
    val taskRun = requireNotNull(taskRunFor(record.id)) { "调用记录缺少 Task Run：${record.id}" }
    InvocationRecord(
        id = InvocationId(record.id),
        taskId = AiTaskId(record.taskId),
        providerId = ProviderId.valueOf(record.providerId),
        modelId = record.modelId,
        harnessVersion = record.harnessVersion,
        completedAt = Instant.ofEpochMilli(record.completedAtEpochMs),
        status = InvocationStatus.valueOf(record.status),
        error = record.errorCode.toAiTaskError(),
        registrySnapshotId = record.registrySnapshotId?.let(::ModelRegistrySnapshotId),
        pricingVersion = record.pricingVersion,
        usage = record.toUsage(),
        cost = record.toCost(),
        taskRun = TaskRun(
            id = TaskRunId(taskRun.id),
            taskId = AiTaskId(taskRun.taskId),
            startedAt = Instant.ofEpochMilli(taskRun.startedAtEpochMs),
            completedAt = Instant.ofEpochMilli(taskRun.completedAtEpochMs),
            status = TaskRunStatus.valueOf(taskRun.status),
            attempts = attemptsFor(taskRun.id).map { attempt -> attempt.toDomain(this) },
        ),
    )
}

private fun ProviderAttemptEntity.toDomain(dao: InvocationLedgerDao): ProviderAttempt {
    val generation = dao.generationFor(id)?.let { entity ->
        val validation = requireNotNull(dao.validationFor(entity.id)) { "生成记录缺少校验：${entity.id}" }
        GenerationRecord(
            id = GenerationId(entity.id),
            createdAt = Instant.ofEpochMilli(entity.createdAtEpochMs),
            status = GenerationStatus.valueOf(entity.status),
            validation = GenerationValidation(
                id = ValidationId(validation.id),
                completedAt = Instant.ofEpochMilli(validation.completedAtEpochMs),
                status = ValidationStatus.valueOf(validation.status),
                outputContractVersion = validation.outputContractVersion,
                error = validation.errorCode.toAiTaskError(),
            ),
        )
    }
    return ProviderAttempt(
        id = ProviderAttemptId(id),
        providerId = ProviderId.valueOf(providerId),
        modelId = modelId,
        registrySnapshotId = registrySnapshotId?.let(::ModelRegistrySnapshotId),
        startedAt = Instant.ofEpochMilli(startedAtEpochMs),
        completedAt = Instant.ofEpochMilli(completedAtEpochMs),
        status = ProviderAttemptStatus.valueOf(status),
        error = errorCode.toAiTaskError(),
        usage = toUsage(),
        cost = toCost(),
        generation = generation,
    )
}

private fun InvocationRecordEntity.toUsage() = ProviderUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens)
private fun ProviderAttemptEntity.toUsage() = ProviderUsage(inputTokens, outputTokens, totalTokens, cachedInputTokens)
private fun InvocationRecordEntity.toCost() = ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros)
private fun ProviderAttemptEntity.toCost() = ProviderCost(costPriceVersion, costCurrencyCode, costTotalMicros)

private fun AiTaskError?.toCode(): String? = this?.javaClass?.simpleName

private fun String?.toAiTaskError(): AiTaskError? = when (this) {
    null -> null
    "ConsentRequired" -> AiTaskError.ConsentRequired
    "TaskDraftMismatch" -> AiTaskError.TaskDraftMismatch
    "TextNotSupported" -> AiTaskError.TextNotSupported
    "VisionNotSupported" -> AiTaskError.VisionNotSupported
    "ProviderFailure" -> AiTaskError.ProviderFailure
    "CandidateNotConfirmed" -> AiTaskError.CandidateNotConfirmed
    "ProvenanceMismatch" -> AiTaskError.ProvenanceMismatch
    "AttachmentNotReady" -> AiTaskError.AttachmentNotReady
    "AttachmentImportFailed" -> AiTaskError.AttachmentImportFailed
    "AttachmentSourceUnavailable" -> AiTaskError.AttachmentSourceUnavailable
    "AttachmentUnsupportedType" -> AiTaskError.AttachmentUnsupportedType
    "AttachmentTooLarge" -> AiTaskError.AttachmentTooLarge
    "AttachmentIntegrityMismatch" -> AiTaskError.AttachmentIntegrityMismatch
    "PersistenceConflict" -> AiTaskError.PersistenceConflict
    "CaptureTextBlank" -> AiTaskError.CaptureTextBlank
    "ProviderConfigurationInvalid" -> AiTaskError.ProviderConfigurationInvalid
    "ProviderCredentialMissing" -> AiTaskError.ProviderCredentialMissing
    "ProviderCredentialInvalid" -> AiTaskError.ProviderCredentialInvalid
    "ProviderCredentialStorageFailed" -> AiTaskError.ProviderCredentialStorageFailed
    "ProviderAuthenticationFailed" -> AiTaskError.ProviderAuthenticationFailed
    "ProviderBalanceInsufficient" -> AiTaskError.ProviderBalanceInsufficient
    "ProviderRateLimited" -> AiTaskError.ProviderRateLimited
    "ProviderTimedOut" -> AiTaskError.ProviderTimedOut
    "ProviderNetworkUnavailable" -> AiTaskError.ProviderNetworkUnavailable
    "ProviderUnavailable" -> AiTaskError.ProviderUnavailable
    "ProviderResponseFormatInvalid" -> AiTaskError.ProviderResponseFormatInvalid
    "ProviderSchemaValidationFailed" -> AiTaskError.ProviderSchemaValidationFailed
    "ProviderContextOverflow" -> AiTaskError.ProviderContextOverflow
    "ModelRegistrySnapshotInvalid" -> AiTaskError.ModelRegistrySnapshotInvalid
    "ModelRegistrySnapshotUnverified" -> AiTaskError.ModelRegistrySnapshotUnverified
    "ModelRegistryModelUnavailable" -> AiTaskError.ModelRegistryModelUnavailable
    else -> error("不支持的已持久化调用错误代码：$this")
}

private fun <T> RoomDatabase.inTransaction(action: () -> T): T = runInTransaction(Callable { action() })
