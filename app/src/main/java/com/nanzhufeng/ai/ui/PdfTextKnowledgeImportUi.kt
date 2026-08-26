package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfTextImportUiState(val visible: Boolean = false, val working: Boolean = false, val tasks: List<PdfTextImportTask> = emptyList(), val selected: PdfTextImportTask? = null, val editing: PdfTextImportItemId? = null, val title: String = "", val body: String = "", val tags: String = "")
class PdfTextImportViewModel(private val imports: ManagePdfTextKnowledgeImportUseCase) : ViewModel() {
    var state by mutableStateOf(PdfTextImportUiState()); private set
    fun show() = reload(open = true); fun dismiss() { if (!state.working) state = state.copy(visible = false) }; fun open(task: PdfTextImportTask) { state = state.copy(selected = task, editing = null) }; fun back() { state = state.copy(selected = null, editing = null) }
    fun selectedFile(name: String, mime: String, bytes: ByteArray) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.select(name, mime, bytes) }; reload() } }
    fun edit(id: PdfTextImportItemId) { state.selected?.items?.firstOrNull { it.id == id }?.let { state = state.copy(editing = id, title = it.title, body = it.body, tags = it.tags.joinToString(",")) } }
    fun update(title: String = state.title, body: String = state.body, tags: String = state.tags) { state = state.copy(title = title, body = body, tags = tags) }
    fun confirm(id: PdfTextImportItemId) { val task = state.selected ?: return; val item = task.items.firstOrNull { it.id == id } ?: return; val editing = state.editing == id; state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.confirm(task.id, id, if (editing) state.title else item.title, if (editing) state.body else item.body, if (editing) state.tags.split(',').map(String::trim).filter(String::isNotEmpty).toSet() else item.tags) }; reload(task.id) } }
    fun skip(id: PdfTextImportItemId) = mutate { imports.skip(it, id) }; fun cancel() = mutate { imports.cancel(it) }; fun retry(id: PdfTextImportTaskId) = mutate(id) { imports.retry(it) }
    private fun mutate(action: (PdfTextImportTaskId) -> Unit) { state.selected?.id?.let { mutate(it, action) } }; private fun mutate(id: PdfTextImportTaskId, action: (PdfTextImportTaskId) -> Unit) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { action(id) }; reload(id) } }
    private fun reload(selected: PdfTextImportTaskId? = state.selected?.id, open: Boolean = false) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { imports.list() }; state = state.copy(visible = state.visible || open, working = false, tasks = tasks, selected = selected?.let { id -> tasks.firstOrNull { it.id == id } }, editing = null) } }
    class Factory(private val imports: ManagePdfTextKnowledgeImportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = PdfTextImportViewModel(imports) as T }
}

@Composable fun PdfTextKnowledgeImportCard(onOpen: () -> Unit) = WhiteCard { Text("PDF 文本资料导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("仅接受带文本层的 PDF；仅在前台私有复制并逐页抽取、预览、确认。系统中断后可手动重试；不会 OCR、执行链接或发送内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen), modifier = Modifier.fillMaxWidth()) { Text("选择 PDF") } }
@Composable fun PdfTextImportDialog(state: PdfTextImportUiState, dismiss: () -> Unit, back: () -> Unit, open: (PdfTextImportTask) -> Unit, retry: (PdfTextImportTaskId) -> Unit, edit: (PdfTextImportItemId) -> Unit, update: (String, String, String) -> Unit, confirm: (PdfTextImportItemId) -> Unit, skip: (PdfTextImportItemId) -> Unit, cancel: () -> Unit) {
    val selected = state.selected
    AlertDialog(
        onDismissRequest = dismiss, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp),
        title = { Text(if (selected == null) "PDF 导入任务" else "PDF 文本任务详情", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.p5aKeyboardTraversal().heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                if (selected == null) {
                    Text("仅保存私有副本与安全任务事实；系统 URI 或路径不会显示或持久化。", color = SecondaryText)
                    state.tasks.forEach { row -> TextButton(onClick = { open(row) }) { Text("${row.asset?.displayName ?: "未完成选择"} · ${row.status}") } }
                } else {
                    Text("${selected.asset?.displayName} · ${selected.status}")
                    Text("阶段：${selected.extractedPageCount}/${selected.pageCount} 页 · PDF 文本 Adapter v${selected.asset?.adapterVersion ?: PDF_TEXT_ADAPTER_VERSION}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    selected.failure?.let { Text("安全失败：$it。未写入 Knowledge；可重试或取消。", color = ErrorRed) }
                    selected.items.forEach { item ->
                        HorizontalDivider(); Text("第 ${item.pageNumber} 页 · ${item.status}"); Text(item.title, fontWeight = FontWeight.Medium)
                        if (item.status == PdfTextImportItemStatus.PENDING_CONFIRMATION) {
                            TextButton(onClick = { edit(item.id) }) { Text("预览并编辑") }
                            if (state.editing == item.id) {
                                OutlinedTextField(state.title, { update(it, state.body, state.tags) }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                                OutlinedTextField(state.body, { update(state.title, it, state.tags) }, label = { Text("抽取文本") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                                OutlinedTextField(state.tags, { update(state.title, state.body, it) }, label = { Text("标签") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = { confirm(item.id) }) { Text("确认写入") }; TextButton(onClick = { skip(item.id) }) { Text("跳过") } }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = if (selected == null) dismiss else back) { Text(if (selected == null) "关闭" else "返回") } },
        confirmButton = { if (selected?.status == PdfTextImportTaskStatus.FAILED) TextButton(onClick = { retry(selected.id) }) { Text("重试") } else if (selected != null && selected.status !in setOf(PdfTextImportTaskStatus.COMPLETED, PdfTextImportTaskStatus.CANCELLED)) TextButton(onClick = cancel) { Text("取消任务") } },
    )
}
