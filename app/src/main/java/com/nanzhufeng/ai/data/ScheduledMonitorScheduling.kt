package com.nanzhufeng.ai.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nanzhufeng.ai.NanfengAiActivity
import com.nanzhufeng.ai.R
import com.nanzhufeng.ai.domain.ScheduledMonitorStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorTask
import com.nanzhufeng.ai.domain.ScheduledMonitorTaskId
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * One unique, connected-only request per task. The next request is enqueued only after a
 * completed attempt records its visible result or safe error in Room.
 */
class AndroidScheduledMonitorScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val manager: WorkManager by lazy { configuredWorkManager(appContext) }
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(task: ScheduledMonitorTask) {
        if (task.status != ScheduledMonitorStatus.ACTIVE) {
            cancel(task.id)
            return
        }
        val delayMillis = Duration.between(Instant.now(), task.nextRunAt).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledMonitorWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_TASK_ID to task.id.value))
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(taskId: ScheduledMonitorTaskId) {
        manager.cancelUniqueWork(workName(taskId))
    }

    private fun workName(taskId: ScheduledMonitorTaskId) = "nfai.scheduled-monitor.${taskId.value}"

    private fun configuredWorkManager(context: Context): WorkManager {
        // Manifest deliberately removes Startup's provider. This feature is the explicit runtime
        // owner that initializes WorkManager after a user creates a monitor, never at app launch.
        runCatching {
            WorkManager.initialize(
                context,
                Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build(),
            )
        }
        return WorkManager.getInstance(context)
    }

    private companion object { const val KEY_TASK_ID = "scheduled_monitor_task_id" }
}

class ScheduledMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)?.let(::ScheduledMonitorTaskId) ?: return Result.success()
        val container = com.nanzhufeng.ai.app.AppContainer(applicationContext)
        return when (val result = container.scheduledMonitorExecutor.execute(taskId)) {
            is com.nanzhufeng.ai.ai.ScheduledMonitorExecutor.Result.Completed -> {
                // A user may pause/delete during a running request. Always re-read the owner
                // before chaining or notifying; the executor's pre-request snapshot is stale.
                val current = container.scheduledMonitorRepository.find(result.task.id)
                current?.let { task ->
                    AndroidScheduledMonitorScheduler(applicationContext).schedule(task)
                    if (
                        task.status == ScheduledMonitorStatus.ACTIVE &&
                        container.loadNotificationReminderSettings.execute().monitorResultsNotificationEnabled
                    ) {
                        ScheduledMonitorNotifier.notifyCompleted(applicationContext, task)
                    }
                }
                Result.success()
            }
            is com.nanzhufeng.ai.ai.ScheduledMonitorExecutor.Result.Failed -> {
                result.task?.let { task ->
                    container.scheduledMonitorRepository.find(task.id)?.let { current ->
                        AndroidScheduledMonitorScheduler(applicationContext).schedule(current)
                    }
                }
                Result.success()
            }
            is com.nanzhufeng.ai.ai.ScheduledMonitorExecutor.Result.Cancelled -> Result.success()
        }
    }

    private companion object { const val KEY_TASK_ID = "scheduled_monitor_task_id" }
}

private object ScheduledMonitorNotifier {
    private const val CHANNEL_ID = "scheduled_monitor_results"
    private const val CHANNEL_NAME = "计划监控"
    private const val NOTIFICATION_ID_PREFIX = 72_000

    fun notifyCompleted(context: Context, task: ScheduledMonitorTask) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(context, NanfengAiActivity::class.java)
            .setAction(NanfengAiActivity.ACTION_OPEN_SCHEDULED_MONITOR)
            .putExtra(NanfengAiActivity.EXTRA_SCHEDULED_MONITOR_TASK_ID, task.id.value)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id.value.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nanfeng_send_rounded)
            .setContentTitle(task.title)
            .setContentText("本次监控已完成，点按查看结果")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIFICATION_ID_PREFIX + task.id.value.hashCode(), notification)
    }
}
