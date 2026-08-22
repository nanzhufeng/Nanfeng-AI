package com.nanzhufeng.ai.domain

import android.net.Uri

/**
 * The single Android UI-facing bridge for a document the user explicitly selected with
 * OpenDocument. Implementations may consume that transient URI once, but only bounded bytes may
 * cross into [WorkspaceExchangeV2AtomicRestoreOwner]; neither UI nor SAF parses the package.
 */
sealed interface WorkspaceExchangeV2OpenDocumentRestoreResult {
    data class Restored(
        val semanticHash: String,
        val objectCount: Int,
        val attachmentCount: Int,
        val replayed: Boolean,
    ) : WorkspaceExchangeV2OpenDocumentRestoreResult

    data object PackageRejected : WorkspaceExchangeV2OpenDocumentRestoreResult
    data object LocalTruthPresent : WorkspaceExchangeV2OpenDocumentRestoreResult
    data object IntentConflict : WorkspaceExchangeV2OpenDocumentRestoreResult
    data object RecoveryRequired : WorkspaceExchangeV2OpenDocumentRestoreResult
    data object FailedRecoverably : WorkspaceExchangeV2OpenDocumentRestoreResult
}

interface WorkspaceExchangeV2OpenDocumentRestorePort {
    fun restoreSelectedDocument(document: Uri): WorkspaceExchangeV2OpenDocumentRestoreResult
}
