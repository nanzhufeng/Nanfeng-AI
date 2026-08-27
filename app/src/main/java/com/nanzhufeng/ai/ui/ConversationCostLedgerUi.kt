package com.nanzhufeng.ai.ui

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
import com.nanzhufeng.ai.domain.AssistantResponseModelAttribution
import com.nanzhufeng.ai.domain.AssistantResponseModelAttributionStore
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.withAvailableLocalCostEstimate
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecord
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecord
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecordStore
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConversationCostLedgerUiState(
    val dialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val records: List<AssistantResponseModelAttribution> = emptyList(),
    val reminderDraftRecords: List<ReminderDraftGenerationRecord> = emptyList(),
    val titleGenerationRecords: List<ConversationTitleGenerationRecord> = emptyList(),
)

/** A message-owned cost list: no request/response body, API Key, attachment or prompt is stored. */
class ConversationCostLedgerViewModel(
    private val attributions: AssistantResponseModelAttributionStore,
    private val reminderDraftRecords: ReminderDraftGenerationRecordStore,
    private val titleGenerationRecords: ConversationTitleGenerationRecordStore,
) : ViewModel() {
    var state by mutableStateOf(ConversationCostLedgerUiState())
        private set

    fun showDialog() {
        state = state.copy(dialogVisible = true, isLoading = true)
        viewModelScope.launch {
            val values = withContext(Dispatchers.IO) {
                Triple(attributions.listCostedNewestFirst().map(AssistantResponseModelAttribution::withAvailableLocalCostEstimate), reminderDraftRecords.listNewestFirst(), titleGenerationRecords.listNewestFirst())
            }
            state = ConversationCostLedgerUiState(dialogVisible = true, records = values.first, reminderDraftRecords = values.second, titleGenerationRecords = values.third)
        }
    }

    fun load() {
        state = state.copy(isLoading = true)
        viewModelScope.launch {
            val values = withContext(Dispatchers.IO) {
                Triple(attributions.listCostedNewestFirst().map(AssistantResponseModelAttribution::withAvailableLocalCostEstimate), reminderDraftRecords.listNewestFirst(), titleGenerationRecords.listNewestFirst())
            }
            state = state.copy(isLoading = false, records = values.first, reminderDraftRecords = values.second, titleGenerationRecords = values.third)
        }
    }

    fun dismissDialog() { state = state.copy(dialogVisible = false) }

    class Factory(
        private val attributions: AssistantResponseModelAttributionStore,
        private val reminderDraftRecords: ReminderDraftGenerationRecordStore,
        private val titleGenerationRecords: ConversationTitleGenerationRecordStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConversationCostLedgerViewModel::class.java))
            return ConversationCostLedgerViewModel(attributions, reminderDraftRecords, titleGenerationRecords) as T
        }
    }
}

@Composable
fun ConversationCostLedgerDialog(state: ConversationCostLedgerUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForegroundSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("费用与用量", fontWeight = FontWeight.SemiBold) },
        text = {
            when {
                state.isLoading -> Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp)); Text("正在读取本机费用记录…", color = SecondaryText)
                }
                state.records.isEmpty() && state.reminderDraftRecords.isEmpty() && state.titleGenerationRecords.isEmpty() -> EmptyConversationCostLedger()
                else -> Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConversationCostSummary(state.records)
                    ConversationTitleCostSummary(state.titleGenerationRecords)
                    ReminderDraftCostSummary(state.reminderDraftRecords)
                    state.records.forEachIndexed { index, record ->
                        if (index == 0) HorizontalDivider(color = NeutralBorder)
                        ConversationCostLedgerRow(record)
                        if (index < state.records.lastIndex) HorizontalDivider(color = NeutralBorder)
                    }
                    state.reminderDraftRecords.forEach { record -> ReminderDraftCostRow(record) }
                    state.titleGenerationRecords.forEach { record -> ConversationTitleCostRow(record) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !state.isLoading) { Text("关闭") } },
    )
}

/** Settings → 模型与联网 → 费用与用量. This page deliberately has no modal owner. */
@Composable
fun ConversationCostLedgerPage(state: ConversationCostLedgerUiState) {
    when {
        state.isLoading -> Column(Modifier.fillMaxWidth().heightIn(min = 160.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentOrange, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp)); Text("正在读取本机费用记录…", color = SecondaryText)
        }
        state.records.isEmpty() && state.reminderDraftRecords.isEmpty() && state.titleGenerationRecords.isEmpty() -> EmptyConversationCostLedger()
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConversationCostSummary(state.records)
            ConversationTitleCostSummary(state.titleGenerationRecords)
            ReminderDraftCostSummary(state.reminderDraftRecords)
            state.records.forEachIndexed { index, record ->
                if (index == 0) HorizontalDivider(color = NeutralBorder)
                ConversationCostLedgerRow(record)
                if (index < state.records.lastIndex) HorizontalDivider(color = NeutralBorder)
            }
            state.reminderDraftRecords.forEach { record -> ReminderDraftCostRow(record) }
            state.titleGenerationRecords.forEach { record -> ConversationTitleCostRow(record) }
        }
    }
}

