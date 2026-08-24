package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.ExportMarkdownKnowledgeUseCase
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.KnowledgeSearchFilter
import com.nanzhufeng.ai.domain.KnowledgeSearchResult
import com.nanzhufeng.ai.domain.ManageKnowledgeUseCase
import com.nanzhufeng.ai.domain.MarkdownExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MarkdownExportUiState(val visible: Boolean = false, val working: Boolean = false, val entries: List<KnowledgeSearchResult> = emptyList(), val selected: Set<KnowledgeItemId> = emptySet(), val result: MarkdownExportResult? = null, val message: String? = null)
class MarkdownKnowledgeExportViewModel(private val knowledge: ManageKnowledgeUseCase, private val exporter: ExportMarkdownKnowledgeUseCase) : ViewModel() {
    var state by mutableStateOf(MarkdownExportUiState()); private set
    fun show() { state = state.copy(visible = true, working = true); viewModelScope.launch { state = state.copy(working = false, entries = withContext(Dispatchers.IO) { knowledge.search(KnowledgeSearchFilter()) }) } }
    fun dismiss() { if (!state.working) state = state.copy(visible = false) }
    fun toggle(id: KnowledgeItemId) { state = state.copy(selected = state.selected.let { if (id in it) it - id else it + id }, message = null) }
    fun export() { if (state.working || state.selected.isEmpty()) return; state = state.copy(working = true); viewModelScope.launch { val outcome = withContext(Dispatchers.IO) { exporter.execute(state.selected) }; state = state.copy(working = false, result = outcome, message = if (outcome == null) "未创建文件：请选择仍为活动状态的正式 Knowledge。" else null) } }
    class Factory(private val knowledge: ManageKnowledgeUseCase, private val exporter: ExportMarkdownKnowledgeUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MarkdownKnowledgeExportViewModel(knowledge, exporter) as T }
}
@Composable fun MarkdownKnowledgeExportCard(onOpen: () -> Unit) = WhiteCard { Text("Markdown 导出", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("先明确选择正式活动 Knowledge，再以可回读 Markdown 与 Manifest 导出。", color = SecondaryText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { androidx.compose.material3.Icon(Icons.Outlined.FileDownload, null); Text("选择范围并导出") } }
@Composable fun MarkdownKnowledgeExportDialog(state: MarkdownExportUiState, onDismiss: () -> Unit, onToggle: (KnowledgeItemId) -> Unit, onExport: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp), title = { Text("导出 Markdown Knowledge", fontWeight = FontWeight.SemiBold) }, text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { Text("仅导出你逐项选择的正式活动 Knowledge。不会包含来源 URI、路径、Key、Prompt、Provider payload、附件字节、关系或已删除项。", color = SecondaryText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall); state.entries.forEach { item -> androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.id in state.selected, { onToggle(item.id) }); Column { Text(item.title, fontWeight = FontWeight.Medium); Text(item.tags.joinToString(" · ").ifBlank { "无标签" }, color = SecondaryText, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) } } }; state.result?.let { Text("已原子写入、回读并校验 SHA-256：${it.fileName} · ${it.sha256}", color = BrandGreen, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }; state.message?.let { Text(it, color = ErrorRed, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) } } }, dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }, confirmButton = { Button(onClick = onExport, enabled = !state.working && state.selected.isNotEmpty()) { Text("导出 ${state.selected.size} 项") } })
