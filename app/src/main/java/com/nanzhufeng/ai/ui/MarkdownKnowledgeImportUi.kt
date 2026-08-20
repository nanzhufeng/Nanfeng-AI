package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.ImportItemId
import com.nanzhufeng.ai.domain.ImportTaskId
import com.nanzhufeng.ai.domain.KnowledgeScope
import com.nanzhufeng.ai.domain.ManageMarkdownImportUseCase
import com.nanzhufeng.ai.domain.MarkdownImportItemStatus
import com.nanzhufeng.ai.domain.MarkdownImportTask
import com.nanzhufeng.ai.domain.MarkdownImportTaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MarkdownImportUiState(val dialogVisible: Boolean = false, val isWorking: Boolean = false, val tasks: List<MarkdownImportTask> = emptyList(), val selectedTask: MarkdownImportTask? = null, val editingItemId: ImportItemId? = null, val editTitle: String = "", val editBody: String = "", val editTags: String = "")

class MarkdownKnowledgeImportViewModel(private val imports: ManageMarkdownImportUseCase) : ViewModel() {
    var state by mutableStateOf(MarkdownImportUiState()); private set
    fun showDialog() { reload(open = true) }
    fun dismiss() { if (!state.isWorking) state = state.copy(dialogVisible = false) }
    fun selectedFile(displayName: String, mimeType: String, bytes: ByteArray) { state = state.copy(isWorking = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.select(displayName, mimeType, bytes) }; reload() } }
    fun open(task: MarkdownImportTask) { state = state.copy(selectedTask = task, editingItemId = null, editTitle = "", editBody = "", editTags = "") }
    fun back() { state = state.copy(selectedTask = null, editingItemId = null, editTitle = "", editBody = "", editTags = "") }
    fun edit(itemId: ImportItemId) { state.selectedTask?.items?.firstOrNull { it.id == itemId }?.let { state = state.copy(editingItemId = itemId, editTitle = it.title, editBody = it.body, editTags = it.tags.joinToString(",")) } }
    fun update(title: String = state.editTitle, body: String = state.editBody, tags: String = state.editTags) { state = state.copy(editTitle = title, editBody = body, editTags = tags) }
    fun confirm(itemId: ImportItemId) { val task = state.selectedTask ?: return; val item = task.items.firstOrNull { it.id == itemId } ?: return; val edited = state.editingItemId == itemId; val title = if (edited) state.editTitle else item.title; val body = if (edited) state.editBody else item.body; val tags = if (edited) state.editTags.split(',').map(String::trim).filter(String::isNotEmpty).toSet() else item.tags; state = state.copy(isWorking = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.confirm(task.id, itemId, title, body, tags, KnowledgeScope.GLOBAL, null) }; reload(task.id) } }
    fun skip(itemId: ImportItemId) { mutate { imports.skip(it, itemId) } }
    fun cancel() { state.selectedTask?.id?.let { id -> mutate { imports.cancel(id) } } }
    fun retry(id: ImportTaskId) { mutate { imports.retry(id) } }
    private fun mutate(block: (ImportTaskId) -> Unit) { val id = state.selectedTask?.id ?: return; state = state.copy(isWorking = true); viewModelScope.launch { withContext(Dispatchers.IO) { block(id) }; reload(id) } }
    private fun reload(selected: ImportTaskId? = state.selectedTask?.id, open: Boolean = false) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { imports.list() }; val task = selected?.let { id -> tasks.firstOrNull { it.id == id } }; state = state.copy(dialogVisible = state.dialogVisible || open, isWorking = false, tasks = tasks, selectedTask = task, editingItemId = null, editTitle = "", editBody = "", editTags = "") } }
    class Factory(private val imports: ManageMarkdownImportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MarkdownKnowledgeImportViewModel(imports) as T }
}

@Composable fun MarkdownKnowledgeImportCard(onOpen: () -> Unit) = WhiteCard {
    Text("Markdown 知识导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp)); Text("仅支持 UTF-8 .md/.markdown。仅在前台处理；系统中断后保留私有副本并可手动重试，逐项确认才写入正式 Knowledge。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp)); Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { androidx.compose.material3.Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.padding(4.dp)); Text("选择 Markdown 文件") }
}

