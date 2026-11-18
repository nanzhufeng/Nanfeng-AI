package com.nanzhufeng.ai.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nanzhufeng.ai.domain.AutomaticHistoryKnowledgeCurationResult
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationCheckpointStore
import com.nanzhufeng.ai.domain.HistoryKnowledgeCurationReservation
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationBeginResult
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunRecord
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunStatus
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunStore
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationTrigger
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Stores only opaque source hashes; text and model output never leave the owner or enter preferences. */
class AndroidHistoryKnowledgeCurationCheckpointStore(context: Context) : HistoryKnowledgeCurationCheckpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    override fun reserve(conversationId: ConversationId, sourceHash: String): HistoryKnowledgeCurationReservation {
        return synchronized(LOCK) { when (state(conversationId, sourceHash)) {
            CheckpointState.PROCESSED -> HistoryKnowledgeCurationReservation.ALREADY_PROCESSED
            CheckpointState.RUNNING -> {
                writeState(conversationId, sourceHash, CheckpointState.UNKNOWN)
                HistoryKnowledgeCurationReservation.UNKNOWN
            }
            CheckpointState.UNKNOWN -> HistoryKnowledgeCurationReservation.UNKNOWN
            CheckpointState.AVAILABLE, CheckpointState.RETRYABLE_FAILED -> {
                writeState(conversationId, sourceHash, CheckpointState.RUNNING)
                HistoryKnowledgeCurationReservation.RESERVED
            }
        } }
    }

    @Synchronized
    override fun markProcessed(conversationId: ConversationId, sourceHash: String) {
        synchronized(LOCK) {
        val next = (preferences.getStringSet(ENTRIES, emptySet()).orEmpty() + token(conversationId, sourceHash))
            .toList().takeLast(MAX_ENTRIES).toSet()
        check(preferences.edit().putStringSet(ENTRIES, next).commit()) { "自动整理检查点无法写入本机。" }
        writeState(conversationId, sourceHash, CheckpointState.PROCESSED)
        }
    }

    @Synchronized
    override fun markRetryableFailure(conversationId: ConversationId, sourceHash: String) {
        synchronized(LOCK) { writeState(conversationId, sourceHash, CheckpointState.RETRYABLE_FAILED) }
    }

    private fun state(conversationId: ConversationId, sourceHash: String): CheckpointState {
        if (token(conversationId, sourceHash) in preferences.getStringSet(ENTRIES, emptySet()).orEmpty()) return CheckpointState.PROCESSED
        val prefix = statePrefix(conversationId, sourceHash)
        return preferences.getStringSet(STATES, emptySet()).orEmpty()
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfterLast('|')
            ?.let { runCatching { CheckpointState.valueOf(it) }.getOrNull() }
            ?: CheckpointState.AVAILABLE
    }

    private fun writeState(conversationId: ConversationId, sourceHash: String, state: CheckpointState) {
        val prefix = statePrefix(conversationId, sourceHash)
        val next = preferences.getStringSet(STATES, emptySet()).orEmpty()
            .filterNot { it.startsWith(prefix) }
            .plus("$prefix${state.name}")
            .toSet()
        check(preferences.edit().putStringSet(STATES, next).commit()) { "自动整理预留状态无法写入本机。" }
    }

    private fun token(conversationId: ConversationId, sourceHash: String) = "${conversationId.value}|$sourceHash"
    private fun statePrefix(conversationId: ConversationId, sourceHash: String) = "${token(conversationId, sourceHash)}|"

    private enum class CheckpointState { AVAILABLE, RUNNING, PROCESSED, RETRYABLE_FAILED, UNKNOWN }

    private companion object {
        val LOCK = Any()
        const val FILE = "history_knowledge_auto_curation_v1"
        const val ENTRIES = "processed_source_hashes"
        const val STATES = "source_states_v2"
        const val MAX_ENTRIES = 512
    }
}

