package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
fun MemoryWorkspaceDialog(state: MemoryUiState, viewModel: MemoryViewModel, projects: List<ProjectSnapshot>, conversations: List<Conversation>) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf<MemoryId?>(null) }
    var confirmingBatch by rememberSaveable { mutableStateOf(false) }
    val selected = state.memories.firstOrNull { it.memory.id == state.selectedId }
    AlertDialog(
        onDismissRequest = viewModel::dismissDialog, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
        title = { Text("长期 Memory", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("仅在你明确创建、编辑或导入后保存到本机。不会自动提取，也不会自动加入对话上下文。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                OutlinedTextField(state.search, viewModel::updateSearch, label = { Text("搜索标题或正文") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterButton("全部范围", state.scopeFilter == null) { viewModel.setScopeFilter(null) }
                    FilterButton("全局", state.scopeFilter == MemoryScopeKind.GLOBAL) { viewModel.setScopeFilter(MemoryScopeKind.GLOBAL) }
                    FilterButton("项目", state.scopeFilter == MemoryScopeKind.PROJECT) { viewModel.setScopeFilter(MemoryScopeKind.PROJECT) }
                    FilterButton("会话", state.scopeFilter == MemoryScopeKind.CONVERSATION) { viewModel.setScopeFilter(MemoryScopeKind.CONVERSATION) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterButton("全部状态", state.statusFilter == null) { viewModel.setStatusFilter(null) }
                    FilterButton("可用", state.statusFilter == MemoryStatus.ACTIVE) { viewModel.setStatusFilter(MemoryStatus.ACTIVE) }
                    FilterButton("暂停", state.statusFilter == MemoryStatus.PAUSED) { viewModel.setStatusFilter(MemoryStatus.PAUSED) }
                    FilterButton("已删", state.statusFilter == MemoryStatus.DELETED) { viewModel.setStatusFilter(MemoryStatus.DELETED) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { creating = true }, shape = RoundedCornerShape(14.dp)) { Text("新建") }
                    if (state.selectedForBatch.isNotEmpty()) OutlinedButton(onClick = { confirmingBatch = true }, shape = RoundedCornerShape(14.dp)) { Text("删除 ${state.selectedForBatch.size} 项") }
                }
                if (state.memories.isEmpty()) Text("没有符合筛选的本地记忆。", color = SecondaryText)
                state.memories.forEach { snapshot -> MemoryRow(snapshot, snapshot.memory.id in state.selectedForBatch, { viewModel.select(snapshot.memory.id) }, { viewModel.toggleBatch(snapshot.memory.id) }) }
                selected?.let { snapshot -> MemoryDetail(snapshot, onEdit = { editing = true }, onPause = { viewModel.pause(snapshot.memory.id, snapshot.memory.status == MemoryStatus.ACTIVE) }, onDelete = { confirmingDelete = snapshot.memory.id }) }
                state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::dismissDialog, shape = RoundedCornerShape(14.dp)) { Text("完成") } },
    )
    if (creating) MemoryEditorDialog("新建长期 Memory", projects, conversations, onDismiss = { creating = false }) { title, body, scope -> viewModel.create(title, body, scope); creating = false }
    if (editing && selected != null) MemoryEditorDialog("编辑 Memory（将追加修订）", projects, conversations, selected, onDismiss = { editing = false }) { title, body, scope -> viewModel.update(selected.memory.id, title, body, scope); editing = false }
    confirmingDelete?.let { id -> ConfirmDeleteDialog("删除这条 Memory？", "删除采用本地软删除；普通列表不会再显示正文，历史仍可审计。", { viewModel.delete(id); confirmingDelete = null }, { confirmingDelete = null }) }
    if (confirmingBatch) ConfirmDeleteDialog("删除选中的 ${state.selectedForBatch.size} 条？", "这是本地批量软删除确认；不会影响 Project 或 Conversation 生命周期。", { viewModel.deleteSelected(); confirmingBatch = false }, { confirmingBatch = false })
    state.pendingConflict?.let { conflict -> AlertDialog(
        onDismissRequest = viewModel::dismissDialog, containerColor = Color.White, shape = RoundedCornerShape(24.dp), title = { Text("同概念内容冲突") },
        text = { Text("“${conflict.candidateTitle}” 在相同范围已有不同正文。请明确选择保留已有、并存，或将候选作为现有记忆的新修订。", color = SecondaryText) },
        confirmButton = { Button(onClick = { viewModel.resolveConflict(MemoryConflictResolution.CREATE_PARALLEL) }, shape = RoundedCornerShape(14.dp)) { Text("并存保存") } },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { viewModel.resolveConflict(MemoryConflictResolution.KEEP_EXISTING) }) { Text("保留已有") }; TextButton(onClick = { viewModel.resolveConflict(MemoryConflictResolution.REVISE_EXISTING) }) { Text("作为新修订") } } },
    ) }
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
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp), title = { Text(titleText) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("仅保存你明确确认的本地内容。密码、API Key、Authorization、恢复码和完整卡号会被拒绝，且正文不会落库。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("正文") }, minLines = 4, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterButton("全局", scopeKind == MemoryScopeKind.GLOBAL) { scopeKind = MemoryScopeKind.GLOBAL }; FilterButton("项目", scopeKind == MemoryScopeKind.PROJECT) { scopeKind = MemoryScopeKind.PROJECT }; FilterButton("会话", scopeKind == MemoryScopeKind.CONVERSATION) { scopeKind = MemoryScopeKind.CONVERSATION } }
        if (scopeKind == MemoryScopeKind.PROJECT) { if (projects.isEmpty()) Text("暂无可关联的 Project；不能保存为项目范围。", style = MaterialTheme.typography.bodySmall, color = SecondaryText); projects.forEach { item -> FilterButton(item.project.title, item.project.id.value == projectId) { projectId = item.project.id.value } } }
        if (scopeKind == MemoryScopeKind.CONVERSATION) { if (conversations.isEmpty()) Text("暂无可关联的 Conversation；不能保存为会话范围。", style = MaterialTheme.typography.bodySmall, color = SecondaryText); conversations.forEach { item -> FilterButton(item.title, item.id.value == conversationId) { conversationId = item.id.value } } }
        Text("不会自动加入对话上下文。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    } }, confirmButton = { Button(onClick = { scope?.let { onSave(title, body, it) } }, enabled = scope != null, shape = RoundedCornerShape(14.dp)) { Text("确认保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
@Composable private fun ConfirmDeleteDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(24.dp), title = { Text(title) }, text = { Text(body, color = SecondaryText) }, confirmButton = { Button(onClick = onConfirm, shape = RoundedCornerShape(14.dp)) { Text("确认删除") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
private fun scopeLabel(scope: MemoryScope) = when (scope.kind) { MemoryScopeKind.GLOBAL -> "全局"; MemoryScopeKind.PROJECT -> "项目"; MemoryScopeKind.CONVERSATION -> "会话" }
private fun statusLabel(status: MemoryStatus) = when (status) { MemoryStatus.ACTIVE -> "可用"; MemoryStatus.PAUSED -> "暂停"; MemoryStatus.DELETED -> "已删" }
