package com.nanzhufeng.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.MemoryConflictResolution
import com.nanzhufeng.ai.domain.MemoryId
import com.nanzhufeng.ai.domain.MemoryScope
import com.nanzhufeng.ai.domain.MemoryScopeKind
import com.nanzhufeng.ai.domain.MemorySnapshot
import com.nanzhufeng.ai.domain.MemoryStatus
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectSnapshot

@Composable
fun MemorySummaryPage(
    state: MemoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteMemory: () -> Unit,
    onDisableMemorySummaryGenerationAndUse: () -> Unit,
    onEditSummary: () -> Unit,
    onUpdateSummaryEditor: (String) -> Unit,
    onSaveSummaryEditor: () -> Unit,
    onDismissSummaryEditor: () -> Unit,
) {
    state.summaryEditor?.let { editor ->
        MemorySummaryEditorPage(editor, onUpdateSummaryEditor, onSaveSummaryEditor, onDismissSummaryEditor)
        return
    }
    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var aboutVisible by rememberSaveable { mutableStateOf(false) }
    var deleteConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    var disableConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val updated = state.memories.maxByOrNull { it.memory.updatedAt }?.memory?.updatedAt
    val updatedText = updated?.let { "更新于 ${it.atZone(java.time.ZoneId.systemDefault()).toLocalDate()}" } ?: "更新于刚刚"

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("记忆摘要", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(updatedText, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { menuVisible = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "记忆摘要操作")
                }
                DropdownMenu(
                    expanded = menuVisible,
                    onDismissRequest = { menuVisible = false },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = ForegroundSurface,
                ) {
                    DropdownMenuItem(
                        text = { Text("关于记忆") },
                        onClick = { menuVisible = false; aboutVisible = true },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("编辑摘要") },
                        onClick = { menuVisible = false; onEditSummary() },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("刷新摘要") },
                        onClick = { menuVisible = false; onRefresh() },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("删除记忆", color = ErrorRed) },
                        onClick = { menuVisible = false; deleteConfirmationVisible = true },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = ErrorRed) },
                    )
                    DropdownMenuItem(
                        text = { Text("关闭记忆摘要生成和应用", color = ErrorRed) },
                        onClick = { menuVisible = false; disableConfirmationVisible = true },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = ErrorRed) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.notice?.let { Text(it, color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
            if (state.memories.isEmpty()) {
                Text("还没有记忆摘要。", style = MaterialTheme.typography.bodyLarge, color = SecondaryText)
            }
            state.memories.forEach { snapshot ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        snapshot.memory.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(snapshot.memory.body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        }

    }

    if (aboutVisible) {
        AlertDialog(
            onDismissRequest = { aboutVisible = false },
            containerColor = ForegroundSurface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("关于记忆") },
            text = {
                Text(
                    "这里显示已保存在本机的记忆摘要。启用后，相关内容可用于回答。",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { aboutVisible = false }) { Text("知道了") } },
        )
    }
    if (deleteConfirmationVisible) {
        ConfirmDeleteDialog(
            title = "删除记忆？",
            body = "删除当前记忆摘要；聊天、文件和记忆开关不受影响。",
            onConfirm = { deleteConfirmationVisible = false; onDeleteMemory() },
            onDismiss = { deleteConfirmationVisible = false },
        )
    }
    if (disableConfirmationVisible) {
        ConfirmDeleteDialog(
            title = "关闭记忆摘要生成和应用？",
            body = "停止生成和使用记忆摘要；已保存内容不会删除。",
            onConfirm = {
                disableConfirmationVisible = false
                onDisableMemorySummaryGenerationAndUse()
            },
            onDismiss = { disableConfirmationVisible = false },
        )
    }
}

@Composable
private fun MemorySummaryEditorPage(
    editor: MemorySummaryEditorState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var discardVisible by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable(editor.loading, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(editor.text))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val exit = {
        if (!editor.saving) {
            if (editor.text != editor.originalText) discardVisible = true else onDismiss()
        }
    }
    BackHandler(onBack = exit)
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = exit, enabled = !editor.saving) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回摘要")
            }
            Text("编辑摘要", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSave, enabled = !editor.loading && !editor.saving && editor.text.isNotBlank()) {
                Text(if (editor.saving) "保存中…" else "保存")
            }
        }
        Text("可直接修改全文，也可以全选后粘贴整篇替换。保存后生效。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                field = field.copy(selection = TextRange(0, field.text.length))
                focusRequester.requestFocus()
                keyboard?.show()
            }, enabled = !editor.loading && !editor.saving && field.text.isNotEmpty()) { Text("全选") }
            TextButton(onClick = {
                field = TextFieldValue("")
                onTextChange("")
                focusRequester.requestFocus()
            }, enabled = !editor.loading && !editor.saving && field.text.isNotEmpty()) { Text("清空重写") }
        }
        editor.error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
        if (editor.loading) {
            Text(if (editor.error == null) "正在读取完整摘要…" else "请返回重试", Modifier.padding(vertical = 16.dp))
        } else {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it; onTextChange(it.text) },
                readOnly = editor.saving,
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("输入或粘贴新的记忆摘要") },
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ForegroundSurface,
                    unfocusedContainerColor = ForegroundSurface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
            Text("仅保存到本机，不调用模型。清空所有记忆请返回使用“删除记忆”。",
                Modifier.padding(vertical = 12.dp), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (discardVisible) {
        ConfirmDeleteDialog(
            title = "放弃本次修改？", body = "尚未保存的修改将丢弃，已保存的摘要保持不变。",
            onConfirm = { discardVisible = false; onDismiss() },
            onDismiss = { discardVisible = false },
        )
    }
}

