package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nanzhufeng.ai.domain.CnyMoneyDisplay
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.ConversationCostEstimator
import com.nanzhufeng.ai.domain.DirectChatCallAuditRecord
import com.nanzhufeng.ai.domain.DirectChatCallAuditStore
import com.nanzhufeng.ai.domain.HISTORY_CURATION_AUDIT_ALIAS
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunRecord
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunStatus
import com.nanzhufeng.ai.domain.HistoryKnowledgeAutoCurationRunStore
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderUsage
import com.nanzhufeng.ai.domain.InvocationRecord
import com.nanzhufeng.ai.domain.InvocationRepository
import com.nanzhufeng.ai.domain.ScheduledMonitorRepository
import com.nanzhufeng.ai.domain.ScheduledMonitorRun
import com.nanzhufeng.ai.domain.withAvailableLocalCostEstimate
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecord
import com.nanzhufeng.ai.domain.ReminderDraftGenerationRecordStore
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecord
import com.nanzhufeng.ai.domain.ConversationTitleGenerationRecordStore
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
    val scheduledMonitorRuns: List<ScheduledMonitorRun> = emptyList(),
    val historyCurationCalls: List<DirectChatCallAuditRecord> = emptyList(),
    val historyCurationRun: HistoryKnowledgeAutoCurationRunRecord? = null,
    val glmOcrCalls: List<InvocationRecord> = emptyList(),
)

private data class CostLedgerRecords(
    val conversations: List<AssistantResponseModelAttribution>,
    val reminders: List<ReminderDraftGenerationRecord>,
    val titles: List<ConversationTitleGenerationRecord>,
    val monitorRuns: List<ScheduledMonitorRun>,
    val historyCurationCalls: List<DirectChatCallAuditRecord>,
    val historyCurationRun: HistoryKnowledgeAutoCurationRunRecord?,
    val glmOcrCalls: List<InvocationRecord>,
)

private enum class ConversationCostLedgerSection(val label: String, val detail: String) {
    CONVERSATION("会话", "查看会话的逐次 Token 与金额"),
    TITLE("会话标题整理", "查看标题整理的逐次 Token 与金额"),
    HISTORY("历史资料整理", "查看历史资料整理的逐次 Token 与金额"),
    OCR("南枫转写", "查看 GLM-OCR 的逐次 Token 与金额"),
}

/** A message-owned cost list: no request/response body, API Key, attachment or prompt is stored. */
class ConversationCostLedgerViewModel(
    private val attributions: AssistantResponseModelAttributionStore,
    private val reminderDraftRecords: ReminderDraftGenerationRecordStore,
    private val titleGenerationRecords: ConversationTitleGenerationRecordStore,
    private val scheduledMonitorRepository: ScheduledMonitorRepository,
    private val directChatCallAudit: DirectChatCallAuditStore,
    private val historyCurationRuns: HistoryKnowledgeAutoCurationRunStore,
    private val invocations: InvocationRepository,
) : ViewModel() {
    var state by mutableStateOf(ConversationCostLedgerUiState())
        private set

    fun showDialog() {
        state = state.copy(dialogVisible = true, isLoading = true)
        viewModelScope.launch {
            state = readLedgerRecords().toState(dialogVisible = true)
        }
    }

    fun load() {
        state = state.copy(isLoading = true)
        viewModelScope.launch {
            state = readLedgerRecords().toState(dialogVisible = state.dialogVisible)
        }
    }

    fun dismissDialog() { state = state.copy(dialogVisible = false) }

    private suspend fun readLedgerRecords(): CostLedgerRecords = withContext(Dispatchers.IO) {
        CostLedgerRecords(
            conversations = attributions.listCostedNewestFirst().map(AssistantResponseModelAttribution::withAvailableLocalCostEstimate),
            reminders = reminderDraftRecords.listNewestFirst(),
            titles = titleGenerationRecords.listNewestFirst(),
            monitorRuns = scheduledMonitorRepository.listCostedRunsNewestFirst(),
            historyCurationCalls = directChatCallAudit.listNewestFirst().filter { it.modelAlias == HISTORY_CURATION_AUDIT_ALIAS },
            historyCurationRun = historyCurationRuns.latest(),
            glmOcrCalls = invocations.listNewestFirst().filter { it.taskId.value.startsWith("glm-ocr:") },
        )
    }

    private fun CostLedgerRecords.toState(dialogVisible: Boolean) = ConversationCostLedgerUiState(
        dialogVisible = dialogVisible,
        records = conversations,
        reminderDraftRecords = reminders,
        titleGenerationRecords = titles,
        scheduledMonitorRuns = monitorRuns,
        historyCurationCalls = historyCurationCalls,
        historyCurationRun = historyCurationRun,
        glmOcrCalls = glmOcrCalls,
    )

    class Factory(
        private val attributions: AssistantResponseModelAttributionStore,
        private val reminderDraftRecords: ReminderDraftGenerationRecordStore,
        private val titleGenerationRecords: ConversationTitleGenerationRecordStore,
        private val scheduledMonitorRepository: ScheduledMonitorRepository,
        private val directChatCallAudit: DirectChatCallAuditStore,
        private val historyCurationRuns: HistoryKnowledgeAutoCurationRunStore,
        private val invocations: InvocationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConversationCostLedgerViewModel::class.java))
            return ConversationCostLedgerViewModel(attributions, reminderDraftRecords, titleGenerationRecords, scheduledMonitorRepository, directChatCallAudit, historyCurationRuns, invocations) as T
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
                state.isEmpty() -> EmptyConversationCostLedger()
                else -> Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                    ConversationCostLedgerContent(state)
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
        state.isEmpty() -> EmptyConversationCostLedger()
        else -> ConversationCostLedgerContent(state)
    }
}

