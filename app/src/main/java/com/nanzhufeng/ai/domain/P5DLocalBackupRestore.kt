package com.nanzhufeng.ai.domain

import android.net.Uri

/** P5-D keeps local backup deliberately separate from cloud sync and Provider configuration. */
data class LocalBackupArtifact(val fileName: String, val sha256: String, val byteCount: Long, val schemaVersion: Int, val tableCounts: Map<String, Long>)
data class LocalBackupPreflight(
    val format: String,
    val version: Int,
    val schemaVersion: Int,
    val tableCounts: Map<String, Long>,
    val assetBytes: Long,
    val missing: List<String>,
    val conflicts: List<String>,
    val unsupported: List<String>,
    val fingerprint: String,
)
sealed interface LocalBackupResult {
    data class Exported(val artifact: LocalBackupArtifact) : LocalBackupResult
    data class Preflighted(val preflight: LocalBackupPreflight) : LocalBackupResult
    /** The process must be restarted: no live Room/container reference may continue after a replacement. */
    data class RestoredRestartRequired(val checkpointId: String) : LocalBackupResult
    data class Rejected(val reason: String) : LocalBackupResult
    data class Failed(val reason: String, val rollbackAttempted: Boolean) : LocalBackupResult
}

interface LocalBackupRestoreManager {
    fun export(destination: Uri): LocalBackupResult
    /** Copies the selected URI into app-private inbox before parsing. No restore is started here. */
    fun preflight(source: Uri): LocalBackupResult
    /** Empty local data restores directly; non-empty data requires [replaceLocal] plus the exact preview fingerprint. */
    fun restore(preflightFingerprint: String, replaceLocal: Boolean): LocalBackupResult
    fun cancelPendingRestore()
}

object LocalBackupFormat {
    const val FORMAT = "nanfeng-ai.local-backup"
    const val VERSION = 1
    const val DATABASE_ENTRY = "database/nanfeng-ai.snapshot"
    const val MANIFEST_ENTRY = "manifest.json"
    /** Local-first archives must cover a real attachment library, while entry/hash/path checks still apply. */
    const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L * 1024L
    const val MAX_ENTRY_COUNT = 10_000
}
