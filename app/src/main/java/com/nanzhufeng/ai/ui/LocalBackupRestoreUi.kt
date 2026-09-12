package com.nanzhufeng.ai.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

enum class LocalBackupOperation { EXPORT, RESTORE }

data class LocalBackupUiState(
    val visible: Boolean = false,
    val workingOperation: LocalBackupOperation? = null,
    val statusOperation: LocalBackupOperation? = null,
    val preflight: LocalBackupPreflight? = null,
    val replaceLocal: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
) {
    val working get() = workingOperation != null
}

class LocalBackupRestoreViewModel(private val manager: LocalBackupRestoreManager) : ViewModel() {
    var state by mutableStateOf(LocalBackupUiState()); private set
    fun show() { state = state.copy(visible = true, statusOperation = null, notice = null, error = null) }
    fun dismiss() { if (!state.working) state = state.copy(visible = false) }
    fun exported(uri: Uri) = runIo(LocalBackupOperation.EXPORT) { manager.export(uri) }
    fun selected(uri: Uri) = runIo(LocalBackupOperation.RESTORE) { manager.preflight(uri) }
    fun replace(value: Boolean) { state = state.copy(replaceLocal = value) }
    fun restore() { val p = state.preflight ?: return; runIo(LocalBackupOperation.RESTORE) { manager.restore(p.fingerprint, state.replaceLocal) } }
    fun cancel() { manager.cancelPendingRestore(); state = state.copy(preflight = null, replaceLocal = false, statusOperation = LocalBackupOperation.RESTORE, notice = "已取消本次恢复，本机数据没有变化。") }
    private fun runIo(operation: LocalBackupOperation, block: () -> LocalBackupResult) { state = state.copy(workingOperation = operation, statusOperation = null, notice = null, error = null); viewModelScope.launch { apply(operation, withContext(Dispatchers.IO) { block() }) } }
    private fun apply(operation: LocalBackupOperation, result: LocalBackupResult) { state = when (result) {
        is LocalBackupResult.Exported -> state.copy(workingOperation = null, statusOperation = LocalBackupOperation.EXPORT, notice = "备份完成，已确认保存的文件可以正常打开。")
        is LocalBackupResult.Preflighted -> state.copy(workingOperation = null, statusOperation = LocalBackupOperation.RESTORE, preflight = result.preflight, replaceLocal = result.preflight.conflicts.isEmpty(), notice = "备份文件检查通过，可以继续选择是否恢复。")
        is LocalBackupResult.RestoredRestartRequired -> state.copy(workingOperation = null, statusOperation = LocalBackupOperation.RESTORE, notice = "恢复完成。请完全退出并重新打开 App 后再继续使用。")
        is LocalBackupResult.Rejected -> state.copy(workingOperation = null, statusOperation = operation, error = result.reason)
        is LocalBackupResult.Failed -> state.copy(workingOperation = null, statusOperation = operation, error = result.reason)
    } }
    class Factory(private val manager: LocalBackupRestoreManager) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LocalBackupRestoreViewModel(manager) as T }
}

@Composable internal fun LocalBackupRestorePage(state: LocalBackupUiState, onExport: () -> Unit, onImport: () -> Unit, onReplace: (Boolean) -> Unit, onRestore: () -> Unit, onCancel: () -> Unit, grouped: Boolean = false) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (grouped) 0.dp else 10.dp)) {
        if (grouped) {
            DataStorageGroupedActionRow(label = "备份", onClick = onExport, enabled = !state.working, working = state.workingOperation == LocalBackupOperation.EXPORT)
            LocalBackupStatusMessage(state, LocalBackupOperation.EXPORT)
            DataStorageGroupedDivider()
            DataStorageGroupedActionRow(label = "恢复", onClick = onImport, enabled = !state.working, working = state.workingOperation == LocalBackupOperation.RESTORE)
            LocalBackupStatusMessage(state, LocalBackupOperation.RESTORE)
        } else {
            OutlinedButton(onClick = onExport, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text(if (state.workingOperation == LocalBackupOperation.EXPORT) "正在备份…" else "备份") }
            OutlinedButton(onClick = onImport, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text(if (state.workingOperation == LocalBackupOperation.RESTORE) "正在恢复…" else "恢复") }
            LocalBackupStatusMessage(state, state.statusOperation)
        }
        state.preflight?.let { p ->
            Text("预检：格式 ${p.format} v${p.version} · Schema ${p.schemaVersion} · 资产 ${p.assetBytes} B")
            Text("数据：" + p.tableCounts.entries.sortedBy { it.key }.joinToString(" · ") { "${it.key} ${it.value}" }, color = SecondaryText)
            if (p.conflicts.isNotEmpty()) { Text(p.conflicts.joinToString("\n"), color = ErrorRed); OutlinedButton(onClick = { onReplace(false) }, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("取消，不替换本地") }; Button(onClick = { onReplace(true) }, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text(if (state.replaceLocal) "已选择：替换本地" else "选择替换本地（强确认）") } }
            Button(onClick = onRestore, enabled = !state.working && (p.conflicts.isEmpty() || state.replaceLocal), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("恢复并要求重启") }
            OutlinedButton(onClick = onCancel, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = P5AInteractiveShape, border = null, colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText)) { Text("取消此次恢复") }
        }
}

@Composable
private fun LocalBackupStatusMessage(state: LocalBackupUiState, operation: LocalBackupOperation?) {
    if (state.statusOperation != operation) return
    val message = state.notice ?: state.error ?: return
    Surface(modifier = Modifier.fillMaxWidth(), color = ForegroundSurface) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = if (state.error == null) BrandGreen else ErrorRed,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
