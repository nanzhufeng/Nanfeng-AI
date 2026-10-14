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
import android.os.SystemClock
import com.nanzhufeng.ai.R
import com.nanzhufeng.ai.ai.NormalChatOpenRouterExecutor
import com.nanzhufeng.ai.app.AppContainer
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.NormalChatEgressAuthorization
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

enum class NormalChatBackgroundOperation { SEND, RETRY }

/** Starts the Android foreground-service execution owner using only opaque local ids. */
interface NormalChatBackgroundExecution {
    fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation = NormalChatBackgroundOperation.SEND, authorization: NormalChatEgressAuthorization? = null): Boolean
    fun cancel(conversationId: ConversationId)
    fun ownsExecution(): Boolean
    fun isRunning(conversationId: ConversationId): Boolean
    fun runningConversationIds(): Set<ConversationId>
}

/** Unit-test/local fallback: the ViewModel remains owner only where Android services do not exist. */
object NoopNormalChatBackgroundExecution : NormalChatBackgroundExecution {
    override fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation, authorization: NormalChatEgressAuthorization?) = true
    override fun cancel(conversationId: ConversationId) = Unit
    override fun ownsExecution() = false
    override fun isRunning(conversationId: ConversationId) = false
    override fun runningConversationIds() = emptySet<ConversationId>()
}

class AndroidNormalChatBackgroundExecution(private val context: Context, private val dataArea: ConversationSurface = ConversationSurface.CHAT) : NormalChatBackgroundExecution {
    override fun begin(conversationId: ConversationId, operation: NormalChatBackgroundOperation, authorization: NormalChatEgressAuthorization?): Boolean = runCatching {
        context.startForegroundService(
            Intent(context, if (dataArea == ConversationSurface.CHAT) NormalChatGenerationForegroundService::class.java else WorkChatGenerationForegroundService::class.java)
                .setAction(NormalChatGenerationForegroundService.ACTION_BEGIN)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId.value)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_OPERATION, operation.name)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_APPROVED_AT_MS, authorization?.approvedAtEpochMs)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_DRAFT_FINGERPRINT, authorization?.draftFingerprint)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_DISCLOSURE_VERSION, authorization?.disclosureVersion)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_CHOICE, authorization?.recipientChoiceId)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_PRESETS, authorization?.recipientPresets?.map { it.name }?.toTypedArray())
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_MODELS, authorization?.recipientPresets?.map { authorization.recipientModelIds.getValue(it) }?.toTypedArray())
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_AUX_PRESETS, authorization?.auxiliaryRecipientModelIds?.keys?.map { it.name }?.toTypedArray())
                .putExtra(NormalChatGenerationForegroundService.EXTRA_EGRESS_AUX_MODELS, authorization?.auxiliaryRecipientModelIds?.values?.toTypedArray())
                .putExtra("EGRESS_AUX_SERVICE_IDS",authorization?.auxiliaryServiceEngines?.keys?.toTypedArray())
                .putExtra("EGRESS_AUX_SERVICE_ENGINES",authorization?.auxiliaryServiceEngines?.values?.toTypedArray()),
        )
        true
    }.getOrDefault(false)

    override fun cancel(conversationId: ConversationId) {
        context.startService(
            Intent(context, if (dataArea == ConversationSurface.CHAT) NormalChatGenerationForegroundService::class.java else WorkChatGenerationForegroundService::class.java)
                .setAction(NormalChatGenerationForegroundService.ACTION_CANCEL)
                .putExtra(NormalChatGenerationForegroundService.EXTRA_CONVERSATION_ID, conversationId.value),
        )
    }

    override fun ownsExecution() = true
    override fun isRunning(conversationId: ConversationId) = NormalChatGenerationRegistry.isRunning(conversationId, dataArea)
    override fun runningConversationIds() = NormalChatGenerationRegistry.snapshot(dataArea)
}

