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
import com.nanzhufeng.ai.domain.P7DWorkScheduler
import java.util.concurrent.TimeUnit

/** Work requests contain only opaque account refs/generation and are never enqueued while disabled. */
class AndroidP7DWorkScheduler(context: Context) : P7DWorkScheduler {
    private val manager = WorkManager.getInstance(context.applicationContext)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    override fun enqueueDelayed(accountRef: String, generation: Long) {
        val request = OneTimeWorkRequestBuilder<P7DSyncWorker>().setConstraints(constraints).setInputData(workDataOf("accountRef" to accountRef, "generation" to generation)).setInitialDelay(30, TimeUnit.SECONDS).build()
        manager.enqueueUniqueWork("nfai.p7d.once.$accountRef", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    override fun ensurePeriodic(accountRef: String) {
        val request = PeriodicWorkRequestBuilder<P7DSyncWorker>(12, TimeUnit.HOURS).setConstraints(constraints).setInputData(workDataOf("accountRef" to accountRef)).build()
        manager.enqueueUniquePeriodicWork("nfai.p7d.periodic.$accountRef", ExistingPeriodicWorkPolicy.KEEP, request)
    }
    override fun cancel(accountRef: String) { manager.cancelUniqueWork("nfai.p7d.once.$accountRef"); manager.cancelUniqueWork("nfai.p7d.periodic.$accountRef") }
}

/** No runtime owner is registered in P7-D, so this cannot perform HTTP even if manually invoked. */
class P7DSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = Result.success()
}
