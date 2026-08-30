package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.AgentRunSnapshot
import com.nanzhufeng.ai.domain.AgentRunStatus
import com.nanzhufeng.ai.domain.AgentLedgerStatus
import com.nanzhufeng.ai.domain.AgentExecutionResult
import com.nanzhufeng.ai.domain.P8CProductionLocalAgentController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class P8ControlledAgentUiState(
    val visible: Boolean = false,
    val working: Boolean = false,
    val runs: List<AgentRunSnapshot> = emptyList(),
    val ledger: AgentLedgerStatus? = null,
    val awaitingApproval: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

/** UI holds only visibility and a short-lived confirmation; durable Run truth comes from AgentLedger. */
class P8ControlledAgentViewModel(private val controller: P8CProductionLocalAgentController) : ViewModel() {
    var state by mutableStateOf(P8ControlledAgentUiState())
        private set

    fun show() { state = state.copy(visible = true, error = null, notice = null); refresh() }
    fun dismiss() { if (!state.working) state = state.copy(visible = false, awaitingApproval = false, error = null) }
    fun refresh() = run { viewModelScope.launch { load() } }
    fun begin() = runAction(action = { controller.begin() }, onSuccess = { result ->
        state = state.copy(awaitingApproval = result is AgentExecutionResult.Accepted, notice = if (result is AgentExecutionResult.Accepted) "计划已持久化。请在 90 秒内明确确认；重启不会自动执行。" else state.notice)
    })
    fun confirm() = runAction(action = { controller.confirm() }, onSuccess = { result ->
        state = state.copy(awaitingApproval = false, notice = if (result is AgentExecutionResult.Accepted) result.summary else state.notice)
    })
    fun pause(runId: String) = runAction(action = { controller.pause(runId) }, onSuccess = { state = state.copy(notice = "已写入暂停 checkpoint；重启后只回读，不会自动恢复。") })
    fun resume(runId: String) = runAction(action = { controller.resume(runId) }, onSuccess = { state = state.copy(notice = "已按当前 durable Run 显式恢复。") })
    fun cancel(runId: String) = runAction(action = { controller.cancel(runId) }, onSuccess = { state = state.copy(notice = "已取消并写入 durable checkpoint。") })

    private fun runAction(action: () -> AgentExecutionResult, onSuccess: (AgentExecutionResult) -> Unit) {
        state = state.copy(working = true, error = null, notice = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            if (result is AgentExecutionResult.Rejected) state = state.copy(working = false, error = safeError(result.code))
            else { state = state.copy(working = false); onSuccess(result) }
            load()
        }
    }
    private suspend fun load() {
        val read = withContext(Dispatchers.IO) { controller.inspect() }
        state = state.copy(runs = read.runs, ledger = read.ledgerStatus, awaitingApproval = read.pending != null)
    }
    private fun safeError(code: String) = when (code) {
        "APPROVAL_EXPIRED" -> "确认已过期；未执行任何动作，请重新创建计划。"
        "APPROVAL_REQUIRED" -> "需要先创建并明确确认本地只读计划。"
        else -> "本地受控运行被安全拒绝：$code。未连接模型与外部工具。"
    }
    class Factory(private val controller: P8CProductionLocalAgentController) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(P8ControlledAgentViewModel::class.java))
            return P8ControlledAgentViewModel(controller) as T
        }
    }
}

@androidx.compose.runtime.Composable
internal fun P8ControlledAgentCard(onOpen: () -> Unit) = WhiteCard {
    Text("受控本地运行", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text("仅可显式运行内建账本自检：只读、无输入、无模型、无外部工具。预算未知与 0、暂停/恢复/取消和 receipt 都如实显示。", color = SecondaryText)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = BrandGreen), modifier = Modifier.fillMaxWidth()) { Text("查看本地 Run 审计") }
}

@androidx.compose.runtime.Composable
internal fun P8ControlledAgentDialog(state: P8ControlledAgentUiState, dismiss: () -> Unit, begin: () -> Unit, confirm: () -> Unit, pause: (String) -> Unit, resume: (String) -> Unit, cancel: (String) -> Unit) = AlertDialog(
    onDismissRequest = dismiss, containerColor = ForegroundSurface,
    title = { Text("本地受控运行", fontWeight = FontWeight.SemiBold) },
    text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("只读检查本机账本，不联网。", color = SecondaryText)
        val ledger = state.ledger
        Text("账本：Run ${ledger?.runCount ?: "未知"} · Step ${ledger?.stepCount ?: "未知"} · Event ${ledger?.eventCount ?: "未知"} · Receipt ${ledger?.receiptCount ?: "未知"}", color = SecondaryText)
        Text("内建动作：p8c_local_ledger_inspect · READ_ONLY / LOCAL_READ · side effect 0 · 预算 1/1/0。", color = SecondaryText)
        state.runs.forEach { snapshot ->
            androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${snapshot.run.status} · ${snapshot.run.id.takeLast(12)}", fontWeight = FontWeight.SemiBold)
                Text("风险 ${snapshot.run.riskCeiling} · 权限 ${snapshot.run.permissionGrant} · 预算 ${snapshot.run.usedSteps}/${snapshot.run.budget.maxSteps ?: "未知"}、${snapshot.run.usedToolCalls}/${snapshot.run.budget.maxToolCalls ?: "未知"}、${snapshot.run.usedSideEffects}/${snapshot.run.budget.maxSideEffects ?: "未知"}", color = SecondaryText)
                Text("Step ${snapshot.steps.size} · Event ${snapshot.events.sortedBy { it.sequence }.joinToString { "#${it.sequence}:${it.kind}" }} · checkpoint ${snapshot.checkpoints.size} · ${snapshot.run.safeErrorCode ?: "无错误"}", color = SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (snapshot.run.status in setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING)) OutlinedButton(onClick = { pause(snapshot.run.id) }, enabled = !state.working) { Text("暂停") }
                    if (snapshot.run.status == AgentRunStatus.PAUSED) OutlinedButton(onClick = { resume(snapshot.run.id) }, enabled = !state.working) { Text("恢复") }
                    if (snapshot.run.status in setOf(AgentRunStatus.PENDING, AgentRunStatus.RUNNING, AgentRunStatus.PAUSED)) OutlinedButton(onClick = { cancel(snapshot.run.id) }, enabled = !state.working) { Text("取消") }
                }
            } }
        }
        if (state.runs.isEmpty()) Text("暂无运行记录。", color = SecondaryText)
        state.notice?.let { Text(it, color = BrandGreen) }; state.error?.let { Text(it, color = ErrorRed) }
    } },
    dismissButton = { TextButton(onClick = dismiss, enabled = !state.working) { Text("关闭") } },
    confirmButton = { if (state.awaitingApproval) Button(onClick = confirm, enabled = !state.working) { Text("确认并运行") } else Button(onClick = begin, enabled = !state.working) { Text("创建检查计划") } },
)