/** In-process truth used only to avoid marking an active foreground request as interrupted. */
object NormalChatGenerationRegistry {
    private val activeConversationIds = ConcurrentHashMap.newKeySet<Pair<ConversationSurface, ConversationId>>()
    fun markRunning(conversationId: ConversationId, area: ConversationSurface = ConversationSurface.CHAT) { activeConversationIds += area to conversationId }
    fun markFinished(conversationId: ConversationId, area: ConversationSurface = ConversationSurface.CHAT) { activeConversationIds -= area to conversationId }
    fun isRunning(conversationId: ConversationId, area: ConversationSurface = ConversationSurface.CHAT) = (area to conversationId) in activeConversationIds
    fun hasActiveExecution() = activeConversationIds.isNotEmpty()
    fun snapshot(area: ConversationSurface = ConversationSurface.CHAT): Set<ConversationId> = activeConversationIds.filter { it.first == area }.mapTo(linkedSetOf()) { it.second }
}

class WorkChatGenerationForegroundService : NormalChatGenerationForegroundService() {
    override val dataArea = ConversationSurface.WORK
}

open class NormalChatGenerationForegroundService : Service() {
    protected open val dataArea = ConversationSurface.CHAT
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<ConversationId, Job>()
    /** UI wake-ups are metadata-only and coalesced; an SSE provider can emit many tiny chunks. */
    private val lastProgressPublishedAtMs = ConcurrentHashMap<ConversationId, Long>()
    private val container by lazy { AppContainer(applicationContext, dataArea = dataArea) }
    private var wakeLock: PowerManager.WakeLock? = null
    private val continuity by lazy { GenerationContinuityDiagnostics(applicationContext) }
    private val userStopped = ConcurrentHashMap.newKeySet<ConversationId>()
    private val submittedMessageIds = ConcurrentHashMap<ConversationId, com.nanzhufeng.ai.domain.MessageNodeId>()
    private val submittedUserIds = ConcurrentHashMap<ConversationId, com.nanzhufeng.ai.domain.MessageNodeId>()
    private var metadataRefresh: Job? = null
    private val lastTraceAt = ConcurrentHashMap<ConversationId, Long>()
    private val wakeLease = com.nanzhufeng.ai.domain.GenerationWakeLease()
    private var wakeRenewal: Job? = null
    private var latestStartId = 0
    private var destroying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_BEGIN -> begin(intent)
            ACTION_CANCEL -> cancel(intent)
            ACTION_CANCEL_ALL -> jobs.keys.toList().forEach { userStopped.add(it); container.normalChatOpenRouterExecutor.cancelActive(it) }
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
        val authorization = intent.getLongExtra(EXTRA_EGRESS_APPROVED_AT_MS, 0L)
            .takeIf { it > 0L }
            ?.let { approvedAtEpochMs ->
                val fingerprint = intent.getStringExtra(EXTRA_EGRESS_DRAFT_FINGERPRINT) ?: return
                val version = intent.getStringExtra(EXTRA_EGRESS_DISCLOSURE_VERSION)
                    ?: NormalChatEgressAuthorization.DISCLOSURE_VERSION
                runCatching {
                    val presets = requireNotNull(intent.getStringArrayExtra(EXTRA_EGRESS_PRESETS)).map {
                        com.nanzhufeng.ai.domain.ModelPresetId.valueOf(it)
                    }
                    val modelIds = requireNotNull(intent.getStringArrayExtra(EXTRA_EGRESS_MODELS)).toList()
                    require(presets.size == modelIds.size)
                    val auxiliaryPresets = intent.getStringArrayExtra(EXTRA_EGRESS_AUX_PRESETS).orEmpty().map { com.nanzhufeng.ai.domain.ModelPresetId.valueOf(it) }
                    val auxiliaryModels = intent.getStringArrayExtra(EXTRA_EGRESS_AUX_MODELS).orEmpty().toList()
                    require(auxiliaryPresets.size == auxiliaryModels.size)
                    val serviceIds=intent.getStringArrayExtra("EGRESS_AUX_SERVICE_IDS").orEmpty().toList()
                    val serviceEngines=intent.getStringArrayExtra("EGRESS_AUX_SERVICE_ENGINES").orEmpty().toList()
                    require(serviceIds.size==serviceEngines.size)
                    NormalChatEgressAuthorization(approvedAtEpochMs, fingerprint, version,
                        requireNotNull(intent.getStringExtra(EXTRA_EGRESS_CHOICE)), presets, presets.zip(modelIds).toMap(), auxiliaryPresets.zip(auxiliaryModels).toMap(),serviceIds.zip(serviceEngines).toMap())
                }.getOrNull()
            }
        // Enter foreground immediately. The count is refreshed after this conversation joins
        // the registry, but Android's foreground-service deadline must not wait for I/O.
        showOngoingNotification(activeCount = (jobs.size + 1).coerceAtLeast(1))
        acquireWakeLockForSync()
        synchronized(jobs) {
            if (jobs.containsKey(conversationId)) {
                showOngoingNotification(activeCount = jobs.size.coerceAtLeast(1))
                return
            }
            NormalChatGenerationRegistry.markRunning(conversationId, dataArea)
            runCatching {GenerationIslandRuntime.begin(applicationContext, dataArea, conversationId)}
            continuity.record("START_${operation.name}", "${dataArea.name}:${conversationId.value}")
            publishExecutionState(conversationId, running = true)
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                var safeResult: String? = "UNKNOWN"
                try {
                    val result = when (operation) {
                        NormalChatBackgroundOperation.SEND -> container.normalChatOpenRouterExecutor.execute(
                            conversationId,
                            authorization = authorization,
                            onLocalSubmission = { captureSubmittedMessage(conversationId); publishExecutionState(conversationId, running = true) },
                            onStreamProgress = { publishStreamProgress(conversationId) },
                        )
                        NormalChatBackgroundOperation.RETRY -> container.normalChatOpenRouterExecutor.retryLatestAttempt(
                            conversationId,
                            // The first running broadcast can precede the durable retry
                            // placeholder. Publish again after it is persisted so the visible
                            // conversation redraws in-place before provider output arrives.
                            onLocalSubmission = { captureSubmittedMessage(conversationId); publishExecutionState(conversationId, running = true) },
                            onStreamProgress = { publishStreamProgress(conversationId) },
                        )
                    }
                    safeResult = result.toSafeResult()
                    continuity.record(safeResult?.replace(':', '_') ?: "COMPLETED", "${dataArea.name}:${conversationId.value}")
                } catch (cancelled: java.util.concurrent.CancellationException) {
                    safeResult = if (conversationId in userStopped) "CANCELLED" else "UNKNOWN"
                    throw cancelled
                } catch (_: Exception) {
                    continuity.record("LOCAL_EXECUTION_INTERRUPTED", "${dataArea.name}:${conversationId.value}")
                    // Last service boundary: no exception or raw payload may reach the process handler.
                    safeResult = runCatching { container.normalChatOpenRouterExecutor.recoverInterruptedExecution(conversationId).toSafeResult() }
                        .getOrDefault("FAILED:LOCAL_EXECUTION_INTERRUPTED")
                } finally {
                    // Main serializes this finalization with begin/cancel service commands.
                    // A finishing task cannot remove a newer task's foreground ownership.
                    val finalCharacters=withContext(NonCancellable + Dispatchers.IO) {
                        updateIslandCheckpoint(conversationId)
                        runCatching {submittedMessageIds[conversationId]?.let(container.conversationRepository::savedTextCharacters)}.getOrNull()
                    }
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        GenerationIslandRuntime.finish(dataArea, conversationId, when {
                            safeResult == null || safeResult?.startsWith("NOTICE:") == true -> "COMPLETE"
                            safeResult == "CANCELLED" || safeResult?.contains("CANCELLED") == true -> "CANCELLED"
                            else -> "UNKNOWN"
                        }, finalCharacters)
                        jobs.remove(conversationId)
                        lastProgressPublishedAtMs.remove(conversationId)
                        lastTraceAt.remove(conversationId)
                        submittedUserIds.remove(conversationId)
                        submittedMessageIds.remove(conversationId)
                        userStopped.remove(conversationId)
                        NormalChatGenerationRegistry.markFinished(conversationId, dataArea)
                        // Publish terminal state only after the registry changes, so a simultaneous
                        // UI reload cannot resurrect a just-completed spinner from a stale snapshot.
                        publishExecutionState(conversationId, running = false, safeResult = safeResult)
                        if (jobs.isEmpty()) {
                            metadataRefresh?.cancel(); metadataRefresh = null
                            releaseWakeLock()
                            if (!destroying && stopSelfResult(latestStartId)) stopForeground(STOP_FOREGROUND_REMOVE)
                        } else if (!destroying) {
                            showOngoingNotification(activeCount = jobs.size)
                        }
                    }
                }
            }
            jobs[conversationId] = job
            showOngoingNotification(activeCount = jobs.size)
            job.start()
            if(metadataRefresh?.isActive != true) metadataRefresh=serviceScope.launch {
                while(kotlinx.coroutines.currentCoroutineContext().isActive) {
                    delay(1000);jobs.keys.toList().forEach(::updateIslandCheckpoint)
                    withContext(Dispatchers.Main.immediate) {if(jobs.isNotEmpty() && !destroying) runCatching {showOngoingNotification(jobs.size,updateOnly=true)}}
                }
            }
        }
    }

    private fun cancel(intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
            ?.takeIf(String::isNotBlank)?.let(::ConversationId) ?: return
        continuity.record("USER_STOP", "${dataArea.name}:${conversationId.value}")
        userStopped.add(conversationId)
        container.normalChatOpenRouterExecutor.cancelActive(conversationId)
    }

    /** Deliberately carries no streamed text: Compose reloads the durable local projection. */
    private fun publishStreamProgress(conversationId: ConversationId) {
        val now = SystemClock.elapsedRealtime()
        val tracedAt = lastTraceAt[conversationId]
        if (tracedAt == null || now - tracedAt >= 10_000L) {
            continuity.record(if (tracedAt == null) "FIRST_TEXT" else "PROGRESS", "${dataArea.name}:${conversationId.value}", if (tracedAt == null) 0 else now - tracedAt)
            lastTraceAt[conversationId] = now
        }
        val previous = lastProgressPublishedAtMs[conversationId]
        if (previous != null && now - previous < STREAM_PROGRESS_COALESCE_MS) return
        lastProgressPublishedAtMs[conversationId] = now
        publishExecutionState(conversationId, running = true, progress = true)
    }

    private fun captureSubmittedMessage(conversationId: ConversationId) {
        runCatching {
            val snapshot = container.conversationRepository.findById(conversationId) ?: return
            val id = snapshot.conversation.currentLeafMessageId ?: return
            snapshot.nodes.firstOrNull {it.id==id && it.role==com.nanzhufeng.ai.domain.MessageRole.ASSISTANT}?.let {message ->
                submittedMessageIds[conversationId]=id
                message.parentMessageId?.let {submittedUserIds[conversationId]=it}
            }
        }
    }
    private fun updateIslandCheckpoint(conversationId: ConversationId) {
        runCatching {
            val id = submittedMessageIds[conversationId] ?: return
            val count=container.conversationRepository.savedTextCharacters(id)
            val model=submittedUserIds[conversationId]?.let {container.generationModelLabel(conversationId,it)}.orEmpty()
            GenerationIslandRuntime.checkpoint(dataArea,conversationId,count,model)
        }
    }

    private fun publishExecutionState(conversationId: ConversationId, running: Boolean, safeResult: String? = null, progress: Boolean = false) {
        sendBroadcast(
            Intent(ACTION_EXECUTION_STATE_CHANGED)
                .setPackage(packageName)
                .putExtra("dataArea", dataArea.name)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId.value)
                .putExtra(EXTRA_RUNNING, running)
                .putExtra(EXTRA_PROGRESS, progress)
                .apply { safeResult?.let { putExtra(EXTRA_SAFE_RESULT, it) } },
        )
    }

    private fun showOngoingNotification(activeCount: Int, updateOnly: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "回复生成", NotificationManager.IMPORTANCE_LOW).apply {
            description = "南枫 AI 正在后台生成回复"
            setShowBadge(false)
        })
        val task=GenerationIslandRuntime.tasks.value.firstOrNull {it.area==dataArea && it.running}
        val launchIntent = if (dataArea == ConversationSurface.WORK) Intent(this, com.nanzhufeng.ai.NanfengWorkActivity::class.java) else packageManager.getLaunchIntentForPackage(packageName)
        val launchPendingIntent = launchIntent?.let {
            task?.let {task -> it.putExtra(com.nanzhufeng.ai.CONVERSATION_SHORTCUT_ID_EXTRA,task.conversationId.value)}
            PendingIntent.getActivity(this, dataArea.ordinal, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nanfeng_send_rounded)
            .setContentTitle(if (activeCount > 1) "$activeCount 个对话正在生成" else "南枫 AI 正在生成回复")
            .setContentText(task?.let {"${it.stage} · ${it.detail(SystemClock.elapsedRealtime())}"} ?: "切换对话或界面不会中断；点按可返回应用。")
            .setProgress(0,0,true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { launchPendingIntent?.let(::setContentIntent) }
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_nanfeng_send_rounded),
                    "停止全部生成",
                    PendingIntent.getService(
                        this, 1 + dataArea.ordinal,
                        Intent(this, this::class.java).setAction(ACTION_CANCEL_ALL),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
            .build()
        if(updateOnly) { manager.notify(NOTIFICATION_ID + dataArea.ordinal,notification);return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID + dataArea.ordinal, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(NOTIFICATION_ID + dataArea.ordinal, notification)
    }

    override fun onDestroy() {
        destroying = true
        if (jobs.isNotEmpty()) continuity.record("SERVICE_DESTROY_ACTIVE")
        // Service destruction is not an in-app Stop action. The direct provider request may fail,
        // but this path never creates a server-side task or retries it automatically.
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        continuity.record("SYSTEM_FGS_TIMEOUT")
        // Android's budget must not turn into a hidden remote cancellation or a direct retry.
        stopSelf(startId)
    }

    private fun acquireWakeLockForSync() {
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLease.begin(SystemClock.elapsedRealtime())
        if (wakeLock == null) wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:generation-sync").apply { setReferenceCounted(false) }
        renewWakeLease()
        if (wakeRenewal?.isActive != true) wakeRenewal = serviceScope.launch {
            while (true) {
                delay(30_000)
                withContext(Dispatchers.Main.immediate) { if (jobs.isNotEmpty()) renewWakeLease() }
            }
        }
    }

    private fun renewWakeLease() {
        val timeout = wakeLease.timeoutMs(SystemClock.elapsedRealtime())
        if (timeout > 0) wakeLock?.acquire(timeout)
        else wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun releaseWakeLock() {
        wakeRenewal?.cancel()
        wakeRenewal = null
        wakeLease.finish()
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
        const val EXTRA_EGRESS_APPROVED_AT_MS = "egressApprovedAtMs"
        const val EXTRA_EGRESS_DRAFT_FINGERPRINT = "egressDraftFingerprint"
        const val EXTRA_EGRESS_MODELS = "egressRecipientModels"
        const val EXTRA_EGRESS_AUX_PRESETS = "egressAuxiliaryPresets"
        const val EXTRA_EGRESS_AUX_MODELS = "egressAuxiliaryModels"
        const val EXTRA_EGRESS_CHOICE = "egressRecipientChoice"
        const val EXTRA_EGRESS_PRESETS = "egressRecipientPresets"
        const val EXTRA_EGRESS_DISCLOSURE_VERSION = "egressDisclosureVersion"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_SAFE_RESULT = "safeResult"
        private const val CHANNEL_ID = "normal_chat_generation"
        private const val NOTIFICATION_ID = 4107
        private const val STREAM_PROGRESS_COALESCE_MS = 120L
    }
}

private fun NormalChatOpenRouterExecutor.Result.toSafeResult(): String? = when (this) {
    NormalChatOpenRouterExecutor.Result.Sent -> null
    is NormalChatOpenRouterExecutor.Result.SentWithNotice -> "NOTICE:${notice.name}"
    is NormalChatOpenRouterExecutor.Result.Blocked -> "BLOCKED:${code.name}"
    is NormalChatOpenRouterExecutor.Result.Failed -> "FAILED:${code.name}"
}
