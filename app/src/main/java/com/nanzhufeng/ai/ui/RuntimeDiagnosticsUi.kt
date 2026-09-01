package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.Conversation

/** One technical page: execution history explains call outcomes; diagnostics explains connection failures. */
@Composable
internal fun RuntimeDiagnosticsPage(
    modelState: ModelSettingsUiState,
    invocationState: InvocationLedgerUiState,
    conversations: List<Conversation>,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RuntimeDiagnosticsSectionHeader(
            title = "连接失败",
            detail = "对话与连接测试中近 7 天的失败记录",
            count = modelState.recentDiagnostics.size,
        )
        ModelSettingsDiagnosticsPage(modelState, conversations)
        Spacer(Modifier.height(6.dp))
        RuntimeDiagnosticsSectionHeader(
            title = "自动与工具任务",
            detail = "标题整理、历史整理和南枫转写等后台调用",
            count = invocationState.records.size,
        )
        if (invocationState.records.isEmpty() && !invocationState.isLoading) {
            Text("暂无自动或工具任务调用记录。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        } else InvocationLedgerPage(invocationState)
    }
}

@Composable
private fun RuntimeDiagnosticsSectionHeader(title: String, detail: String, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("$count 条", color = SecondaryText, style = MaterialTheme.typography.labelLarge)
        }
        Text(detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    }
}
