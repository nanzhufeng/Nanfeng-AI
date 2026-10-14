package com.nanzhufeng.ai

import android.app.Application
import android.util.Log
import androidx.work.Configuration

/**
 * Supplies WorkManager configuration to SystemJobService in a cold process.
 *
 * AndroidX Startup remains disabled deliberately; Configuration.Provider lets WorkManager initialize
 * only when scheduled work actually starts, without requiring an Activity or AppContainer first.
 */
class NanfengAiApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        // Never alter the crash handler or reissue an interrupted provider request.
        Thread({ com.nanzhufeng.ai.background.GenerationContinuityDiagnostics(this).recordPreviousProcessExit() }, "generation-exit-metadata").start()
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)
            .build()
    }
}
