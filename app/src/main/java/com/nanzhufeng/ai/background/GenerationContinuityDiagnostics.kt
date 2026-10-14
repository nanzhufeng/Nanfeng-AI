package com.nanzhufeng.ai.background

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.security.MessageDigest

/** App-private, bounded metadata only. No text, paths, model payload or credentials. */
class GenerationContinuityDiagnostics(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("generation_continuity_v1", Context.MODE_PRIVATE)

    fun record(code: String, task: String = "process", ageMs: Long = 0) {
        if (!code.matches(Regex("[A-Z0-9_:]{1,80}"))) return
        runCatching {
            synchronized(lock) {
                val key = MessageDigest.getInstance("SHA-256").digest(task.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
                val row = "${System.currentTimeMillis()}|${SystemClock.elapsedRealtime()}|$key|$code|${ageMs.coerceAtLeast(0)}"
                val previous = preferences.getString("events", "").orEmpty().takeLast(16_384).lineSequence().filter(String::isNotBlank).toList()
                preferences.edit().putString("events", (previous + row).takeLast(64).joinToString("\n")).apply()
            }
        }
    }

    fun recordPreviousProcessExit() {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val manager = app.getSystemService(ActivityManager::class.java) ?: return
            val latest = manager.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull() ?: return
            if (latest.timestamp <= preferences.getLong("last_exit_timestamp", 0)) return
            record("PROCESS_EXIT_${latest.reason}")
            preferences.edit().putLong("last_exit_timestamp", latest.timestamp).apply()
        }
    }

    companion object { private val lock = Any() }
}
