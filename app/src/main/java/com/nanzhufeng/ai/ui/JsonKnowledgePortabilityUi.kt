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

data class JsonKnowledgeImportUiState(val visible: Boolean = false, val working: Boolean = false, val tasks: List<JsonKnowledgeTask> = emptyList(), val selected: JsonKnowledgeTask? = null, val editing: JsonKnowledgeImportItemId? = null, val title: String = "", val body: String = "", val tags: String = "")
class JsonKnowledgeImportViewModel(private val imports: ManageJsonKnowledgeImportUseCase) : ViewModel() {
    var state by mutableStateOf(JsonKnowledgeImportUiState()); private set
    fun show() = reload(open = true); fun dismiss() { if (!state.working) state = state.copy(visible = false) }; fun open(task: JsonKnowledgeTask) { state = state.copy(selected = task, editing = null) }; fun back() { state = state.copy(selected = null, editing = null) }
    fun selectedFile(name: String, mime: String, bytes: ByteArray) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.select(name, mime, bytes) }; reload() } }
    fun edit(id: JsonKnowledgeImportItemId) { state.selected?.items?.firstOrNull { it.id == id }?.let { state = state.copy(editing = id, title = it.title, body = it.body, tags = it.tags.joinToString(",")) } }
    fun update(title: String = state.title, body: String = state.body, tags: String = state.tags) { state = state.copy(title = title, body = body, tags = tags) }
    fun confirm(id: JsonKnowledgeImportItemId) { val task = state.selected ?: return; val item = task.items.firstOrNull { it.id == id } ?: return; val edited = state.editing == id; state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { imports.confirm(task.id, id, if (edited) state.title else item.title, if (edited) state.body else item.body, if (edited) state.tags.split(',').map(String::trim).filter(String::isNotEmpty).toSet() else item.tags) }; reload(task.id) } }
    fun skip(id: JsonKnowledgeImportItemId) = mutate { imports.skip(it, id) }; fun cancel() = mutate { imports.cancel(it) }; fun retry(id: JsonKnowledgeTaskId) = mutate(id) { imports.retry(it) }
    private fun mutate(action: (JsonKnowledgeTaskId) -> Unit) { state.selected?.id?.let { mutate(it, action) } }; private fun mutate(id: JsonKnowledgeTaskId, action: (JsonKnowledgeTaskId) -> Unit) { state = state.copy(working = true); viewModelScope.launch { withContext(Dispatchers.IO) { action(id) }; reload(id) } }
    private fun reload(selected: JsonKnowledgeTaskId? = state.selected?.id, open: Boolean = false) { viewModelScope.launch { val tasks = withContext(Dispatchers.IO) { imports.list() }; state = state.copy(visible = state.visible || open, working = false, tasks = tasks, selected = selected?.let { id -> tasks.firstOrNull { it.id == id } }, editing = null) } }
    class Factory(private val imports: ManageJsonKnowledgeImportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = JsonKnowledgeImportViewModel(imports) as T }
}
data class JsonKnowledgeExportUiState(val visible: Boolean = false, val working: Boolean = false, val entries: List<KnowledgeSearchResult> = emptyList(), val selected: Set<KnowledgeItemId> = emptySet(), val result: JsonKnowledgeExportResult? = null, val message: String? = null)
class JsonKnowledgeExportViewModel(private val knowledge: ManageKnowledgeUseCase, private val exporter: ExportJsonKnowledgeUseCase) : ViewModel() {
    var state by mutableStateOf(JsonKnowledgeExportUiState()); private set
    fun show() { state = state.copy(visible = true, working = true); viewModelScope.launch { state = state.copy(working = false, entries = withContext(Dispatchers.IO) { knowledge.search(KnowledgeSearchFilter()) }) } }; fun dismiss() { if (!state.working) state = state.copy(visible = false) }; fun toggle(id: KnowledgeItemId) { state = state.copy(selected = if (id in state.selected) state.selected - id else state.selected + id, message = null) }
    fun export() { if (state.working || state.selected.isEmpty()) return; state = state.copy(working = true); viewModelScope.launch { val r = withContext(Dispatchers.IO) { exporter.execute(state.selected) }; state = state.copy(working = false, result = r, message = if (r == null) "未创建文件：范围中存在非活动正式 Knowledge。" else null) } }
    class Factory(private val knowledge: ManageKnowledgeUseCase, private val exporter: ExportJsonKnowledgeUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = JsonKnowledgeExportViewModel(knowledge, exporter) as T }
}

