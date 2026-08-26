package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.ProjectId
import com.nanzhufeng.ai.domain.ProjectListScope

@Composable
fun ProjectWorkspacePage(state: ProjectUiState, viewModel: ProjectViewModel) {
    var editingInstruction by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var instruction by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.createDialogVisible) {
        if (state.createDialogVisible) {
            title = ""
            description = ""
        }
    }
    val selected = state.projects.firstOrNull { it.project.id == state.selectedProjectId }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("项目、项目指令和知识范围只保存在本机；不会构造 Prompt、读取 Key 或连接 Provider。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.setScope(ProjectListScope.ACTIVE) }, shape = RoundedCornerShape(14.dp)) { Text("活动") }
                    OutlinedButton(onClick = { viewModel.setScope(ProjectListScope.ARCHIVED) }, shape = RoundedCornerShape(14.dp)) { Text("归档") }
                    Button(onClick = viewModel::showCreateDialog, shape = RoundedCornerShape(14.dp)) { Text("新建") }
                }
                if (state.projects.isEmpty()) Text("暂无${if (state.scope == ProjectListScope.ACTIVE) "活动" else "归档"}项目。", color = SecondaryText)
                state.projects.forEach { snapshot ->
                    OutlinedButton(onClick = { viewModel.select(snapshot.project.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(snapshot.project.title)
                            Text(snapshot.project.description.ifBlank { "没有项目说明" }, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        }
                    }
                }
                selected?.let { snapshot ->
                    Spacer(Modifier.height(2.dp))
                    Text("项目详情", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${snapshot.instructionRevisions.size} 个指令修订 · 当前优先级低于系统与安全规则。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.pin(snapshot.project.id, snapshot.project.pinnedAt == null) }, shape = RoundedCornerShape(14.dp)) { Text(if (snapshot.project.pinnedAt == null) "置顶" else "取消置顶") }
                        OutlinedButton(onClick = { viewModel.archive(snapshot.project.id, snapshot.project.archivedAt == null) }, shape = RoundedCornerShape(14.dp)) { Text(if (snapshot.project.archivedAt == null) "归档" else "恢复") }
                        Button(onClick = { editingInstruction = true; instruction = snapshot.activeInstruction?.content.orEmpty() }, shape = RoundedCornerShape(14.dp)) { Text("编辑指令") }
                    }
                    snapshot.instructionRevisions.takeLast(4).reversed().forEach { revision ->
                        Text("r${revision.revision} · ${if (revision.isEmptyInstruction) "已清空指令" else "用户指令"} · ${revision.contentHash.take(12)}", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                    }
                }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SecondaryText) }
    }
    if (state.createDialogVisible) AlertDialog(
        onDismissRequest = viewModel::dismissCreateDialog, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp), title = { Text("新建项目") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("项目标题") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(description, { description = it }, label = { Text("项目说明（可选）") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { viewModel.create(title, description); viewModel.dismissCreateDialog() }, shape = RoundedCornerShape(14.dp)) { Text("创建") } }, dismissButton = { TextButton(onClick = viewModel::dismissCreateDialog, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
    )
    selected?.let { snapshot -> if (editingInstruction) AlertDialog(
        onDismissRequest = { editingInstruction = false }, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp), title = { Text("项目指令") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("空内容会产生一条可审计的“已清空”修订。项目指令不能覆盖系统或安全规则。", style = MaterialTheme.typography.bodySmall, color = SecondaryText); OutlinedTextField(instruction, { instruction = it }, minLines = 5, label = { Text("用户拥有的本地指令") }, modifier = Modifier.fillMaxWidth().p5aKeyboardTraversal()) } },
        confirmButton = { Button(onClick = { viewModel.updateInstruction(snapshot.project.id, instruction); editingInstruction = false }, shape = RoundedCornerShape(14.dp)) { Text("保存新修订") } }, dismissButton = { TextButton(onClick = { editingInstruction = false }, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
    ) }
}

@Composable
fun ProjectAssignmentDialog(
    projects: List<com.nanzhufeng.ai.domain.ProjectSnapshot>, current: ProjectId?,
    onAssign: (ProjectId?, Boolean) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = ForegroundSurface, shape = RoundedCornerShape(24.dp), title = { Text("归入项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("这是本机显式归属操作；不会改变消息树、调用、附件或导出。", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                projects.forEach { item ->
                    OutlinedButton(onClick = { onAssign(item.project.id, true); onDismiss() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(if (item.project.id == current) "当前：${item.project.title}" else item.project.title)
                    }
                }
                if (current != null) TextButton(onClick = { onAssign(current, false); onDismiss() }, shape = RoundedCornerShape(14.dp)) { Text("移出当前项目") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("取消") } },
    )
}
