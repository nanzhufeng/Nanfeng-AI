package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.data.AndroidDataStorageLocation
import com.nanzhufeng.ai.data.AndroidDataStorageLocationManager
import com.nanzhufeng.ai.data.AndroidDataStorageLocationState
import com.nanzhufeng.ai.data.AndroidDataStorageMoveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DataStorageLocationUiState(
    val location: AndroidDataStorageLocationState,
    val selecting: Boolean = false,
    val pendingTarget: AndroidDataStorageLocation? = null,
    val working: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

class DataStorageLocationViewModel(private val manager: AndroidDataStorageLocationManager) : ViewModel() {
    var state by mutableStateOf(DataStorageLocationUiState(manager.state()))
        private set

    fun showChoices() { if (!state.working) state = state.copy(selecting = !state.selecting, pendingTarget = null, notice = null, error = null) }
    fun select(target: AndroidDataStorageLocation) { if (!state.working) state = state.copy(pendingTarget = target, notice = null, error = null) }
    fun cancel() { if (!state.working) state = state.copy(selecting = false, pendingTarget = null) }

    fun move() {
        val target = state.pendingTarget ?: return
        state = state.copy(working = true, notice = null, error = null)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { manager.moveTo(target) }) {
                is AndroidDataStorageMoveResult.RestartRequired -> state = state.copy(
                    location = result.state,
                    selecting = false,
                    pendingTarget = null,
                    working = false,
                    notice = "已迁移到新的手机存储位置。请完全退出并重新打开 App。",
                )
                is AndroidDataStorageMoveResult.Rejected -> state = state.copy(working = false, error = result.reason)
                is AndroidDataStorageMoveResult.Failed -> state = state.copy(working = false, error = "${result.reason} 请完全退出并重新打开 App。")
            }
        }
    }

    class Factory(private val manager: AndroidDataStorageLocationManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DataStorageLocationViewModel(manager) as T
    }
}

@Composable
internal fun DataStorageLocationCard(
    state: DataStorageLocationUiState,
    onShowChoices: () -> Unit,
    onSelect: (AndroidDataStorageLocation) -> Unit,
    onMove: () -> Unit,
    onCancel: () -> Unit,
) = DataStorageGroupedCard {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("数据保存路径", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onShowChoices, enabled = !state.working) { Text("更改路径") }
    }
    Text(
        state.location.absolutePath,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        color = SecondaryText,
    )
    if (state.selecting) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StorageTargetButton(
                label = "应用内部存储",
                selected = state.pendingTarget == AndroidDataStorageLocation.INTERNAL,
                enabled = !state.working,
                onClick = { onSelect(AndroidDataStorageLocation.INTERNAL) },
            )
            StorageTargetButton(
                label = "手机设备存储",
                selected = state.pendingTarget == AndroidDataStorageLocation.EXTERNAL,
                enabled = !state.working && state.location.externalAvailable,
                onClick = { onSelect(AndroidDataStorageLocation.EXTERNAL) },
            )
            if (state.pendingTarget != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCancel, enabled = !state.working, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = onMove, enabled = !state.working, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)) {
                        Text(if (state.working) "正在迁移…" else "迁移到此位置")
                    }
                }
            }
        }
    }
    state.notice?.let { Text(it, modifier = Modifier.padding(20.dp), color = SecondaryText) }
    state.error?.let { Text(it, modifier = Modifier.padding(20.dp), color = androidx.compose.ui.graphics.Color(0xFFC83E36)) }
}

@Composable
private fun StorageTargetButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AccentOrange.copy(alpha = 0.10f) else ForegroundSurface,
            contentColor = BodyText,
        ),
    ) { Text(label) }
}