/** Bounded content-free run history; it is never included in provider call counts or costs. */
class AndroidHistoryKnowledgeAutoCurationRunStore(context: Context) : HistoryKnowledgeAutoCurationRunStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    override fun begin(runId: String, trigger: HistoryKnowledgeAutoCurationTrigger, startedAt: Instant): HistoryKnowledgeAutoCurationBeginResult {
        val records = read()
        val priorIndex = records.indexOfLast { it.runId == runId }
        if (priorIndex >= 0) {
            val prior = records[priorIndex]
            when {
                prior.status == HistoryKnowledgeAutoCurationRunStatus.RUNNING -> {
                    records[priorIndex] = prior.copy(
                        status = HistoryKnowledgeAutoCurationRunStatus.UNKNOWN,
                        reasonCode = "INTERRUPTED_WORK_REDELIVERY",
                        retryable = false,
                        completedAt = startedAt,
                    )
                    write(records)
                    return HistoryKnowledgeAutoCurationBeginResult.INTERRUPTED
                }
                prior.status == HistoryKnowledgeAutoCurationRunStatus.FAILED && prior.retryable -> {
                    records[priorIndex] = prior.copy(status = HistoryKnowledgeAutoCurationRunStatus.RUNNING, reasonCode = null, retryable = false, startedAt = startedAt, completedAt = null)
                    write(records)
                    return HistoryKnowledgeAutoCurationBeginResult.RETRY_STARTED
                }
                // WorkManager deliberately reuses a periodic WorkSpec id for every period.
                // Deduplicate source checkpoints, not all future executions of that WorkSpec.
                trigger == HistoryKnowledgeAutoCurationTrigger.PERIODIC -> Unit
                else -> return HistoryKnowledgeAutoCurationBeginResult.ALREADY_FINISHED
            }
        }
        if (records.lastOrNull()?.status == HistoryKnowledgeAutoCurationRunStatus.RUNNING) {
            val last = records.lastIndex
            records[last] = records[last].copy(
                status = HistoryKnowledgeAutoCurationRunStatus.UNKNOWN,
                reasonCode = "INTERRUPTED_BY_NEW_WINDOW",
                retryable = false,
                completedAt = startedAt,
            )
        }
        records += HistoryKnowledgeAutoCurationRunRecord(runId, trigger, HistoryKnowledgeAutoCurationRunStatus.RUNNING, null, false, startedAt, null)
        while (records.size > MAX_RECORDS) records.removeAt(0)
        write(records)
        return HistoryKnowledgeAutoCurationBeginResult.STARTED
    }

    @Synchronized
    override fun finish(runId: String, status: HistoryKnowledgeAutoCurationRunStatus, reasonCode: String?, retryable: Boolean, completedAt: Instant) {
        val records = read()
        val index = records.indexOfLast { it.runId == runId }
        if (index < 0 || records[index].status != HistoryKnowledgeAutoCurationRunStatus.RUNNING) return
        records[index] = records[index].copy(status = status, reasonCode = reasonCode, retryable = retryable, completedAt = completedAt)
        write(records)
    }

    @Synchronized
    override fun latest(): HistoryKnowledgeAutoCurationRunRecord? = read().lastOrNull()

    private fun read(): MutableList<HistoryKnowledgeAutoCurationRunRecord> = runCatching {
        val array = JSONArray(preferences.getString(RECORDS, "[]") ?: "[]")
        MutableList(array.length()) { index ->
            val raw = array.getJSONObject(index)
            HistoryKnowledgeAutoCurationRunRecord(
                runId = raw.getString("runId"),
                trigger = HistoryKnowledgeAutoCurationTrigger.valueOf(raw.getString("trigger")),
                status = HistoryKnowledgeAutoCurationRunStatus.valueOf(raw.getString("status")),
                reasonCode = raw.optString("reasonCode").takeIf(String::isNotBlank),
                retryable = raw.optBoolean("retryable", false),
                startedAt = Instant.ofEpochMilli(raw.getLong("startedAt")),
                completedAt = raw.takeIf { it.has("completedAt") }?.getLong("completedAt")?.let(Instant::ofEpochMilli),
            )
        }
    }.getOrDefault(mutableListOf())

    private fun write(records: List<HistoryKnowledgeAutoCurationRunRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("runId", record.runId); put("trigger", record.trigger.name); put("status", record.status.name)
                record.reasonCode?.let { put("reasonCode", it) }; put("retryable", record.retryable)
                put("startedAt", record.startedAt.toEpochMilli()); record.completedAt?.let { put("completedAt", it.toEpochMilli()) }
            })
        }
        check(preferences.edit().putString(RECORDS, array.toString()).commit()) { "自动整理运行记录无法写入本机。" }
    }

    private companion object {
        const val FILE = "history_knowledge_auto_curation_runs_v1"
        const val RECORDS = "records"
        const val MAX_RECORDS = 64
    }
}

