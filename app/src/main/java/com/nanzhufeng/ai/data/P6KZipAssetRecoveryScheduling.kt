package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.ConversationSurface

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nanzhufeng.ai.NanfengAiActivity
import com.nanzhufeng.ai.R
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJobRepository
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryScheduler
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryState
import com.nanzhufeng.ai.domain.P6KZipAssetRecoveryJob
import com.nanzhufeng.ai.domain.P6KZipImportTaskRepository
import com.nanzhufeng.ai.domain.P6KZipTaskStatus
import com.nanzhufeng.ai.domain.P6KZipTaskId
import com.nanzhufeng.ai.domain.P6K_ZIP_ASSET_MAPPING_INDEX_VERSION
import com.nanzhufeng.ai.domain.ThirdPartyZipProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One durable, non-expedited long-running recovery request per ZIP task. */
class AndroidP6KZipAssetRecoveryScheduler(
    context: Context,
    private val jobs: P6KZipAssetRecoveryJobRepository,
    private val tasks: P6KZipImportTaskRepository,
    private val dataArea: com.nanzhufeng.ai.domain.ConversationSurface = com.nanzhufeng.ai.domain.ConversationSurface.CHAT,
) : P6KZipAssetRecoveryScheduler {
    private val appContext = context.applicationContext
    private val manager: WorkManager by lazy { configuredWorkManager(appContext) }
    private val resumeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun enqueue(taskId: P6KZipTaskId) {
        val request = OneTimeWorkRequestBuilder<P6KZipAssetRecoveryWorker>()
            .setInputData(workDataOf("dataArea" to dataArea.name, KEY_TASK_ID to taskId.value))
            .build()
        manager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.KEEP, request)
    }

    override fun resumePending() {
        resumeScope.launch {
            migrateLegacyJobs()
            jobs.resumable().forEach { enqueue(it.taskId) }
        }
    }

    override fun cancel(taskId: P6KZipTaskId) {
        manager.cancelUniqueWork(workName(taskId))
    }

    private fun workName(taskId: P6KZipTaskId) = "nfai.p6k-asset-recovery.${if (dataArea == com.nanzhufeng.ai.domain.ConversationSurface.CHAT) "" else "WORK."}${taskId.value}"

    /** One-time bridge for imports created before the Room recovery job existed. */
    private fun migrateLegacyJobs() {
        val root = File(appContext.filesDir, "p6k-zip-import/v1")
        val archives = File(root, "archives")
        tasks.list().forEach { task ->
            val existing = jobs.find(task.id)
            if (existing != null) {
                if (task.provider == ThirdPartyZipProvider.CHATGPT &&
                    existing.indexVersion < P6K_ZIP_ASSET_MAPPING_INDEX_VERSION &&
                    File(archives, "${task.id.value}.zip").isFile
                ) {
                    jobs.save(existing.copy(
                        state = P6KZipAssetRecoveryState.PENDING,
                        linkedOccurrences = 0,
                        processedConversations = 0,
                        failedConversations = 0,
                        lastFailureKind = null,
                        lastFailureAtMs = null,
                        indexVersion = P6K_ZIP_ASSET_MAPPING_INDEX_VERSION,
                        updatedAtMs = System.currentTimeMillis(),
                    ))
                }
                return@forEach
            }
            if (task.provider != ThirdPartyZipProvider.CHATGPT) return@forEach
            if (task.status !in setOf(P6KZipTaskStatus.COMPLETED, P6KZipTaskStatus.PARTIALLY_COMPLETED)) return@forEach
            val completedMarker = File(root, "${task.id.value}.assets-v2.done")
            val linked = task.assets.count { it.attachmentId != null }
            val job = when {
                completedMarker.isFile -> P6KZipAssetRecoveryJob(
                    taskId = task.id,
                    state = P6KZipAssetRecoveryState.COMPLETED,
                    totalOccurrences = linked,
                    linkedOccurrences = linked,
                    processedConversations = task.items.mapNotNull { it.conversationId }.distinct().size,
                    uniqueAssets = linked,
                    updatedAtMs = System.currentTimeMillis(),
                )
                File(archives, "${task.id.value}.zip").isFile -> P6KZipAssetRecoveryJob(
                    taskId = task.id,
                    state = P6KZipAssetRecoveryState.PENDING,
                    updatedAtMs = System.currentTimeMillis(),
                )
                else -> null
            }
            job?.let(jobs::save)
            if (job?.state == P6KZipAssetRecoveryState.COMPLETED) completedMarker.delete()
        }
    }

    private fun configuredWorkManager(context: Context): WorkManager {
        runCatching {
            WorkManager.initialize(
                context,
                Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build(),
            )
        }
        return WorkManager.getInstance(context)
    }

    internal companion object { const val KEY_TASK_ID = "p6k_zip_asset_recovery_task_id" }
}

class P6KZipAssetRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(AndroidP6KZipAssetRecoveryScheduler.KEY_TASK_ID) ?: return Result.success()
        setForeground(recoveryForegroundInfo(applicationContext, ConversationSurface.valueOf(inputData.getString("dataArea") ?: "CHAT")))
        val store = com.nanzhufeng.ai.app.AppContainer(applicationContext, dataArea = com.nanzhufeng.ai.domain.ConversationSurface.valueOf(inputData.getString("dataArea") ?: "CHAT")).p6kZipIntakeStore
        return runCatching { store.runAssetRecovery(taskId) }
            .fold(
                onSuccess = { job ->
                    when (job?.state) {
                        P6KZipAssetRecoveryState.COMPLETED, P6KZipAssetRecoveryState.PARTIAL,
                        P6KZipAssetRecoveryState.FAILED, null -> Result.success()
                        else -> Result.retry()
                    }
                },
                onFailure = {
                    store.recordAssetRecoveryInterruption(taskId)
                    Result.success()
                },
            )
    }
}

private fun recoveryForegroundInfo(context: Context, area: ConversationSurface): ForegroundInfo {
    val channelId = "zip_asset_recovery"
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager?.createNotificationChannel(NotificationChannel(channelId, "ZIP 附件恢复", NotificationManager.IMPORTANCE_LOW))
    }
    val intent = Intent(context, if (area == ConversationSurface.WORK) com.nanzhufeng.ai.NanfengWorkActivity::class.java else NanfengAiActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        73_100 + area.ordinal,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_nanfeng_conversation_bubble)
        .setContentTitle("正在恢复 ZIP 附件")
        .setContentText("可返回南枫 AI 查看持久化进度")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(pendingIntent)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(73_100 + area.ordinal, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(73_100 + area.ordinal, notification)
    }
}
