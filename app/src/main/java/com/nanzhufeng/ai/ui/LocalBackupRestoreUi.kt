package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.LocalBackupPreflight
import com.nanzhufeng.ai.domain.LocalBackupRestoreManager
import com.nanzhufeng.ai.domain.LocalBackupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalBackupUiState(val visible: Boolean = false, val working: Boolean = false, val preflight: LocalBackupPreflight? = null, val replaceLocal: Boolean = false, val notice: String? = null, val error: String? = null)

class LocalBackupRestoreViewModel(private val manager: LocalBackupRestoreManager) : ViewModel() {
    var state by mutableStateOf(LocalBackupUiState()); private set
    fun show() { state = state.copy(visible = true, notice = null, error = null) }
    fun dismiss() { if (!state.working) state = state.copy(visible = false) }
    fun exported(uri: Uri) = runIo { manager.export(uri) }
    fun selected(uri: Uri) = runIo { manager.preflight(uri) }
    fun replace(value: Boolean) { state = state.copy(replaceLocal = value) }
    fun restore() { val p = state.preflight ?: return; runIo { manager.restore(p.fingerprint, state.replaceLocal) } }
    fun cancel() { manager.cancelPendingRestore(); state = state.copy(preflight = null, replaceLocal = false, notice = "已取消；未改动本地数据。") }
    private fun runIo(block: () -> LocalBackupResult) { state = state.copy(working = true, notice = null, error = null); viewModelScope.launch { apply(withContext(Dispatchers.IO) { block() }) } }
    private fun apply(result: LocalBackupResult) { state = when (result) {
        is LocalBackupResult.Exported -> state.copy(working = false, notice = "备份已 SAF 回读校验：${result.artifact.sha256.take(12)}…")
        is LocalBackupResult.Preflighted -> state.copy(working = false, preflight = result.preflight, replaceLocal = result.preflight.conflicts.isEmpty(), notice = "预检通过：Schema ${result.preflight.schemaVersion}，请确认恢复方式。")
        is LocalBackupResult.RestoredRestartRequired -> state.copy(working = false, notice = "替换已完成。为避免旧 Room 引用，现请手动完全重启 App；不会自动继续任务。")
        is LocalBackupResult.Rejected -> state.copy(working = false, error = result.reason)
        is LocalBackupResult.Failed -> state.copy(working = false, error = result.reason)
    } }
    class Factory(private val manager: LocalBackupRestoreManager) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalBackupRestoreViewModel(manager) as T }
}

@Composable internal fun LocalBackupRestoreDialog(state: LocalBackupUiState, onDismiss: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onReplace: (Boolean) -> Unit, onRestore: () -> Unit, onCancel: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, containerColor = Color.White, title = { Text("备份与恢复") },
    text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("手工本地备份，不等于云同步。包含一致性数据库快照与必要私有资产；不会包含 Provider 凭据、路由偏好、诊断、导出或签名信息。", color = SecondaryText)
        OutlinedButton(onClick = onExport, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("选择位置导出完整备份") }
        OutlinedButton(onClick = onImport, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("选择备份包并严格预检") }
        state.preflight?.let { p ->
            Text("预检：格式 ${p.format} v${p.version} · Schema ${p.schemaVersion} · 资产 ${p.assetBytes} B")
            Text("数据：" + p.tableCounts.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key} ${it.value}" }, color = SecondaryText)
            if (p.conflicts.isNotEmpty()) { Text(p.conflicts.joinToString("\n"), color = ErrorRed); OutlinedButton(onClick = { onReplace(false) }, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("取消，不替换本地") }; Button(onClick = { onReplace(true) }, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text(if (state.replaceLocal) "已选择：替换本地" else "选择替换本地（强确认）") } }
            Button(onClick = onRestore, enabled = !state.working && (p.conflicts.isEmpty() || state.replaceLocal), modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("恢复并要求重启") }
            OutlinedButton(onClick = onCancel, enabled = !state.working, modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape) { Text("取消此次恢复") }
        }
        state.notice?.let { Text(it, color = BrandGreen) }; state.error?.let { Text(it, color = ErrorRed) }
    } }, confirmButton = { OutlinedButton(onClick = onDismiss, enabled = !state.working, shape = P5AInteractiveShape) { Text("关闭") } },
)
