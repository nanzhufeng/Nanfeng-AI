package com.nanzhufeng.ai.data

import android.content.Context
import androidx.room.InvalidationTracker
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
import com.nanzhufeng.ai.data.local.NanfengAiDatabase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** User-controlled periodic refresh for the successful manual-selection ledger only. */
class P7FSelectedConversationSyncScheduler(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun enabled(): Boolean = preferences.getBoolean(ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(ENABLED, enabled).commit()) { "PERIODIC_SYNC_SETTING_WRITE_FAILED" }
        if (enabled) schedule() else cancel()
    }

    fun resumeIfEnabled() { if (enabled()) schedule() }
    fun pauseForSignOut() = cancel()

    /** Coalesces real local changes for selected conversations; the 12-hour switch remains a fallback. */
    fun scheduleAfterLocalConversationMutation() {
        val request = OneTimeWorkRequestBuilder<P7FSelectedConversationSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        configuredWorkManager().enqueueUniqueWork(MUTATION_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<P7FSelectedConversationSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        configuredWorkManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancel() {
        configuredWorkManager().cancelUniqueWork(WORK_NAME)
        configuredWorkManager().cancelUniqueWork(MUTATION_WORK_NAME)
    }

    private fun configuredWorkManager(): WorkManager {
        // Startup initialization is deliberately disabled; only this explicit toggle owns setup.
        runCatching {
            WorkManager.initialize(app, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build())
        }
        return WorkManager.getInstance(app)
    }

    private companion object {
        const val PREFERENCES = "nanfeng_ai_selected_conversation_sync"
        const val ENABLED = "periodic_enabled"
        const val WORK_NAME = "nfai.selected-conversations.periodic"
        const val MUTATION_WORK_NAME = "nfai.selected-conversations.mutations"
    }
}

/**
 * Room is the single durable mutation seam for replies and list operations.  The sync receipt
 * table is deliberately not observed, so a successful upload cannot schedule itself again.
 */
class P7FSelectedConversationMutationObserver(
    database: NanfengAiDatabase,
    private val scheduler: P7FSelectedConversationSyncScheduler,
) {
    private val observer = object : InvalidationTracker.Observer(
        "conversations", "message_nodes", "message_content_blocks",
    ) {
        override fun onInvalidated(tables: Set<String>) = scheduler.scheduleAfterLocalConversationMutation()
    }

    init { database.invalidationTracker.addObserver(observer) }
}

class P7FSelectedConversationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            com.nanzhufeng.ai.app.AppContainer(applicationContext)
                .p7fManualConversationSyncOwner
                .syncPreviouslySelected()
        }.fold(
            onSuccess = { results ->
                // A selected receipt that could not reach the cloud must not
                // be silently acknowledged: WorkManager owns bounded retry.
                if (results.any { it is P7FManualConversationSyncResult.Rejected }) Result.retry()
                else Result.success()
            },
            onFailure = { Result.retry() },
        )
    }
}
