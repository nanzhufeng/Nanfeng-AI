package com.nanzhufeng.ai.data.local

import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderDiagnosticRecord
import com.nanzhufeng.ai.domain.ProviderDiagnosticStore
import com.nanzhufeng.ai.domain.ProviderId
import java.time.Instant

/** Database owner for the seven-day, redacted diagnostics lane. */
class RoomProviderDiagnosticStore(private val database: NanfengAiDatabase) : ProviderDiagnosticStore {
    override fun append(record: ProviderDiagnosticRecord) = database.runInTransaction {
        val dao = database.providerDiagnosticDao()
        dao.deleteBefore(record.createdAt.minusSeconds(RETENTION_SECONDS).toEpochMilli())
        dao.insert(record.toEntity())
        dao.trimTo(MAX_RECORDS)
    }

    override fun recent(limit: Int): List<ProviderDiagnosticRecord> =
        database.providerDiagnosticDao().recent(limit.coerceIn(1, MAX_RECORDS)).map { it.toDomain() }

    private companion object {
        const val RETENTION_SECONDS = 7L * 24 * 60 * 60
        const val MAX_RECORDS = 500
    }
}

private fun ProviderDiagnosticRecord.toEntity() = ProviderDiagnosticEntity(
    id, createdAt.toEpochMilli(), conversationId, providerId.name, endpointHost, apiModelId, httpStatus,
    errorClass.name, redactedBody, requestShape, latencyMs, timeToFirstByteMs,
)

private fun ProviderDiagnosticEntity.toDomain() = ProviderDiagnosticRecord(
    id, Instant.ofEpochMilli(createdAtEpochMs), conversationId, ProviderId.valueOf(providerId), endpointHost,
    apiModelId, httpStatus, ProviderDiagnosticErrorClass.valueOf(errorClass), redactedBody,
    requestShape, latencyMs, timeToFirstByteMs,
)
