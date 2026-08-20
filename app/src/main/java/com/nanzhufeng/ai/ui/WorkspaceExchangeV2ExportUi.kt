package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ExportPort
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ExportResult
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopePreparation
import com.nanzhufeng.ai.domain.WorkspaceExchangeV2ScopeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkspaceExchangeV2ExportUiState(
    val working: Boolean = false,
    val scope: WorkspaceExchangeV2ScopeSummary? = null,
    val notice: String? = null,
    val error: String? = null,
)

/** Scope selection, SAF output, and receipt display stay separate from the v2 mapper/writer. */
class WorkspaceExchangeV2ExportViewModel(private val port: WorkspaceExchangeV2ExportPort) : ViewModel() {
    var state by mutableStateOf(WorkspaceExchangeV2ExportUiState()); private set

    fun selectCompleteWorkspaceScope() {
        if (state.working) return
        state = WorkspaceExchangeV2ExportUiState(working = true)
        viewModelScope.launch {
            when (val prepared = withContext(Dispatchers.IO) { port.prepareCompleteWorkspace() }) {
                is WorkspaceExchangeV2ScopePreparation.Prepared -> state = WorkspaceExchangeV2ExportUiState(scope = prepared.value)
                is WorkspaceExchangeV2ScopePreparation.Rejected -> state = WorkspaceExchangeV2ExportUiState(error = prepared.reason)
            }
        }
    }

    fun export(destination: Uri) {
        val scope = state.scope ?: return
        state = state.copy(working = true, error = null, notice = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { port.export(scope, destination) }) {
                is WorkspaceExchangeV2ExportResult.Exported -> state = WorkspaceExchangeV2ExportUiState(
                    notice = "完整工作区 v2 已严格回读：${result.semanticHash.take(12)}… · ${result.objectCount} 项对象 · ${result.attachmentCount} 项附件",
                )
                is WorkspaceExchangeV2ExportResult.Rejected -> state = WorkspaceExchangeV2ExportUiState(error = result.reason)
                is WorkspaceExchangeV2ExportResult.Failed -> state = WorkspaceExchangeV2ExportUiState(error = result.reason)
            }
        }
    }

    fun clearScope() { if (!state.working) state = WorkspaceExchangeV2ExportUiState() }

    class Factory(private val port: WorkspaceExchangeV2ExportPort) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceExchangeV2ExportViewModel(port) as T
    }
}
