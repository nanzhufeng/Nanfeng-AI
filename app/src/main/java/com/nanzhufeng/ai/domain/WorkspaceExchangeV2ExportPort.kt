package com.nanzhufeng.ai.domain

import android.net.Uri

sealed interface WorkspaceExchangeV2ExportResult {
    data class Exported(
        val packageHash: String,
        val semanticHash: String,
        val objectCount: Int,
        val attachmentCount: Int,
    ) : WorkspaceExchangeV2ExportResult
    data class Rejected(val reason: String) : WorkspaceExchangeV2ExportResult
    data class Failed(val reason: String) : WorkspaceExchangeV2ExportResult
}

/** Android UI can only reach v2 serialization through this explicit, user-selected SAF port. */
interface WorkspaceExchangeV2ExportPort {
    fun prepareCompleteWorkspace(): WorkspaceExchangeV2ScopePreparation
    fun export(scope: WorkspaceExchangeV2ScopeSummary, destination: Uri): WorkspaceExchangeV2ExportResult
}
