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
import org.json.JSONArray
import org.json.JSONObject

/** Stores only opaque source hashes; text and model output never leave the owner or enter preferences. */
class AndroidHistoryKnowledgeCurationCheckpointStore(context: Context) : HistoryKnowledgeCurationCheckpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    override fun reserve(conversationId: ConversationId, sourceHash: String): HistoryKnowledgeCurationReservation {
        return when (state(conversationId, sourceHash)) {
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
        }
    }

    @Synchronized
    override fun markProcessed(conversationId: ConversationId, sourceHash: String) {
        val next = (preferences.getStringSet(ENTRIES, emptySet()).orEmpty() + token(conversationId, sourceHash))
            .toList().takeLast(MAX_ENTRIES).toSet()
        check(preferences.edit().putStringSet(ENTRIES, next).commit()) { "自动整理检查点无法写入本机。" }
        writeState(conversationId, sourceHash, CheckpointState.PROCESSED)
    }

    @Synchronized
    override fun markRetryableFailure(conversationId: ConversationId, sourceHash: String) {
        writeState(conversationId, sourceHash, CheckpointState.RETRYABLE_FAILED)
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
            .takeLast(MAX_ENTRIES)
            .toSet()
        check(preferences.edit().putStringSet(STATES, next).commit()) { "自动整理预留状态无法写入本机。" }
    }

    private fun token(conversationId: ConversationId, sourceHash: String) = "${conversationId.value}|$sourceHash"
    private fun statePrefix(conversationId: ConversationId, sourceHash: String) = "${token(conversationId, sourceHash)}|"

    private enum class CheckpointState { AVAILABLE, RUNNING, PROCESSED, RETRYABLE_FAILED, UNKNOWN }

    private companion object {
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
            return when {
                prior.status == HistoryKnowledgeAutoCurationRunStatus.RUNNING -> {
                    records[priorIndex] = prior.copy(
                        status = HistoryKnowledgeAutoCurationRunStatus.UNKNOWN,
                        reasonCode = "INTERRUPTED_WORK_REDELIVERY",
                        retryable = false,
                        completedAt = startedAt,
                    )
                    write(records)
                    HistoryKnowledgeAutoCurationBeginResult.INTERRUPTED
                }
                prior.status == HistoryKnowledgeAutoCurationRunStatus.FAILED && prior.retryable -> {
                    records[priorIndex] = prior.copy(status = HistoryKnowledgeAutoCurationRunStatus.RUNNING, reasonCode = null, retryable = false, startedAt = startedAt, completedAt = null)
                    write(records)
                    HistoryKnowledgeAutoCurationBeginResult.RETRY_STARTED
                }
                else -> HistoryKnowledgeAutoCurationBeginResult.ALREADY_FINISHED
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
            manager().cancelUniqueWork(scoped(PERIODIC_WORK))
        }
    }

    fun enqueueConversation(conversationId: ConversationId) {
        val request = OneTimeWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>()
            .setConstraints(network)
            .setInputData(workDataOf("dataArea" to dataArea.name, CONVERSATION_ID to conversationId.value, TRIGGER to HistoryKnowledgeAutoCurationTrigger.CONVERSATION_IDLE.name))
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        manager().enqueueUniqueWork(scoped(ONCE_WORK), ExistingWorkPolicy.REPLACE, request)
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
        val container = com.nanzhufeng.ai.app.AppContainer(applicationContext, dataArea = com.nanzhufeng.ai.domain.ConversationSurface.valueOf(inputData.getString("dataArea") ?: "CHAT"))
        val owner = container.automaticHistoryKnowledgeCurationOwner
        val runStore = container.historyKnowledgeAutoCurationRunStore
        val trigger = inputData.getString("trigger")
            ?.let { runCatching { HistoryKnowledgeAutoCurationTrigger.valueOf(it) }.getOrNull() }
            ?: HistoryKnowledgeAutoCurationTrigger.PERIODIC
        when (runStore.begin(id.toString(), trigger, Instant.now())) {
            HistoryKnowledgeAutoCurationBeginResult.INTERRUPTED,
            HistoryKnowledgeAutoCurationBeginResult.ALREADY_FINISHED -> return@withContext Result.success()
            HistoryKnowledgeAutoCurationBeginResult.STARTED,
            HistoryKnowledgeAutoCurationBeginResult.RETRY_STARTED -> Unit
        }
        val conversationId = inputData.getString("conversationId")?.let(::ConversationId)
        val result = conversationId?.let(owner::curate) ?: owner.curateNext()
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
        if (transient) Result.retry() else Result.success()
    }
}

/** Only transport conditions may retry. Auth, bad requests and malformed model output wait for a user/config/source change. */
internal fun String.isTransientAutoCurationFailure(): Boolean = this == "TIMEOUT" || this == "NETWORK" ||
    this == "HTTP_408" || this == "HTTP_429" ||
    (startsWith("HTTP_") && substringAfter("HTTP_").toIntOrNull()?.let { it in 500..599 } == true)
