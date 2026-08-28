package com.nanzhufeng.ai.data

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
import com.nanzhufeng.ai.domain.P6KZipTaskId

/** One durable, non-expedited long-running recovery request per ZIP task. */
class AndroidP6KZipAssetRecoveryScheduler(
    context: Context,
    private val jobs: P6KZipAssetRecoveryJobRepository,
) : P6KZipAssetRecoveryScheduler {
    private val appContext = context.applicationContext
    private val manager: WorkManager by lazy { configuredWorkManager(appContext) }

    override fun enqueue(taskId: P6KZipTaskId) {
        val request = OneTimeWorkRequestBuilder<P6KZipAssetRecoveryWorker>()
            .setInputData(workDataOf(KEY_TASK_ID to taskId.value))
            .build()
        manager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.KEEP, request)
    }

    override fun resumePending() {
        jobs.resumable().forEach { enqueue(it.taskId) }
    }

    override fun cancel(taskId: P6KZipTaskId) {
        manager.cancelUniqueWork(workName(taskId))
    }

    private fun workName(taskId: P6KZipTaskId) = "nfai.p6k-asset-recovery.${taskId.value}"

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
        setForeground(recoveryForegroundInfo(applicationContext))
        val store = com.nanzhufeng.ai.app.AppContainer(applicationContext).p6kZipIntakeStore
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

private fun recoveryForegroundInfo(context: Context): ForegroundInfo {
    val channelId = "zip_asset_recovery"
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager?.createNotificationChannel(NotificationChannel(channelId, "ZIP 附件恢复", NotificationManager.IMPORTANCE_LOW))
    }
    val intent = Intent(context, NanfengAiActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        73_100,
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
        ForegroundInfo(73_100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(73_100, notification)
    }
}
