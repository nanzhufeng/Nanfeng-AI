package com.nanzhufeng.ai.data

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<P7FSelectedConversationSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        configuredWorkManager().enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancel() { configuredWorkManager().cancelUniqueWork(WORK_NAME) }

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
    }
}

class P7FSelectedConversationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            com.nanzhufeng.ai.app.AppContainer(applicationContext)
                .p7fManualConversationSyncOwner
                .syncPreviouslySelected()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
