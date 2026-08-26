package com.nanzhufeng.ai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.nanzhufeng.ai.data.P7CAndroidCloudGateway
import com.nanzhufeng.ai.domain.P7CServiceAvailability

data class P7DAccountSyncUiState(val detailVisible: Boolean = false, val configured: Boolean = false)

/** Opening this page is presentation-only: it never restores an account or schedules work. */
class P7DAccountSyncViewModel : ViewModel() {
    var state by mutableStateOf(P7DAccountSyncUiState(configured = P7CAndroidCloudGateway.availability() is P7CServiceAvailability.Configured))
        private set
    fun open() { state = state.copy(detailVisible = true) }
    fun close() { state = state.copy(detailVisible = false) }
}

@androidx.compose.runtime.Composable
internal fun AccountSyncStatusCard(state: P7DAccountSyncUiState, onOpen: () -> Unit) = WhiteCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Google 账号与同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (state.configured) "等待已验证账号" else "尚未配置 · 离线可用", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onOpen, modifier = Modifier.height(48.dp), shape = P5AInteractiveShape) { Text("查看") }
    }
}

@androidx.compose.runtime.Composable
internal fun P7DAccountSyncScreen(state: P7DAccountSyncUiState, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回设置") }
            Text("Google 账号与同步", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(36.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(if (state.configured) "等待已验证账号" else "尚未配置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (state.configured) "请先完成 Google 身份验证。" else "本机保持离线可用；尚未发起登录、同步或网络请求。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        WhiteCard {
            Text("加密范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("仅未来明确允许的结构化快照会进入端到端加密容器；不会同步密钥、恢复码、Provider 设置、诊断、头像缓存或本地私有资产。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        WhiteCard {
            Text("同步与恢复准备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("当前未配置，恢复入口保持禁用。只有真实已验证账号、恢复码确认、方向确认和远端 revision 校验同时成立后，受控链才会写入候选库；这里不会伪造登录或云端成功。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) {
                Icon(Icons.Rounded.Sync, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("立即同步")
            }
        }
        WhiteCard {
            Text("账号操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("没有已验证账号时，切换与退出不会伪造身份或修改本机数据。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("切换 Google 账号") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(48.dp), shape = P5AInteractiveShape) { Text("退出 Google 账号") }
        }
    }
}
