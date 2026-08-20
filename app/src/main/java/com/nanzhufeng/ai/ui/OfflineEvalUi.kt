package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nanzhufeng.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OfflineEvalUiState(val visible: Boolean = false, val working: Boolean = false, val runs: List<EvalRun> = emptyList(), val selected: EvalRun? = null, val report: EvalReportResult? = null, val message: String? = null, val reviewerAlias: String = "本地审阅", val selectedCase: EvalCaseId? = null, val scoreValue: String = "", val scoreDimension: HumanScoreDimension = HumanScoreDimension.RELEVANCE)

class OfflineEvalViewModel(private val eval: RunOfflineEvalUseCase, private val repository: EvalRunRepository, private val exporter: ExportOfflineEvalReportUseCase) : ViewModel() {
    var state by mutableStateOf(OfflineEvalUiState()); private set
    fun show() = reload(open = true)
    fun dismiss() { if (!state.working) state = state.copy(visible = false) }
    fun select(run: EvalRun) { state = state.copy(selected = run, selectedCase = run.results.firstOrNull()?.caseId, report = null, message = null) }
    fun start() { state = state.copy(working = true, message = null); viewModelScope.launch { val run = withContext(Dispatchers.IO) { eval.run() }; reload(selected = run.id) } }
    fun export() { val id = state.selected?.id ?: return; state = state.copy(working = true); viewModelScope.launch { val report = withContext(Dispatchers.IO) { exporter.execute(id) }; state = state.copy(working = false, report = report, message = if (report == null) "报告写入、回读或校验失败。" else null) } }
    fun alias(value: String) { state = state.copy(reviewerAlias = value.take(80)) }
    fun selectCase(value: EvalCaseId) { state = state.copy(selectedCase = value) }
    fun score(value: String) { state = state.copy(scoreValue = value.take(1)) }
    fun rotateDimension() { state = state.copy(scoreDimension = HumanScoreDimension.entries[(state.scoreDimension.ordinal + 1) % HumanScoreDimension.entries.size]) }
    fun saveScore() { val run = state.selected ?: return; val case = state.selectedCase ?: return; val number = state.scoreValue.toIntOrNull(); state = state.copy(working = true); viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { eval.score(run.id, case, state.scoreDimension, number, state.reviewerAlias, null) } }.onFailure { state = state.copy(message = "人工评分未保存：${it.message}") }; state = state.copy(working = false) } }
    fun comparison(): EvalComparison? { val selected = state.selected ?: return null; val other = state.runs.firstOrNull { it.id != selected.id } ?: return null; return runCatching { eval.compare(other, selected) }.getOrNull() }
    private fun reload(selected: EvalRunId? = state.selected?.id, open: Boolean = false) { viewModelScope.launch { val runs = withContext(Dispatchers.IO) { repository.list() }; val chosen = selected?.let { id -> runs.firstOrNull { it.id == id } } ?: state.selected; state = state.copy(visible = state.visible || open, working = false, runs = runs, selected = chosen, selectedCase = chosen?.results?.firstOrNull()?.caseId ?: state.selectedCase) } }
    class Factory(private val eval: RunOfflineEvalUseCase, private val repository: EvalRunRepository, private val exporter: ExportOfflineEvalReportUseCase) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = OfflineEvalViewModel(eval, repository, exporter) as T }
}

@Composable fun OfflineEvalCard(onOpen: () -> Unit) = WhiteCard {
    Text("离线 Eval 基线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp)); Text("版本化本地回归、红队和人工审阅。仅前台运行；中断不会伪造继续，需手动重新运行。不会调用模型、发送内容或报告 Token、TTFT、费用、缓存收益或模型质量。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp)); Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)) { Text("打开本地 Eval") }
}

@Composable fun OfflineEvalDialog(state: OfflineEvalUiState, onDismiss: () -> Unit, onStart: () -> Unit, onSelect: (EvalRun) -> Unit, onExport: () -> Unit, onAlias: (String) -> Unit, onCase: (EvalCaseId) -> Unit, onScore: (String) -> Unit, onDimension: () -> Unit, onSaveScore: () -> Unit, comparison: EvalComparison?) = AlertDialog(
    onDismissRequest = { if (!state.working) onDismiss() }, containerColor = Color.White, shape = RoundedCornerShape(24.dp),
    title = { Text("本地离线 Eval", fontWeight = FontWeight.SemiBold) },
    text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("证据等级：OFFLINE_LOCAL。夹具是打包只读版本；运行结果不含生产正文、Prompt、Provider payload、URI/路径或附件字节。真实服务与成本尚未验证。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onStart, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text(if (state.working) "正在运行本地回归…" else "开始本地回归") }
        state.runs.forEach { run -> TextButton(onClick = { onSelect(run) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text("${run.datasetVersion} · ${run.results.count { it.verdict == EvalVerdict.PASS }}/${run.results.size} 通过", fontWeight = FontWeight.Medium); Text("${run.completedAt} · ${run.id.value.take(8)}", color = SecondaryText, style = MaterialTheme.typography.bodySmall) } } }
        state.selected?.let { run ->
            HorizontalDivider(color = NeutralBorder); Text("运行详情：${run.id.value.take(8)}", fontWeight = FontWeight.SemiBold)
            run.results.forEach { result -> TextButton(onClick = { onCase(result.caseId) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text("${result.caseId.value} · ${result.verdict.name}"); Text(result.assertions.joinToString(" · ") { it.fact.name }, color = SecondaryText, style = MaterialTheme.typography.bodySmall) } } }
            Text("人工评分：允许留空（未评分）或 1–5；不改变自动断言。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.reviewerAlias, onAlias, label = { Text("本地审阅别名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.scoreValue, onScore, label = { Text("${state.scoreDimension.name} 分数（留空=未评分）") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onDimension) { Text("切换维度（相关性/事实性/完整性/安全性/可追溯性）") }
            TextButton(onClick = onSaveScore, enabled = state.selectedCase != null && state.reviewerAlias.isNotBlank()) { Text("为 ${state.selectedCase?.value ?: "用例"} 追加人工评分") }
            TextButton(onClick = onExport) { Text("导出此运行的 JSON + Manifest + SHA-256") }
            comparison?.let { Text("与另一运行比较：${if (it.changed.isEmpty()) "无确定性差异" else "变化 ${it.changed.joinToString { c -> c.value }}"}", color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
        }
        state.report?.let { Text("已原子写入、回读并校验：${it.fileName} · ${it.sha256}", color = BrandGreen, style = MaterialTheme.typography.bodySmall) }
        state.message?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
    } },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !state.working) { Text("关闭") } }, confirmButton = {},
)