@Composable private fun EmptyConversationCostLedger() = Column(
    Modifier.fillMaxWidth().padding(vertical = 20.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally,
) {
    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(scaledAppIconSize(36.dp)))
    Text("尚无可用费用记录", fontWeight = FontWeight.Medium)
    Text("已返回输入和输出 Token 的对话会显示服务商金额或本地估算。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ConversationCostSummary(records: List<AssistantResponseModelAttribution>) {
    val exact = records.filter { it.costSource == ConversationCostSource.PROVIDER_RESPONSE }.sumOf { it.cost.totalMicros ?: 0L }
    val estimated = records.filter { it.costSource == ConversationCostSource.LOCAL_ESTIMATE }.sumOf { it.cost.totalMicros ?: 0L }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("本机累计", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (exact > 0L || records.any { it.costSource == ConversationCostSource.PROVIDER_RESPONSE }) {
            ConversationCostSummaryAmount(label = "OpenRouter 实际金额：", amount = "\$${exact.usdText()}")
        }
        if (estimated > 0L || records.any { it.costSource == ConversationCostSource.LOCAL_ESTIMATE }) {
            ConversationCostSummaryAmount(label = "本地估算：", amount = "≈ \$${estimated.usdText()}")
        }
    }
}

@Composable private fun ConversationCostSummaryAmount(label: String, amount: String) = Row {
    Text(label, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text(amount, color = AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
}

@Composable private fun ReminderDraftCostSummary(records: List<ReminderDraftGenerationRecord>) {
    if (records.isEmpty()) return
    val estimated = records.sumOf { it.cost.totalMicros ?: 0L }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = NeutralBorder)
        Text("提醒草案整理", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("千问整理调用：${records.size} 次 · 本地估算：≈ \$${estimated.usdText()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ConversationTitleCostSummary(records: List<ConversationTitleGenerationRecord>) {
    if (records.isEmpty()) return
    val estimated = records.sumOf { it.cost.totalMicros ?: 0L }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = NeutralBorder)
        Text("会话标题整理", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("已配置模型整理：${records.size} 次 · 本地估算：≈ \$${estimated.usdText()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ConversationCostLedgerRow(record: AssistantResponseModelAttribution) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(record.footerLabel(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(record.recordedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    Text(record.footerCostLabel() ?: "Token 已返回，但此模型暂无本机价目表", color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text(
        "输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${when (record.costSource) { ConversationCostSource.PROVIDER_RESPONSE -> "OpenRouter 实际金额"; ConversationCostSource.LOCAL_ESTIMATE -> "本地价目表估算"; null -> "缺少可用估算价目表" }}",
        color = SecondaryText, style = MaterialTheme.typography.bodySmall,
    )
    Text(record.modelDurationLabel(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ReminderDraftCostRow(record: ReminderDraftGenerationRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("提醒草案 · ${record.modelId ?: "千问"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(record.requestedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.cost.totalMicros?.let { "≈ \$${it.usdText()}（估算）" } ?: "未产生可用金额"
    Text(amount, color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ConversationTitleCostRow(record: ConversationTitleGenerationRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("会话标题 · ${record.modelId ?: record.providerId?.titleProviderLabel() ?: "未配置服务"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(record.requestedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.cost.totalMicros?.let { "≈ \$${it.usdText()}（估算）" } ?: "未产生可用金额"
    Text(amount, color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

private fun Long.usdText(): String = BigDecimal.valueOf(this).movePointLeft(6).setScale(6, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString()
private fun java.time.Instant.costTimestamp(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(this)
private fun com.nanzhufeng.ai.domain.ProviderId.titleProviderLabel(): String = when (this) {
    com.nanzhufeng.ai.domain.ProviderId.QWEN -> "千问"
    com.nanzhufeng.ai.domain.ProviderId.OPENROUTER -> "OpenRouter"
    com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK -> "DeepSeek"
    com.nanzhufeng.ai.domain.ProviderId.MOCK -> "本地模拟"
}

private fun AssistantResponseModelAttribution.modelDurationLabel(): String = modelDurationMillis?.let { duration ->
    val seconds = duration / 1_000
    when {
        seconds < 60L -> "模型耗时 ${duration / 100L / 10.0} 秒"
        else -> "模型耗时 ${seconds / 60} 分 ${seconds % 60} 秒"
    }
} ?: "模型耗时未记录"
