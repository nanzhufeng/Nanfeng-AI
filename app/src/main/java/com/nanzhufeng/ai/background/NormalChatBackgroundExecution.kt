package com.nanzhufeng.ai.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.nanzhufeng.ai.R
import com.nanzhufeng.ai.ai.NormalChatOpenRouterExecutor
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.ConversationId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class NormalChatBackgroundOperation { SEND, RETRY }

/** Starts the Android foreground-service execution owner using only opaque local ids. */
interface NormalChatBackgroundExecution {
    fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation = NormalChatBackgroundOperation.SEND): Boolean
    fun cancel(conversationId: ConversationId)
    fun ownsExecution(): Boolean
    fun isRunning(conversationId: ConversationId): Boolean
}

/** Unit-test/local fallback: the ViewModel remains owner only where Android services do not exist. */
object NoopNormalChatBackgroundExecution : NormalChatBackgroundExecution {
    override fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation) = true
    override fun cancel(conversationId: ConversationId) = Unit
    override fun ownsExecution() = false
    override fun isRunning(conversationId: ConversationId) = false
}

class AndroidNormalChatBackgroundExecution(private val context: Context) : NormalChatBackgroundExecution {
    override fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation): Boolean = runCatching {
        context.startForegroundService(
            Intent(context, NormalChatGenerationForegroundService::class.java)
                .setAction(NormalChatGenerationForegroundService.ACTION_BEGIN)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId.value)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_OPERATION, operation.name),
        )
        true
    }.getOrDefault(false)

    override fun cancel(conversationId: ConversationId) {
        context.startService(
            Intent(context, NormalChatGenerationForegroundService::class.java)
                .setAction(NormalChatGenerationForegroundService.ACTION_CANCEL)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId.value),
        )
    }

    override fun ownsExecution() = true
    override fun isRunning(conversationId: ConversationId) = NormalChatGenerationRegistry.isRunning(conversationId)
}

/** In-process truth used only to avoid marking an active foreground request as interrupted. */
object NormalChatGenerationRegistry {
    private val activeConversationIds = ConcurrentHashMap.newKeySet<String>()
    fun markRunning(conversationId: ConversationId) { activeConversationIds += conversationId.value }
    fun markFinished(conversationId: ConversationId) { activeConversationIds -= conversationId.value }
    fun isRunning(conversationId: ConversationId) = conversationId.value in activeConversationIds
    fun hasActiveExecution() = activeConversationIds.isNotEmpty()
}

class NormalChatGenerationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<ConversationId, Job>()
    private val container by lazy { AppContainer(applicationContext) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_BEGIN -> begin(intent)
            ACTION_CANCEL -> cancel(intent)
            ACTION_CANCEL_ALL -> jobs.keys.toList().forEach(container.normalChatOpenRouterExecutor::cancelActive)
        }
        // Android must never recreate a completed/unknown provider request. A process death is
        // represented as UNKNOWN and can only be retried by the user with its original key.
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?.takeIf(String::isNotBlank)?.let(::ConversationId) ?: return
        val operation = intent.getStringExtra(EXTRA_OPERATION)
            ?.let { raw -> NormalChatBackgroundOperation.entries.firstOrNull { it.name == raw } }
            ?: NormalChatBackgroundOperation.SEND
        showOngoingNotification()
        acquireWakeLockForSync()
        synchronized(jobs) {
            if (jobs.containsKey(conversationId)) return
            NormalChatGenerationRegistry.markRunning(conversationId)
            publishExecutionState(conversationId, running = true)
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = when (operation) {
                        NormalChatBackgroundOperation.SEND -> container.normalChatOpenRouterExecutor.execute(
                            conversationId,
                            onLocalSubmission = { publishExecutionState(conversationId, running = true) },
                        )
                        NormalChatBackgroundOperation.RETRY -> container.normalChatOpenRouterExecutor.retryLatestAttempt(conversationId)
                    }
                    publishExecutionState(conversationId, running = false, safeResult = result.toSafeResult())
                } finally {
                    jobs.remove(conversationId)
                    NormalChatGenerationRegistry.markFinished(conversationId)
                    publishExecutionState(conversationId, running = false)
                    if (jobs.isEmpty()) {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE).also { stopSelf() }
                    }
                }
            }
            jobs[conversationId] = job
            job.start()
        }
    }

    private fun cancel(intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?.takeIf(String::isNotBlank)?.let(::ConversationId) ?: return
        container.normalChatOpenRouterExecutor.cancelActive(conversationId)
    }

    private fun publishExecutionState(conversationId: ConversationId, running: Boolean, safeResult: String? = null) {
        sendBroadcast(
            Intent(ACTION_EXECUTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId.value)
                .putExtra(EXTRA_RUNNING, running)
                .apply { safeResult?.let { putExtra(EXTRA_SAFE_RESULT, it) } },
        )
    }

    private fun showOngoingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "回复生成", NotificationManager.IMPORTANCE_LOW).apply {
            description = "南枫 AI 正在后台生成回复"
            setShowBadge(false)
        })
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nanfeng_send_rounded)
            .setContentTitle("南枫 AI 正在生成回复")
            .setContentText("离开应用后将继续生成；点按可返回对话。")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { launchPendingIntent?.let(::setContentIntent) }
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_nanfeng_send_rounded),
                    "停止生成",
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, NormalChatGenerationForegroundService::class.java).setAction(ACTION_CANCEL_ALL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        // Service destruction is not an in-app Stop action. The direct provider request may fail,
        // but this path never creates a server-side task or retries it automatically.
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android's budget must not turn into a hidden remote cancellation or a direct retry.
        stopSelf(startId)
    }

    private fun acquireWakeLockForSync() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:generation-sync").apply {
            // Hard cap: this protects only the currently active direct request.
            acquire(TimeUnit.MINUTES.toMillis(2))
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        wakeLock = null
    }

    companion object {
        const val ACTION_BEGIN = "com.nanzhufeng.ai.action.NORMAL_CHAT_GENERATION_BEGIN"
        const val ACTION_CANCEL = "com.nanzhufeng.ai.action.NORMAL_CHAT_GENERATION_CANCEL"
        const val ACTION_CANCEL_ALL = "com.nanzhufeng.ai.action.NORMAL_CHAT_GENERATION_CANCEL_ALL"
        const val ACTION_EXECUTION_STATE_CHANGED = "com.nanzhufeng.ai.action.NORMAL_CHAT_GENERATION_STATE_CHANGED"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_SAFE_RESULT = "safeResult"
        private const val CHANNEL_ID = "normal_chat_generation"
        private const val NOTIFICATION_ID = 4107
    }
}

private fun NormalChatOpenRouterExecutor.Result.toSafeResult(): String? = when (this) {
    NormalChatOpenRouterExecutor.Result.Sent -> null
    is NormalChatOpenRouterExecutor.Result.SentWithNotice -> "NOTICE:${notice.name}"
    is NormalChatOpenRouterExecutor.Result.Blocked -> "BLOCKED:${code.name}"
    is NormalChatOpenRouterExecutor.Result.Failed -> "FAILED:${code.name}"
}
