package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2OpenDocumentRestorePort
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2OpenDocumentRestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WorkspaceExchangeV2RestoreUiOutcome {
    IDLE, RESTORED, REPLAYED, PACKAGE_REJECTED, LOCAL_TRUTH_PRESENT, INTENT_CONFLICT, RECOVERY_REQUIRED, FAILED_RECOVERABLY,
}

/** Deliberately content-free: no URI, name, path, package bytes, IR, attachment bytes, or raw error. */
data class WorkspaceExchangeV2RestoreUiState(
    val working: Boolean = false,
    val outcome: WorkspaceExchangeV2RestoreUiOutcome = WorkspaceExchangeV2RestoreUiOutcome.IDLE,
    val semanticHashPrefix: String? = null,
    val objectCount: Int = 0,
    val attachmentCount: Int = 0,
)

class WorkspaceExchangeV2RestoreViewModel(
    private val port: WorkspaceExchangeV2OpenDocumentRestorePort,
) : ViewModel() {
    var state by mutableStateOf(WorkspaceExchangeV2RestoreUiState()); private set

    fun selectedDocument(document: Uri) {
        if (state.working) return
        state = WorkspaceExchangeV2RestoreUiState(working = true)
        viewModelScope.launch {
            state = withContext(Dispatchers.IO) { port.restoreSelectedDocument(document).toUiState() }
        }
    }

    private fun WorkspaceExchangeV2OpenDocumentRestoreResult.toUiState(): WorkspaceExchangeV2RestoreUiState = when (this) {
        is WorkspaceExchangeV2OpenDocumentRestoreResult.Restored -> WorkspaceExchangeV2RestoreUiState(
            outcome = if (replayed) WorkspaceExchangeV2RestoreUiOutcome.REPLAYED else WorkspaceExchangeV2RestoreUiOutcome.RESTORED,
            semanticHashPrefix = semanticHash.take(12), objectCount = objectCount, attachmentCount = attachmentCount,
        )
        WorkspaceExchangeV2OpenDocumentRestoreResult.PackageRejected -> WorkspaceExchangeV2RestoreUiState(outcome = WorkspaceExchangeV2RestoreUiOutcome.PACKAGE_REJECTED)
        WorkspaceExchangeV2OpenDocumentRestoreResult.LocalTruthPresent -> WorkspaceExchangeV2RestoreUiState(outcome = WorkspaceExchangeV2RestoreUiOutcome.LOCAL_TRUTH_PRESENT)
        WorkspaceExchangeV2OpenDocumentRestoreResult.IntentConflict -> WorkspaceExchangeV2RestoreUiState(outcome = WorkspaceExchangeV2RestoreUiOutcome.INTENT_CONFLICT)
        WorkspaceExchangeV2OpenDocumentRestoreResult.RecoveryRequired -> WorkspaceExchangeV2RestoreUiState(outcome = WorkspaceExchangeV2RestoreUiOutcome.RECOVERY_REQUIRED)
        WorkspaceExchangeV2OpenDocumentRestoreResult.FailedRecoverably -> WorkspaceExchangeV2RestoreUiState(outcome = WorkspaceExchangeV2RestoreUiOutcome.FAILED_RECOVERABLY)
    }

    class Factory(private val port: WorkspaceExchangeV2OpenDocumentRestorePort) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceExchangeV2RestoreViewModel(port) as T
    }
}