@Composable fun JsonKnowledgePortabilityCard(onImport: () -> Unit, onExport: () -> Unit) = WhiteCard { Text("JSON 知识导入 / 导出", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("仅支持版本化 nfai.knowledge.json/v1；仅在前台私有复制、逐项确认。系统中断后可手动重试；导出只含明确选择的活动正式 Knowledge。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onImport, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("导入 JSON") }; OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出 JSON") } } }
@Composable fun JsonKnowledgeImportDialog(state: JsonKnowledgeImportUiState, dismiss: () -> Unit, back: () -> Unit, open: (JsonKnowledgeTask) -> Unit, retry: (JsonKnowledgeTaskId) -> Unit, edit: (JsonKnowledgeImportItemId) -> Unit, update: (String, String, String) -> Unit, confirm: (JsonKnowledgeImportItemId) -> Unit, skip: (JsonKnowledgeImportItemId) -> Unit, cancel: () -> Unit) {
    val selected = state.selected
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (selected == null) "JSON 导入任务" else "JSON 任务详情", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.p5aKeyboardTraversal().heightIn(max = 540.dp).verticalScroll(rememberScrollState())) {
                if (selected == null) {
                    Text("任务和私有副本会在重建后回读；JSON 字符串不会被执行或解释为指令。", color = SecondaryText)
                    state.tasks.forEach { row -> TextButton(onClick = { open(row) }) { Text("${row.asset?.displayName ?: "未完成选择"} · ${row.status}") } }
                } else {
                    Text("${selected.asset?.displayName} · ${selected.status}")
                    selected.failure?.let { Text("失败：$it。可重试或取消。", color = ErrorRed) }
                    selected.items.forEach { item ->
                        HorizontalDivider()
                        Text("${item.ordinal + 1}. ${item.title} · ${item.status}")
                        if (item.requestedScope == KnowledgeScope.PROJECT) Text("未提供明确 Project 映射：确认时将写入 GLOBAL。", color = SecondaryText)
                        if (item.status == JsonKnowledgeItemStatus.PENDING_CONFIRMATION) {
                            TextButton(onClick = { edit(item.id) }) { Text("编辑此项") }
                            if (state.editing == item.id) {
                                OutlinedTextField(value = state.title, onValueChange = { update(it, state.body, state.tags) }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                                OutlinedTextField(value = state.body, onValueChange = { update(state.title, it, state.tags) }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                                OutlinedTextField(value = state.tags, onValueChange = { update(state.title, state.body, it) }, label = { Text("标签") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
                            }
                            Row { TextButton(onClick = { confirm(item.id) }) { Text("确认写入") }; TextButton(onClick = { skip(item.id) }) { Text("跳过") } }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = if (selected == null) dismiss else back) { Text(if (selected == null) "关闭" else "返回") } },
        confirmButton = { if (selected?.status == JsonKnowledgeTaskStatus.FAILED) TextButton(onClick = { retry(selected.id) }) { Text("重试") } else if (selected != null && selected.status !in setOf(JsonKnowledgeTaskStatus.COMPLETED, JsonKnowledgeTaskStatus.CANCELLED)) TextButton(onClick = cancel) { Text("取消任务") } },
    )
}
@Composable fun JsonKnowledgeExportDialog(state: JsonKnowledgeExportUiState, dismiss: () -> Unit, toggle: (KnowledgeItemId) -> Unit, export: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp), title = { Text("导出 JSON Knowledge") }, text = { Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) { Text("只导出明确选择的 ACTIVE 正式 Knowledge；原 ID 仅作为导入来源，导入时会重映射。不会输出 URI、路径、Key、Prompt、Provider payload、附件、关系或临时 Context。", color = SecondaryText, style = MaterialTheme.typography.bodySmall); state.entries.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(item.id in state.selected, { toggle(item.id) }); Column { Text(item.title); Text(item.tags.joinToString(" · ").ifBlank { "无标签" }, color = SecondaryText, style = MaterialTheme.typography.bodySmall) } } }; state.result?.let { Text("已原子写入、回读并校验 SHA-256：${it.fileName} · ${it.sha256}", color = BrandGreen) }; state.message?.let { Text(it, color = ErrorRed) } } }, dismissButton = { TextButton(onClick = dismiss) { Text("关闭") } }, confirmButton = { Button(onClick = export, enabled = !state.working && state.selected.isNotEmpty()) { Text("导出 ${state.selected.size} 项") } })
}