@Composable fun MarkdownKnowledgeImportDialog(state: MarkdownImportUiState, onDismiss: () -> Unit, onBack: () -> Unit, onOpen: (MarkdownImportTask) -> Unit, onRetry: (ImportTaskId) -> Unit, onEdit: (ImportItemId) -> Unit, onTitle: (String) -> Unit, onBody: (String) -> Unit, onTags: (String) -> Unit, onConfirm: (ImportItemId) -> Unit, onSkip: (ImportItemId) -> Unit, onCancel: () -> Unit) = AlertDialog(
    onDismissRequest = { if (!state.isWorking) onDismiss() }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
    title = { Text(if (state.selectedTask == null) "Markdown 导入任务" else "导入任务详情", fontWeight = FontWeight.SemiBold) },
    text = {
        Column(Modifier.p5aKeyboardTraversal().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val task = state.selectedTask
            if (task == null) {
                Text("退出页面不会丢失已选择、解析、待确认或失败任务。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                state.tasks.forEach { row ->
                    TextButton(onClick = { onOpen(row) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(row.asset?.displayName ?: "未完成选择", fontWeight = FontWeight.Medium)
                            Text("${row.status.label()} · 成功 ${row.completedCount} · 失败 ${row.failedCount}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text("${task.asset?.displayName ?: "无私有资产"} · ${task.status.label()}", fontWeight = FontWeight.Medium)
                task.failure?.let { Text("失败：${it.name}。可重试或取消；未确认项不会写入。", color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                task.items.forEach { item ->
                    HorizontalDivider(color = NeutralBorder)
                    Text("${item.ordinal + 1}. ${item.title} · ${item.status.label()}", fontWeight = FontWeight.Medium)
                    if (item.status == MarkdownImportItemStatus.PENDING_CONFIRMATION) {
                        TextButton(onClick = { onEdit(item.id) }) { Text("编辑此项") }
                        if (state.editingItemId == item.id) {
                            OutlinedTextField(state.editTitle, onTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                            OutlinedTextField(state.editBody, onBody, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                            OutlinedTextField(state.editTags, onTags, label = { Text("标签") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                        }
                        Row {
                            TextButton(onClick = { onConfirm(item.id) }) { Text("确认写入") }
                            TextButton(onClick = { onSkip(item.id) }) { Text("跳过") }
                        }
                    } else {
                        item.failure?.let { Text("失败：$it", color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    },
    dismissButton = { TextButton(onClick = if (state.selectedTask == null) onDismiss else onBack, enabled = !state.isWorking) { Text(if (state.selectedTask == null) "关闭" else "返回") } },
    confirmButton = { state.selectedTask?.let { task -> Row { if (task.status == MarkdownImportTaskStatus.FAILED) TextButton(onClick = { onRetry(task.id) }) { Text("重试") }; if (task.status !in setOf(MarkdownImportTaskStatus.COMPLETED, MarkdownImportTaskStatus.CANCELLED)) TextButton(onClick = onCancel) { Text("取消任务") } } } },
)

private fun MarkdownImportTaskStatus.label() = when (this) { MarkdownImportTaskStatus.SELECTED -> "已选择"; MarkdownImportTaskStatus.PRIVATE_COPIED -> "已私有复制"; MarkdownImportTaskStatus.PARSING -> "解析中"; MarkdownImportTaskStatus.AWAITING_CONFIRMATION -> "待逐项确认"; MarkdownImportTaskStatus.PARTIALLY_COMPLETED -> "部分完成"; MarkdownImportTaskStatus.COMPLETED -> "完成"; MarkdownImportTaskStatus.FAILED -> "失败"; MarkdownImportTaskStatus.CANCELLED -> "已取消" }
private fun MarkdownImportItemStatus.label() = when (this) { MarkdownImportItemStatus.PENDING_CONFIRMATION -> "待确认"; MarkdownImportItemStatus.CONFIRMED -> "已写入"; MarkdownImportItemStatus.SKIPPED -> "已跳过"; MarkdownImportItemStatus.FAILED -> "失败" }
