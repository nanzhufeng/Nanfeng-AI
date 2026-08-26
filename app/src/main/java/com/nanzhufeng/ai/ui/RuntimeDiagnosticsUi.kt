package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("独立任务调用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (invocationState.records.isEmpty() && !invocationState.isLoading) {
            Text("暂无独立任务调用记录。对话中的失败会显示在下方。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        } else InvocationLedgerPage(invocationState)
        Spacer(Modifier.height(8.dp))
        Text("连接问题", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ModelSettingsDiagnosticsPage(modelState, conversations)
    }
}
