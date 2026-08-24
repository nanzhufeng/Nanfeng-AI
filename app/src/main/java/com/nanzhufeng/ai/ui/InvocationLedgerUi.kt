package com.nanzhufeng.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.AiTaskError
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.InvocationStatus
import com.nanzhufeng.ai.domain.ProviderId
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InvocationLedgerUiState(
    val dialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val records: List<InvocationRecord> = emptyList(),
)

class InvocationLedgerViewModel(private val repository: InvocationRepository) : ViewModel() {
    var state by mutableStateOf(InvocationLedgerUiState())
        private set

    fun showDialog() {
        state = state.copy(dialogVisible = true, isLoading = true)
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) { repository.listNewestFirst() }
            state = InvocationLedgerUiState(dialogVisible = true, records = records)
        }
    }

    fun dismissDialog() {
        state = state.copy(dialogVisible = false)
    }

    class Factory(private val repository: InvocationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InvocationLedgerViewModel::class.java))
            return InvocationLedgerViewModel(repository) as T
        }
    }
}

@Composable
fun InvocationLedgerCard(state: InvocationLedgerUiState, onOpen: () -> Unit) {
    WhiteCard {
        Text("调用记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("仅显示本地安全运行元数据；不会保存密钥、提示正文、完整响应或图片内容。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (state.records.isEmpty()) "查看调用记录" else "查看调用记录（${state.records.size}）")
        }
    }
}

@Composable
fun InvocationLedgerDialog(state: InvocationLedgerUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("调用记录", fontWeight = FontWeight.SemiBold) },
        text = {
            when {
                state.isLoading -> Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = BrandGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("正在读取本地记录…", color = SecondaryText)
                }
                state.records.isEmpty() -> EmptyInvocationLedger()
                else -> Column(
                    modifier = Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.records.forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(color = NeutralBorder)
                        InvocationLedgerRow(record)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.isLoading) { Text("关闭") } },
    )
}

@Composable
private fun EmptyInvocationLedger() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(36.dp))
        Text("尚无本地调用记录", fontWeight = FontWeight.Medium)
        Text("当前未发出任何真实服务请求。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InvocationLedgerRow(record: InvocationRecord) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.status.uiLabel(), color = record.status.uiColor(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(record.completedAt.ledgerTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Text("${record.providerLabel()} · ${record.modelId}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Harness v${record.harnessVersion} · 耗时 ${record.taskRun.durationMillis()} ms · ${record.taskRun.attempts.size} 次 Attempt",
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(record.usageAndCostLabel(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        record.error?.let { error ->
            Text("原因：${error.uiLabel()}", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun InvocationStatus.uiLabel(): String = when (this) {
    InvocationStatus.SUCCEEDED -> "成功"
    InvocationStatus.FAILED -> "失败"
    InvocationStatus.CANCELLED -> "已取消"
    InvocationStatus.BLOCKED -> "已阻止（未发起调用）"
}

private fun InvocationStatus.uiColor(): Color = when (this) {
    InvocationStatus.SUCCEEDED -> BrandGreen
    InvocationStatus.FAILED -> ErrorRed
    InvocationStatus.CANCELLED, InvocationStatus.BLOCKED -> ActionOrange
}

private fun ProviderId.uiLabel(): String = when (this) {
    ProviderId.MOCK -> "本地 Mock（非真实服务）"
    ProviderId.OPENROUTER -> "OpenRouter（未验证真实连接）"
    ProviderId.QWEN -> "Qwen 官方直连（未验证真实连接）"
    ProviderId.DEEPSEEK -> "DeepSeek 官方直连（未验证真实连接）"
}

private fun InvocationRecord.providerLabel(): String = when (providerId) {
    ProviderId.MOCK -> providerId.uiLabel()
    // A non-BLOCKED OpenRouter record has an actual ProviderAttempt and therefore represents a
    // real service attempt, including safe failure terminal states.
    ProviderId.OPENROUTER -> if (taskRun.attempts.isNotEmpty()) "OpenRouter（真实服务）" else providerId.uiLabel()
    ProviderId.QWEN -> if (taskRun.attempts.isNotEmpty()) "Qwen 官方直连（真实服务）" else providerId.uiLabel()
    ProviderId.DEEPSEEK -> if (taskRun.attempts.isNotEmpty()) "DeepSeek 官方直连（真实服务）" else providerId.uiLabel()
}

private fun InvocationRecord.usageAndCostLabel(): String {
    fun Long?.valueOrUnknown(): String = this?.toString() ?: "未知"
    val fee = cost.totalMicros?.let { micros -> "${cost.currencyCode} ${micros} 微货币" } ?: "未知"
    return "输入 Token：${usage.inputTokens.valueOrUnknown()} · 输出 Token：${usage.outputTokens.valueOrUnknown()} · 费用：$fee"
}

private fun com.nanzhufeng.ai.domain.TaskRun.durationMillis(): Long =
    completedAt.toEpochMilli() - startedAt.toEpochMilli()

private fun java.time.Instant.ledgerTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(this)

private fun AiTaskError.uiLabel(): String = when (this) {
    AiTaskError.ConsentRequired -> "未完成本次外发确认"
    AiTaskError.ProviderTimedOut -> "调用已取消或超时"
    AiTaskError.ProviderAuthenticationFailed -> "鉴权失败"
    AiTaskError.ProviderBalanceInsufficient -> "余额不足"
    AiTaskError.ProviderRateLimited -> "请求受限"
    AiTaskError.ProviderNetworkUnavailable -> "网络不可用"
    AiTaskError.ProviderUnavailable -> "服务暂不可用"
    AiTaskError.ProviderResponseFormatInvalid -> "响应格式无效"
    AiTaskError.ProviderSchemaValidationFailed -> "输出合同校验失败"
    AiTaskError.ProviderContextOverflow -> "上下文超限"
    else -> "${this.javaClass.simpleName}"
}