@Composable private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) = OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text(if (selected) "✓ $label" else label, style = MaterialTheme.typography.labelSmall) }
@Composable private fun MemoryRow(snapshot: MemorySnapshot, checked: Boolean, onSelect: () -> Unit, onToggle: () -> Unit) = OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(snapshot.memory.title, fontWeight = FontWeight.Medium)
            Text("${scopeLabel(snapshot.memory.scope)} · ${statusLabel(snapshot.memory.status)} · r${snapshot.revisions.maxOfOrNull { it.revision } ?: 0}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
            Text(snapshot.memory.sourceSummary, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
    }
}
@Composable private fun MemoryDetail(snapshot: MemorySnapshot, onEdit: () -> Unit, onPause: () -> Unit, onDelete: () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Spacer(Modifier.height(2.dp)); Text("Memory 详情", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    if (snapshot.memory.status != MemoryStatus.DELETED) Text(snapshot.memory.body, style = MaterialTheme.typography.bodySmall) else Text("已软删除：普通列表不展示正文。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    Text("来源：${snapshot.memory.source.name} · ${snapshot.memory.sourceStableId} · ${snapshot.memory.sourceSummary}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
    Text("历史：${snapshot.revisions.takeLast(4).reversed().joinToString(" · ") { "r${it.revision}/${statusLabel(it.status)}" }}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
    if (snapshot.memory.status != MemoryStatus.DELETED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onEdit, shape = RoundedCornerShape(14.dp)) { Text("编辑") }; OutlinedButton(onClick = onPause, shape = RoundedCornerShape(14.dp)) { Text(if (snapshot.memory.status == MemoryStatus.ACTIVE) "暂停" else "恢复") }; TextButton(onClick = onDelete) { Text("删除") } }
}

@Composable private fun MemoryEditorDialog(titleText: String, projects: List<ProjectSnapshot>, conversations: List<Conversation>, existing: MemorySnapshot? = null, onDismiss: () -> Unit, onSave: (String, String, MemoryScope) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.memory?.title.orEmpty()) }; var body by rememberSaveable { mutableStateOf(existing?.memory?.body.orEmpty()) }
    var scopeKind by rememberSaveable { mutableStateOf(existing?.memory?.scope?.kind ?: MemoryScopeKind.GLOBAL) }
    var projectId by rememberSaveable { mutableStateOf(existing?.memory?.scope?.projectId?.value ?: projects.firstOrNull()?.project?.id?.value.orEmpty()) }
    var conversationId by rememberSaveable { mutableStateOf(existing?.memory?.scope?.conversationId?.value ?: conversations.firstOrNull()?.id?.value.orEmpty()) }
    val scope = when (scopeKind) { MemoryScopeKind.GLOBAL -> MemoryScope(MemoryScopeKind.GLOBAL); MemoryScopeKind.PROJECT -> projectId.takeIf { it.isNotBlank() }?.let { MemoryScope(MemoryScopeKind.PROJECT, ProjectId(it)) }; MemoryScopeKind.CONVERSATION -> conversationId.takeIf { it.isNotBlank() }?.let { MemoryScope(MemoryScopeKind.CONVERSATION, conversationId = ConversationId(it)) } }
    AlertDialog(onDismissRequest = onDismiss, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp), title = { Text(titleText) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("敏感信息不会保存。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("正文") }, minLines = 4, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterButton("全局", scopeKind == MemoryScopeKind.GLOBAL) { scopeKind = MemoryScopeKind.GLOBAL }; FilterButton("项目", scopeKind == MemoryScopeKind.PROJECT) { scopeKind = MemoryScopeKind.PROJECT }; FilterButton("会话", scopeKind == MemoryScopeKind.CONVERSATION) { scopeKind = MemoryScopeKind.CONVERSATION } }
        if (scopeKind == MemoryScopeKind.PROJECT) { if (projects.isEmpty()) Text("暂无可关联项目。", style = MaterialTheme.typography.bodySmall, color = SecondaryText); projects.forEach { item -> FilterButton(item.project.title, item.project.id.value == projectId) { projectId = item.project.id.value } } }
        if (scopeKind == MemoryScopeKind.CONVERSATION) { if (conversations.isEmpty()) Text("暂无可关联对话。", style = MaterialTheme.typography.bodySmall, color = SecondaryText); conversations.forEach { item -> FilterButton(item.title, item.id.value == conversationId) { conversationId = item.id.value } } }
        Text("启用后，相关记忆可用于回答。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    } }, confirmButton = { Button(onClick = { scope?.let { onSave(title, body, it) } }, enabled = scope != null, shape = RoundedCornerShape(14.dp)) { Text("确认保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
@Composable private fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp), title = { Text(title) }, text = { Text(body, color = SecondaryText) }, confirmButton = { Button(onClick = onConfirm, shape = RoundedCornerShape(14.dp)) { Text("确认删除") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
private fun scopeLabel(scope: MemoryScope) = when (scope.kind) { MemoryScopeKind.GLOBAL -> "全局"; MemoryScopeKind.PROJECT -> "项目"; MemoryScopeKind.CONVERSATION -> "会话" }
private fun statusLabel(status: MemoryStatus) = when (status) { MemoryStatus.ACTIVE -> "可用"; MemoryStatus.PAUSED -> "暂停"; MemoryStatus.DELETED -> "已删" }
