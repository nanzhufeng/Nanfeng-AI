package com.nanzhufeng.ai.data

import android.content.Context
import androidx.work.Configuration
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores only opaque source hashes; text and model output never leave the owner or enter preferences. */
class AndroidHistoryKnowledgeCurationCheckpointStore(context: Context) : HistoryKnowledgeCurationCheckpointStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun hasProcessed(conversationId: ConversationId, sourceHash: String): Boolean = token(conversationId, sourceHash) in preferences.getStringSet(ENTRIES, emptySet()).orEmpty()

    override fun markProcessed(conversationId: ConversationId, sourceHash: String) {
        val next = (preferences.getStringSet(ENTRIES, emptySet()).orEmpty() + token(conversationId, sourceHash))
            .toList().takeLast(MAX_ENTRIES).toSet()
        check(preferences.edit().putStringSet(ENTRIES, next).commit()) { "自动整理检查点无法写入本机。" }
    }

    private fun token(conversationId: ConversationId, sourceHash: String) = "${conversationId.value}|$sourceHash"

    private companion object {
        const val FILE = "history_knowledge_auto_curation_v1"
        const val ENTRIES = "processed_source_hashes"
        const val MAX_ENTRIES = 512
    }
}

/** A user-enabled queue: latest completed chat replaces older pending work, while history backfill is slow and bounded. */
class AndroidHistoryKnowledgeAutoCurationScheduler(context: Context) {
    private val app = context.applicationContext

    fun onSettingChanged(enabled: Boolean) {
        if (enabled) {
            enqueueBackfill()
            ensurePeriodic()
        } else {
            manager().cancelUniqueWork(ONCE_WORK)
            manager().cancelUniqueWork(PERIODIC_WORK)
        }
    }

    fun enqueueConversation(conversationId: ConversationId) {
        val request = OneTimeWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(CONVERSATION_ID to conversationId.value))
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        manager().enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueBackfill() {
        val request = OneTimeWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>()
            .setConstraints(network)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        manager().enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<HistoryKnowledgeAutoCurationWorker>(12, TimeUnit.HOURS)
            .setConstraints(network).build()
        manager().enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun manager(): WorkManager {
        runCatching { WorkManager.initialize(app, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()) }
        return WorkManager.getInstance(app)
    }

    private val network get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val ONCE_WORK = "nfai.history-knowledge.auto.once"
        const val PERIODIC_WORK = "nfai.history-knowledge.auto.periodic"
        const val CONVERSATION_ID = "conversationId"
    }
}

class HistoryKnowledgeAutoCurationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val owner = com.nanzhufeng.ai.app.AppContainer(applicationContext).automaticHistoryKnowledgeCurationOwner
        val conversationId = inputData.getString("conversationId")?.let(::ConversationId)
        when (val result = conversationId?.let(owner::curate) ?: owner.curateNext()) {
            is AutomaticHistoryKnowledgeCurationResult.Failed -> if (result.safeCode.isTransientAutoCurationFailure()) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

/** Only transport conditions may retry. Auth, bad requests and malformed model output wait for a user/config/source change. */
internal fun String.isTransientAutoCurationFailure(): Boolean = this == "TIMEOUT" || this == "NETWORK" ||
    this == "HTTP_408" || this == "HTTP_429" ||
    (startsWith("HTTP_") && substringAfter("HTTP_").toIntOrNull()?.let { it in 500..599 } == true)
