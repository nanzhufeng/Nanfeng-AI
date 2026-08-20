package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import com.nanzhufeng.ai.data.local.SyncJobEntity
import com.nanzhufeng.ai.data.local.SyncJobReceiptEntity
import com.nanzhufeng.ai.domain.P7DSyncJob
import com.nanzhufeng.ai.domain.P7DSyncReceipt
import com.nanzhufeng.ai.domain.P7DSyncStage
import com.nanzhufeng.ai.domain.P7DStateStore

/** Room 19 owner for safe orchestration metadata; it has no API for business bodies or secrets. */
class RoomP7DStateStore(private val database: NanfengAiDatabase, private val now: () -> Long = { System.currentTimeMillis() }) : P7DStateStore {
    private val dao get() = database.syncJobDao()
    override fun job(accountRef: String): P7DSyncJob? = dao.job(accountRef)?.toDomain()
    override fun receipt(intentId: String): P7DSyncReceipt? = dao.receipt(intentId)?.toDomain()
    override fun save(job: P7DSyncJob, receipt: P7DSyncReceipt) = database.runInTransaction {
        dao.save(job.toEntity(now()))
        dao.saveReceipt(receipt.toEntity(now()))
    }
    private fun SyncJobEntity.toDomain() = P7DSyncJob(accountRef, generation, completedGeneration, P7DSyncStage.valueOf(stage), lastLocalRevision, lastLocalHash, lastRemoteRevision, lastRemoteHash, stagingRef, stagingHash, stagingBytes, lastErrorCode)
    private fun SyncJobReceiptEntity.toDomain() = P7DSyncReceipt(intentId, accountRef, generation, P7DSyncStage.valueOf(stage))
    private fun P7DSyncJob.toEntity(timestamp: Long) = SyncJobEntity(accountRef, generation, completedGeneration, stage.name, lastLocalRevision, lastLocalHash, lastRemoteRevision, lastRemoteHash, stagingRef, stagingHash, stagingBytes, lastErrorCode, timestamp)
    private fun P7DSyncReceipt.toEntity(timestamp: Long) = SyncJobReceiptEntity(intentId, accountRef, generation, stage.name, timestamp)
}