/** A user-enabled queue: latest completed chat replaces older pending work, while history backfill is slow and bounded. */
class AndroidHistoryKnowledgeAutoCurationScheduler(context: Context, private val dataArea: com.nanzhufeng.ai.domain.ConversationSurface = com.nanzhufeng.ai.domain.ConversationSurface.CHAT) {
    private fun scoped(value: String) = if (dataArea == com.nanzhufeng.ai.domain.ConversationSurface.CHAT) value else "$value-WORK"
    private val app = context.applicationContext

    fun onSettingChanged(enabled: Boolean) {
        if (enabled) {
            enqueueBackfill()
            ensurePeriodic()
        } else {
            manager().cancelUniqueWork(scoped(ONCE_WORK))
            manager().cancelAllWorkByTag(scoped(ONCE_WORK))
            manager().cancelUniqueWork(scoped(PERIODIC_WORK))
        }
    }

    fun enqueueConversation(conversationId: ConversationId) {
        val request = OneTimeWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>()
            .addTag(scoped(ONCE_WORK))
            .setConstraints(network)
            .setInputData(workDataOf("dataArea" to dataArea.name, CONVERSATION_ID to conversationId.value, TRIGGER to HistoryKnowledgeAutoCurationTrigger.CONVERSATION_IDLE.name))
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        manager().enqueueUniqueWork(scoped("$ONCE_WORK-${conversationId.value}"), ExistingWorkPolicy.KEEP, request)
    }

    private fun enqueueBackfill() {
        val request = OneTimeWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>()
            .setConstraints(network)
            .setInputData(workDataOf("dataArea" to dataArea.name, TRIGGER to HistoryKnowledgeAutoCurationTrigger.SETTING_ENABLED.name))
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        manager().enqueueUniqueWork(scoped(ONCE_WORK), ExistingWorkPolicy.KEEP, request)
    }

    private fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>(12, TimeUnit.HOURS)
            .setConstraints(network)
            .setInputData(workDataOf("dataArea" to dataArea.name, TRIGGER to HistoryKnowledgeAutoCurationTrigger.PERIODIC.name))
            .build()
        manager().enqueueUniquePeriodicWork(scoped(PERIODIC_WORK), ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun manager(): WorkManager = WorkManager.getInstance(app)

    private val network get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val ONCE_WORK = "nfai.history-knowledge.auto.once"
        const val PERIODIC_WORK = "nfai.history-knowledge.auto.periodic"
        const val CONVERSATION_ID = "conversationId"
        const val TRIGGER = "trigger"
    }
}

class HistoryKnowledgeAutoCurationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runMutex.withLock {
        val area = com.nanzhufeng.ai.domain.ConversationSurface.valueOf(inputData.getString("dataArea") ?: "CHAT")
        val container = com.nanzhufeng.ai.app.AppContainer(applicationContext, dataArea = area, scheduleHistoryCuration = false)
        val owner = container.automaticHistoryKnowledgeCurationOwner
        val runStore = container.historyKnowledgeAutoCurationRunStore
        val trigger = inputData.getString("trigger")
            ?.let { runCatching { HistoryKnowledgeAutoCurationTrigger.valueOf(it) }.getOrNull() }
            ?: HistoryKnowledgeAutoCurationTrigger.PERIODIC
        val startedAt = Instant.now()
        val receipts = AndroidHistoryCurationReceipts(applicationContext, area)
        fun receipt(phase: String) = JSONObject().apply {
            put("runId", id.toString()); put("trigger", trigger.name); put("area", area.name); put("phase", phase)
            put("version", com.nanzhufeng.ai.BuildConfig.VERSION_NAME); put("versionCode", com.nanzhufeng.ai.BuildConfig.VERSION_CODE)
            put("startedAt", startedAt.toEpochMilli()); put("observedAt", Instant.now().toEpochMilli())
        }
        when (runStore.begin(id.toString(), trigger, startedAt)) {
            HistoryKnowledgeAutoCurationBeginResult.INTERRUPTED,
            HistoryKnowledgeAutoCurationBeginResult.ALREADY_FINISHED -> return@withContext Result.success()
            HistoryKnowledgeAutoCurationBeginResult.STARTED,
            HistoryKnowledgeAutoCurationBeginResult.RETRY_STARTED -> Unit
        }
        val before = AndroidHistoryCurationReceipts.witness(container)
        receipts.append(receipt("STARTED").put("witness", before))
        val conversationId = inputData.getString("conversationId")?.let(::ConversationId)
        val result = try { conversationId?.let(owner::curate) ?: owner.curateNext() }
        catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            runStore.finish(id.toString(), HistoryKnowledgeAutoCurationRunStatus.UNKNOWN, "OWNER_EXECUTION_FAILED", false, Instant.now())
            receipts.append(receipt("FINISHED").put("status", "UNKNOWN").put("reason", "OWNER_EXECUTION_FAILED")
                .put("before", before).put("after", AndroidHistoryCurationReceipts.witness(container)).put("calls", AndroidHistoryCurationReceipts.calls(container, startedAt)))
            return@withContext Result.failure()
        }
        val transient = (result as? AutomaticHistoryKnowledgeCurationResult.Failed)?.safeCode?.isTransientAutoCurationFailure() == true
        val (status, reason) = when (result) {
            AutomaticHistoryKnowledgeCurationResult.Disabled -> HistoryKnowledgeAutoCurationRunStatus.DISABLED to "SETTING_DISABLED"
            is AutomaticHistoryKnowledgeCurationResult.NotEligible -> HistoryKnowledgeAutoCurationRunStatus.NO_CANDIDATE to result.reason.name
            is AutomaticHistoryKnowledgeCurationResult.Duplicate -> HistoryKnowledgeAutoCurationRunStatus.NO_CANDIDATE to result.reason.name
            is AutomaticHistoryKnowledgeCurationResult.NotConfident -> HistoryKnowledgeAutoCurationRunStatus.NO_CANDIDATE to result.reason.name
            is AutomaticHistoryKnowledgeCurationResult.Unknown -> HistoryKnowledgeAutoCurationRunStatus.UNKNOWN to result.safeCode
            is AutomaticHistoryKnowledgeCurationResult.Saved -> HistoryKnowledgeAutoCurationRunStatus.SAVED to "KNOWLEDGE_SAVED"
            is AutomaticHistoryKnowledgeCurationResult.Failed -> HistoryKnowledgeAutoCurationRunStatus.FAILED to result.safeCode
        }
        runStore.finish(id.toString(), status, reason, transient, Instant.now())
        receipts.append(receipt("FINISHED").put("status", status.name).put("reason", reason)
            .put("before", before).put("after", AndroidHistoryCurationReceipts.witness(container)).put("calls", AndroidHistoryCurationReceipts.calls(container, startedAt)))
        if (transient) Result.retry() else Result.success()
        }
    }
    private companion object { val runMutex = Mutex() }
}

/** Only transport conditions may retry. Auth, bad requests and malformed model output wait for a user/config/source change. */
internal fun String.isTransientAutoCurationFailure(): Boolean = this == "CONNECT_FAILED" || this == "HTTP_429" ||
    this == "HTTP_502" || this == "HTTP_503"