@Composable private fun EmptyConversationCostLedger() = Column(
    Modifier.fillMaxWidth().padding(vertical = 20.dp), Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally,
) {
    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(scaledAppIconSize(36.dp)))
    Text("尚无可用费用记录", fontWeight = FontWeight.Medium)
    Text("已返回输入和输出 Token 的调用会显示费用与用量。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

private fun ConversationCostLedgerUiState.isEmpty() = records.isEmpty() && reminderDraftRecords.isEmpty() && titleGenerationRecords.isEmpty() && scheduledMonitorRuns.isEmpty() && historyCurationCalls.isEmpty() && historyCurationRun == null && glmOcrCalls.isEmpty()

@Composable private fun ConversationCostLedgerContent(state: ConversationCostLedgerUiState) {
    var selectedSection by remember {
        mutableStateOf(
            if (state.records.isEmpty() && state.titleGenerationRecords.isEmpty() && state.historyCurationRun != null) ConversationCostLedgerSection.HISTORY
            else ConversationCostLedgerSection.CONVERSATION,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConversationCostSummary(state)
        ConversationCostCategoryGrid(state)
        ConversationCostLedgerSectionPicker(
            selected = selectedSection,
            onSelected = { selectedSection = it },
        )
        ConversationCostLedgerSectionRows(state, selectedSection)
        ReminderDraftCostSummary(state.reminderDraftRecords)
        ScheduledMonitorCostSummary(state.scheduledMonitorRuns)
        state.reminderDraftRecords.forEach { record -> ReminderDraftCostRow(record) }
        state.scheduledMonitorRuns.forEach { record -> ScheduledMonitorCostRow(record) }
    }
}

@Composable private fun ConversationCostSummary(state: ConversationCostLedgerUiState) {
    val costs = state.records.map { it.cost } +
        state.reminderDraftRecords.map { it.cost } + state.titleGenerationRecords.map { it.cost } +
        state.scheduledMonitorRuns.mapNotNull(ScheduledMonitorRun::estimatedCost) +
        state.historyCurationCalls.mapNotNull(DirectChatCallAuditRecord::estimatedCost) +
        state.glmOcrCalls.map { it.cost }
    ConversationCostSummarySection(title = "本机累计") {
        if (costs.isEmpty()) {
            ConversationCostSummaryMetric(label = "计费调用", value = "暂无")
        }
        if (costs.isNotEmpty()) {
            CnyMoneyDisplay.summaryLabel(costs)?.let { amount ->
                ConversationCostSummaryMetric(label = "费用", value = amount, emphasizeValue = true)
            }
        }
    }
}

@Composable private fun ConversationCostSummarySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = ForegroundSurface,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BodyText)
        content()
    }
}

@Composable private fun ConversationCostSummaryMetric(
    label: String,
    value: String,
    emphasizeValue: Boolean = false,
) = Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, modifier = Modifier.weight(1f), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text(
        value,
        color = if (emphasizeValue) AccentOrange else BodyText,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (emphasizeValue) FontWeight.Bold else FontWeight.Medium,
    )
}

private data class ConversationCostCategoryMetric(
    val label: String,
    val value: String,
    val emphasizeValue: Boolean = false,
)

