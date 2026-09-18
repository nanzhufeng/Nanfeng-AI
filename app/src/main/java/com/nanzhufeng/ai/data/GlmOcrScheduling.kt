package com.nanzhufeng.ai.data

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nanzhufeng.ai.domain.GlmOcrTaskId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit user requests are durable, unique per task and never retried after an unknown outcome. */
class AndroidGlmOcrScheduler(context: Context, private val dataArea: com.nanzhufeng.ai.domain.ConversationSurface = com.nanzhufeng.ai.domain.ConversationSurface.CHAT) {
    private val app = context.applicationContext

    fun enqueue(taskId: GlmOcrTaskId) {
        val request = OneTimeWorkRequestBuilder<GlmOcrWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("dataArea" to dataArea.name, TASK_ID to taskId.value))
            .build()
        manager().enqueueUniqueWork("nfai.glm-ocr.${if (dataArea == com.nanzhufeng.ai.domain.ConversationSurface.CHAT) "" else "WORK."}${taskId.value}", ExistingWorkPolicy.REPLACE, request)
    }

    private fun manager(): WorkManager {
        runCatching { WorkManager.initialize(app, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()) }
        return WorkManager.getInstance(app)
    }

    companion object { const val TASK_ID = "glmOcrTaskId" }
}

class GlmOcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(AndroidGlmOcrScheduler.TASK_ID)?.takeIf(String::isNotBlank)
            ?: return@withContext Result.failure()
        // The owner persists the exact terminal state. Unknown network/timeout results wait for
        // an explicit user retry so the app never silently bills the same document twice.
        val taskId = GlmOcrTaskId(id)
        val owner = com.nanzhufeng.ai.app.AppContainer(applicationContext, dataArea = com.nanzhufeng.ai.domain.ConversationSurface.valueOf(inputData.getString("dataArea") ?: "CHAT")).glmOcrTaskOwner
        runCatching { owner.process(taskId) }.onFailure { runCatching { owner.failUnexpectedExecution(taskId) } }
        Result.success()
    }
}
