package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nanzhufeng.ai.domain.ConnectionCapabilitySnapshot
import com.nanzhufeng.ai.domain.ConnectionPathGuard
import com.nanzhufeng.ai.domain.DataPath
import com.nanzhufeng.ai.domain.EgressConsentState
import com.nanzhufeng.ai.domain.OnlineIntent
import com.nanzhufeng.ai.domain.PathSelectionResult
import com.nanzhufeng.ai.domain.ReadConnectionCapabilityUseCase

data class DualPathConnectionUiState(
    val detailVisible: Boolean = false,
    val snapshot: ConnectionCapabilitySnapshot? = null,
    val selection: PathSelectionResult? = null,
)

class DualPathConnectionViewModel(private val readCapability: ReadConnectionCapabilityUseCase) : ViewModel() {
    var state by mutableStateOf(DualPathConnectionUiState(snapshot = readCapability.execute()))
        private set
    fun open() { state = state.copy(detailVisible = true, snapshot = readCapability.execute(), selection = null) }
    fun close() { state = state.copy(detailVisible = false, selection = null) }
    fun selectLocal() {
        val snapshot = state.snapshot ?: return
        state = state.copy(selection = ConnectionPathGuard(snapshot).selectLocal())
    }
    fun requestOnlineGuidance() {
        val snapshot = state.snapshot ?: return
        state = state.copy(selection = ConnectionPathGuard(snapshot).selectOnline(
            OnlineIntent("p10b-guidance", DataPath.LOCAL_ONLY, EgressConsentState.REQUIRED_PER_INTENT, costKnown = false, modelAvailable = false),
        ))
    }

    class Factory(private val readCapability: ReadConnectionCapabilityUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = DualPathConnectionViewModel(readCapability) as T
    }
}

@Composable
internal fun DualPathConnectionCard(state: DualPathConnectionUiState, onOpen: () -> Unit) = WhiteCard {
    Text("连接与数据路径", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    val snapshot = state.snapshot
    Text(
        when {
            snapshot == null -> "正在读取本机状态…"
            else -> "本地离线始终可用 · 联网未配置或未验证；不会自动外发本地数据。"
        },
        color = SecondaryText, style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("查看双路径状态") }
}

@Composable
internal fun DualPathConnectionDialog(
    state: DualPathConnectionUiState,
    onDismiss: () -> Unit,
    onSelectLocal: () -> Unit,
    onRequestOnlineGuidance: () -> Unit,
) {
    if (!state.detailVisible) return
    val snapshot = state.snapshot ?: return
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp), shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 10.dp) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本地与联网双路径", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("本地离线", fontWeight = FontWeight.SemiBold)
                Text("LOCAL_OFFLINE / LOCAL_ONLY · 始终可用；断网、未配置或取消时不会丢失本地工作。", color = SecondaryText)
                Text("联网模型", fontWeight = FontWeight.SemiBold)
                Text("ONLINE_PROVIDER · 配置：${snapshot.providerConfiguration}；Key presence：${snapshot.credentialPresence}（只显示存在性）；目录：${snapshot.catalogFreshness}；逐次外发同意：${snapshot.egressConsent}。", color = SecondaryText)
                Text("账号同步", fontWeight = FontWeight.SemiBold)
                Text("ENCRYPTED_SYNC · ${snapshot.syncCapability}；它与模型外发单独配置、单独授权，不会自动上传本地数据。", color = SecondaryText)
                Text("当前联网状态", fontWeight = FontWeight.SemiBold)
                Text("${snapshot.degradedReasons.joinToString()}。这些状态不是成功；需要联网的请求会明确阻止，用户可选择继续本地工作或修复后重试。", color = SecondaryText)
                when (val selection = state.selection) {
                    is PathSelectionResult.LocalReady -> Text("已选择 LOCAL_OFFLINE / ${selection.dataPath}：可继续本地工作；没有调用模型或同步。", color = SecondaryText)
                    is PathSelectionResult.OnlineBlocked -> Text("ONLINE_PROVIDER 未启动：${selection.reasons.joinToString()}。未来须经授权完成配置、价格确认、模型选择与本次外发同意。", color = SecondaryText)
                    else -> Unit
                }
                OutlinedButton(onClick = onSelectLocal, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("继续本地工作") }
                OutlinedButton(onClick = onRequestOnlineGuidance, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("查看联网前提") }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("完成") }
            }
        }
    }
}