@Composable
private fun ConversationCostCategoryGrid(state: ConversationCostLedgerUiState) {
    if (state.records.isEmpty() && state.titleGenerationRecords.isEmpty() && state.historyCurationCalls.isEmpty() && state.glmOcrCalls.isEmpty()) return

    val conversationMetrics = buildList {
        add(ConversationCostCategoryMetric("次数", "${state.records.size} 次"))
        add(ConversationCostCategoryMetric("费用", CnyMoneyDisplay.summaryLabel(state.records.map { it.cost }) ?: "金额未知", true))
    }
    val titleMetrics = listOf(
        ConversationCostCategoryMetric("次数", "${state.titleGenerationRecords.size} 次"),
        ConversationCostCategoryMetric(
            "费用",
            CnyMoneyDisplay.summaryLabel(state.titleGenerationRecords.map { it.cost }) ?: "金额未知",
            true,
        ),
    )
    val historyMetrics = listOf(
        ConversationCostCategoryMetric("次数", "${state.historyCurationCalls.size} 次"),
        ConversationCostCategoryMetric(
            "费用",
            CnyMoneyDisplay.summaryLabel(state.historyCurationCalls.mapNotNull(DirectChatCallAuditRecord::estimatedCost)) ?: "金额未知",
            true,
        ),
    )
    val ocrMetrics = listOf(
        ConversationCostCategoryMetric("次数", "${state.glmOcrCalls.size} 次"),
        ConversationCostCategoryMetric(
            "费用",
            CnyMoneyDisplay.summaryLabel(state.glmOcrCalls.map { it.cost }) ?: "金额未知",
            true,
        ),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ForegroundSurface,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ConversationCostCategoryCell(
                    title = "会话",
                    metrics = conversationMetrics,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                VerticalDivider(color = NeutralBorder.copy(alpha = 0.66f))
                ConversationCostCategoryCell(
                    title = "会话标题整理",
                    metrics = titleMetrics,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            HorizontalDivider(color = NeutralBorder.copy(alpha = 0.66f))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ConversationCostCategoryCell(
                    title = "历史资料整理",
                    metrics = historyMetrics,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                VerticalDivider(color = NeutralBorder.copy(alpha = 0.66f))
                ConversationCostCategoryCell(
                    title = "南枫转写",
                    metrics = ocrMetrics,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ConversationCostCategoryCell(
    title: String,
    metrics: List<ConversationCostCategoryMetric>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BodyText,
        )
        metrics.forEach { metric ->
            ConversationCostSummaryMetric(
                label = metric.label,
                value = metric.value,
                emphasizeValue = metric.emphasizeValue,
            )
        }
    }
}

@Composable private fun ReminderDraftCostSummary(records: List<ReminderDraftGenerationRecord>) {
    if (records.isEmpty()) return
    val estimated = CnyMoneyDisplay.totalLabel(records.map { it.cost }, estimated = true) ?: "金额未知"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = NeutralBorder)
        Text("提醒草案整理", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(modelNameAnnotatedText(modelName = "千问", suffix = "整理调用：${records.size} 次 · 费用：$estimated"), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ScheduledMonitorCostSummary(records: List<ScheduledMonitorRun>) {
    if (records.isEmpty()) return
    val estimated = CnyMoneyDisplay.totalLabel(records.mapNotNull(ScheduledMonitorRun::estimatedCost), estimated = true) ?: "金额未知"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HorizontalDivider(color = NeutralBorder)
        Text("定时监控", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("已返回 Token 的执行：${records.size} 次 · 费用：$estimated", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}

/** Four fixed ledger sections are a direct segmented switch, not a dropdown input. */
@Composable private fun ConversationCostLedgerSectionPicker(
    selected: ConversationCostLedgerSection,
    onSelected: (ConversationCostLedgerSection) -> Unit,
) {
    val containerShape = RoundedCornerShape(28.dp)
    Surface(
        color = ForegroundSurface,
        contentColor = BodyText,
        shape = containerShape,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ConversationCostLedgerSection.entries.forEach { section ->
                val active = section == selected
                Surface(
                    onClick = { onSelected(section) },
                    color = if (active) AccentOrange else Color.Transparent,
                    contentColor = if (active) Color.White else BodyText,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            section.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun ConversationCostLedgerSectionRows(
    state: ConversationCostLedgerUiState,
    section: ConversationCostLedgerSection,
) = when (section) {
    ConversationCostLedgerSection.CONVERSATION -> {
        if (state.records.isEmpty()) ConversationCostLedgerSectionEmpty(section)
        else state.records.forEachIndexed { index, record ->
            if (index == 0) HorizontalDivider(color = NeutralBorder)
            ConversationCostLedgerRow(record)
            if (index < state.records.lastIndex) HorizontalDivider(color = NeutralBorder)
        }
    }
    ConversationCostLedgerSection.TITLE -> {
        if (state.titleGenerationRecords.isEmpty()) ConversationCostLedgerSectionEmpty(section)
        else state.titleGenerationRecords.forEach { record -> ConversationTitleCostRow(record) }
    }
    ConversationCostLedgerSection.HISTORY -> {
        state.historyCurationRun?.let { HistoryCurationRunStatusRow(it) }
        if (state.historyCurationCalls.isEmpty()) ConversationCostLedgerSectionEmpty(section)
        else state.historyCurationCalls.forEach { record -> HistoryCurationCostRow(record) }
    }
    ConversationCostLedgerSection.OCR -> {
        if (state.glmOcrCalls.isEmpty()) ConversationCostLedgerSectionEmpty(section)
        else state.glmOcrCalls.forEach { record -> GlmOcrCostRow(record) }
    }
}

@Composable private fun ConversationCostLedgerSectionEmpty(section: ConversationCostLedgerSection) = Text(
    "暂无${section.label}费用记录",
    color = SecondaryText,
    style = MaterialTheme.typography.bodySmall,
)

@Composable private fun ConversationCostLedgerRow(record: AssistantResponseModelAttribution) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(record.footerLabel(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(record.recordedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    Text(record.footerCostLabel() ?: "Token 已返回，但此模型暂无本机价目表", color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · ${record.usage.outputBreakdownLabel()}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text(record.modelDurationLabel(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ReminderDraftCostRow(record: ReminderDraftGenerationRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelNameAnnotatedText(prefix = "提醒草案 · ", modelName = record.modelId ?: "千问"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(record.requestedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.cost.totalMicros?.let {
        CnyMoneyDisplay.label(it, record.cost.currencyCode, estimated = true)
    } ?: "未产生可用金额"
    Text(amount, color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ConversationTitleCostRow(record: ConversationTitleGenerationRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelNameAnnotatedText(prefix = "会话标题 · ", modelName = record.modelId ?: record.providerId?.titleProviderLabel() ?: "未配置服务"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(record.requestedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.cost.totalMicros?.let {
        CnyMoneyDisplay.label(it, record.cost.currencyCode, estimated = true)
    } ?: "未产生可用金额"
    Text(amount, color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ScheduledMonitorCostRow(record: ScheduledMonitorRun) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelNameAnnotatedText(prefix = "定时监控 · ", modelName = record.modelId ?: "未记录模型"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(record.startedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.estimatedCost()?.let { CnyMoneyDisplay.label(it.totalMicros ?: 0L, it.currencyCode, estimated = true) } ?: "未产生可用金额"
    Text(amount, color = if (record.estimatedCost() == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.inputTokens ?: "未知"} · 输出 ${record.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun HistoryCurationCostRow(record: DirectChatCallAuditRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelNameAnnotatedText(prefix = "历史资料整理 · ", modelName = record.modelId), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(record.requestedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.estimatedCost()?.let { CnyMoneyDisplay.label(it.totalMicros ?: 0L, it.currencyCode, estimated = true) } ?: "未产生可用金额"
    Text(amount, color = if (record.estimatedCost() == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.inputTokens ?: "未知"} · 输出 ${record.outputTokens ?: "未知"} · ${record.status}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun HistoryCurationRunStatusRow(record: HistoryKnowledgeAutoCurationRunRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("最近自动检查", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text((record.completedAt ?: record.startedAt).costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    Text(
        record.historyCurationRunLabel(),
        color = if (record.status == HistoryKnowledgeAutoCurationRunStatus.FAILED || record.status == HistoryKnowledgeAutoCurationRunStatus.UNKNOWN) AccentOrange else SecondaryText,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun HistoryKnowledgeAutoCurationRunRecord.historyCurationRunLabel(): String = when (status) {
    HistoryKnowledgeAutoCurationRunStatus.RUNNING -> "正在检查候选对话"
    HistoryKnowledgeAutoCurationRunStatus.DISABLED -> "历史资料库已关闭，本轮未调用模型"
    HistoryKnowledgeAutoCurationRunStatus.SAVED -> "已从本轮对话整理 1 条资料"
    HistoryKnowledgeAutoCurationRunStatus.UNKNOWN -> "上次运行被中断；为避免重复发送，未自动重发"
    HistoryKnowledgeAutoCurationRunStatus.FAILED -> when {
        retryable -> "检查失败（${reasonCode ?: "网络错误"}），网络恢复后可重试"
        reasonCode == "CREDENTIAL_MISSING" || reasonCode == "SERVICE_DISABLED" || reasonCode == "MODEL_UNAVAILABLE" -> "模型服务未配置完整，本轮未产生费用"
        else -> "检查失败（${reasonCode ?: "未知错误"}），未自动重发"
    }
    HistoryKnowledgeAutoCurationRunStatus.NO_CANDIDATE -> when (reasonCode) {
        "MODEL_NOT_ELIGIBLE" -> "模型判断本轮对话没有可沉淀资料"
        "LOW_CONFIDENCE" -> "模型信心不足，本轮未写入资料库"
        "DUPLICATE_CONTENT" -> "模型结果与已有资料重复，本轮未写入"
        "ALREADY_PROCESSED", "EXISTING_SOURCE" -> "最近对话已检查过，本轮未调用模型"
        "TOO_FEW_MESSAGES", "SOURCE_TOO_SHORT", "SOURCE_TOO_LONG", "NO_TEXT", "MISSING_CONVERSATION" -> "候选对话不符合整理条件，本轮未调用模型"
        else -> "暂无符合条件的对话，本轮未调用模型"
    }
}

@Composable private fun GlmOcrCostRow(record: InvocationRecord) = Column(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(modelNameAnnotatedText(prefix = "南枫转写 · ", modelName = "GLM-OCR"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(record.completedAt.costTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
    val amount = record.cost.totalMicros?.let { CnyMoneyDisplay.label(it, record.cost.currencyCode, estimated = true) } ?: "未产生可用金额"
    Text(amount, color = if (record.cost.totalMicros == null) SecondaryText else AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    Text("输入 ${record.usage.inputTokens ?: "未知"} · 输出 ${record.usage.outputTokens ?: "未知"} · ${record.status.name}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Text(record.taskRun.durationMillis().costDurationLabel(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
}

private fun ScheduledMonitorRun.estimatedCost(): ProviderCost? = modelId?.let { model ->
    ConversationCostEstimator.estimate(model, ProviderUsage(inputTokens, outputTokens), startedAt)
}

private fun DirectChatCallAuditRecord.estimatedCost(): ProviderCost? =
    ConversationCostEstimator.estimate(modelId, ProviderUsage(inputTokens, outputTokens), requestedAt)

private fun java.time.Instant.costTimestamp(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(this)
private fun com.nanzhufeng.ai.domain.ProviderId.titleProviderLabel(): String = when (this) {
    com.nanzhufeng.ai.domain.ProviderId.QWEN -> "千问"
    com.nanzhufeng.ai.domain.ProviderId.OPENROUTER -> "OpenRouter"
    com.nanzhufeng.ai.domain.ProviderId.DEEPSEEK -> "DeepSeek"
    com.nanzhufeng.ai.domain.ProviderId.ZHIPU -> "智谱"
    com.nanzhufeng.ai.domain.ProviderId.MOCK -> "本地模拟"
}

private fun AssistantResponseModelAttribution.modelDurationLabel(): String = modelDurationMillis?.let { duration ->
    val seconds = duration / 1_000
    when {
        seconds < 60L -> "模型耗时 ${duration / 100L / 10.0} 秒"
        else -> "模型耗时 ${seconds / 60} 分 ${seconds % 60} 秒"
    }
} ?: "模型耗时未记录"

private fun ProviderUsage.outputBreakdownLabel(): String {
    val output = outputTokens ?: return "输出 未知"
    val reasoning = reasoningTokens ?: return "输出 $output · 推理明细未返回"
    val answer = (output - reasoning).coerceAtLeast(0L)
    return "输出 $output（推理 $reasoning · 正文 $answer）"
}

private fun com.nanzhufeng.ai.domain.TaskRun.durationMillis(): Long =
    java.time.Duration.between(startedAt, completedAt).toMillis().coerceAtLeast(0L)

private fun Long.costDurationLabel(): String {
    val seconds = this / 1_000
    return if (seconds < 60L) "模型耗时 ${this / 100L / 10.0} 秒" else "模型耗时 ${seconds / 60} 分 ${seconds % 60} 秒"
}
