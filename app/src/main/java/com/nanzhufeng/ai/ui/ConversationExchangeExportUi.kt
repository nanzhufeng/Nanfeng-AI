package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nanzhufeng.ai.domain.ConversationExchangeExportPort
import com.nanzhufeng.ai.domain.ConversationExchangeExportResult
import com.nanzhufeng.ai.domain.ConversationId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversationExchangeExportUiState(
    val working: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

/** UI state only: the selected conversation and SAF Uri remain explicit inputs to the port. */
class ConversationExchangeExportViewModel(private val port: ConversationExchangeExportPort) : ViewModel() {
    var state by mutableStateOf(ConversationExchangeExportUiState()); private set

    fun export(conversationId: ConversationId, destination: Uri) {
        state = ConversationExchangeExportUiState(working = true)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { port.export(conversationId, destination) }) {
                is ConversationExchangeExportResult.Exported -> state = ConversationExchangeExportUiState(
                    notice = "跨端交换包已严格回读：${result.semanticHash.take(12)}… · ${result.byteCount} B",
                )
                is ConversationExchangeExportResult.Rejected -> state = ConversationExchangeExportUiState(error = result.reason)
                is ConversationExchangeExportResult.Failed -> state = ConversationExchangeExportUiState(error = result.reason)
            }
        }
    }

    fun clear() { if (!state.working) state = ConversationExchangeExportUiState() }

    class Factory(private val port: ConversationExchangeExportPort) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ConversationExchangeExportViewModel(port) as T
    }
}
