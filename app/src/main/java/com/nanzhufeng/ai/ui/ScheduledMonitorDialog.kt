package com.nanzhufeng.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nanzhufeng.ai.domain.ScheduledMonitorCadence
import com.nanzhufeng.ai.domain.ScheduledMonitorStatus
import com.nanzhufeng.ai.domain.ScheduledMonitorTask
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ScheduledMonitorDialog(
    state: ScheduledMonitorUiState,
    onDismiss: () -> Unit,
    onStartCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onCadenceChanged: (ScheduledMonitorCadence) -> Unit,
    onCreate: () -> Unit,
    onPause: (ScheduledMonitorTask, Boolean) -> Unit,
    onDelete: (ScheduledMonitorTask) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = PageBackground,
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.creating) "新建计划" else "已计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(50)) { Text("关闭") }
                }
                if (state.creating && state.refining) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = AccentOrange)
                        Text("正在用千问整理提醒草案", fontWeight = FontWeight.Bold)
                        Text("仅发送本对话开头的一问一答；不会发送完整对话或附件。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (state.creating) {
                    ScheduledMonitorEditor(
                        state = state,
                        onTitleChanged = onTitleChanged,
                        onInstructionChanged = onInstructionChanged,
                        onCadenceChanged = onCadenceChanged,
                        onCancel = onCancelCreate,
                        onCreate = onCreate,
                    )
                } else {
                    Text("按所选周期联网生成简报，可随时暂停或删除。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("开启结果通知")
                    }
                    state.notice?.let { Text(it, color = AccentOrange, style = MaterialTheme.typography.bodySmall) }
                    if (state.tasks.isEmpty()) {
                        Text("暂无计划。可以添加新闻、公告或价格等长期跟踪任务。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.tasks.forEach { task ->
                            ScheduledMonitorTaskCard(task, onPause = onPause, onDelete = onDelete)
                        }
                    }
                    Button(onClick = onStartCreate, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加计划")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMonitorEditor(
    state: ScheduledMonitorUiState,
    onTitleChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onCadenceChanged: (ScheduledMonitorCadence) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    Text("仅发送任务名称和监控要求，不发送完整对话或附件。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    TextField(
        value = state.draft.title,
        onValueChange = onTitleChanged,
        label = { Text("计划名称（20字内）") },
        singleLine = true,
        colors = scheduledMonitorTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    TextField(
        value = state.draft.instruction,
        onValueChange = onInstructionChanged,
        label = { Text("监控要求") },
        placeholder = { Text("例如：每天检查英伟达的财报、SEC 文件和重大新闻，只报告可核验变化。") },
        minLines = 4,
        colors = scheduledMonitorTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("运行频率", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScheduledMonitorCadence.entries.forEach { cadence ->
            val selected = state.draft.cadence == cadence
            Button(
                onClick = { onCadenceChanged(cadence) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = if (selected) AccentOrange else NeutralSystemSurface, contentColor = if (selected) Color.White else BodyText),
            ) { Text(cadence.label) }
        }
    }
    state.notice?.let { Text(it, color = AccentOrange, style = MaterialTheme.typography.bodySmall) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("取消") }
        Button(onClick = onCreate, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("保存并开始") }
    }
}

@Composable
private fun ScheduledMonitorTaskCard(
    task: ScheduledMonitorTask,
    onPause: (ScheduledMonitorTask, Boolean) -> Unit,
    onDelete: (ScheduledMonitorTask) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ForegroundSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (task.status == ScheduledMonitorStatus.ACTIVE) "监控 · ${task.cadence.label}" else "已暂停 · ${task.cadence.label}", color = if (task.status == ScheduledMonitorStatus.ACTIVE) Color(0xFF287CC7) else SecondaryText, style = MaterialTheme.typography.labelMedium)
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(task.instruction, color = SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            task.latestResult?.let {
                Text("最近结果", style = MaterialTheme.typography.labelMedium, color = SecondaryText)
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            task.lastSafeErrorCode?.let { Text("最近一次未完成：$it", color = AccentOrange, style = MaterialTheme.typography.bodySmall) }
            Text(
                if (task.status == ScheduledMonitorStatus.ACTIVE) "下次运行：${scheduledMonitorTime(task.nextRunAt)}" else "已停止联网；恢复后立即安排下一次运行。",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onPause(task, task.status == ScheduledMonitorStatus.ACTIVE) }, shape = RoundedCornerShape(50)) {
                    Icon(if (task.status == ScheduledMonitorStatus.ACTIVE) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (task.status == ScheduledMonitorStatus.ACTIVE) "暂停" else "恢复")
                }
                OutlinedButton(onClick = { onDelete(task) }, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun scheduledMonitorTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = ForegroundSurface,
    unfocusedContainerColor = ForegroundSurface,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

private fun scheduledMonitorTime(instant: java.time.Instant): String =
    DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(ZoneId.systemDefault()).format(instant)
