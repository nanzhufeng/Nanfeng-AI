package com.nanzhufeng.ai.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.ModelPresetDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelPresetUsage
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ChatRoutingPolicy
import com.nanzhufeng.ai.domain.ContextSelectionAuditRecord
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ProviderDiagnosticErrorClass
import com.nanzhufeng.ai.domain.ProviderDiagnosticRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun ProviderReadinessLine(label: String, configuration: ModelServiceConfiguration?) {
    val ready = configuration?.settings?.enabled == true && configuration.credentialState == CredentialState.STORED
    Text(
        text = "${if (ready) "●" else "○"} $label · ${if (ready) "已连接" else "未启用"}",
        color = if (ready) AccentOrange else SecondaryText,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun ModelServiceStatusCard(
    state: ModelSettingsUiState,
    webSearchEnabled: Boolean,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onOpenConversationCostLedger: () -> Unit,
    onOpenContextSelections: () -> Unit,
    onOpenRuntimeDiagnostics: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val openRouter = state.providerConfigurations[ProviderId.OPENROUTER] ?: state.configuration
        val qwen = state.providerConfigurations[ProviderId.QWEN]
        val deepSeek = state.providerConfigurations[ProviderId.DEEPSEEK]
        val zhipu = state.providerConfigurations[ProviderId.ZHIPU]
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ProviderReadinessLine("OpenAI / Claude / Gemini", openRouter)
            ProviderReadinessLine("Qwen", qwen)
            ProviderReadinessLine("DeepSeek", deepSeek)
            ProviderReadinessLine("智谱 GLM", zhipu)
            com.nanzhufeng.ai.domain.RelayCoreModels.providers.forEach { id -> ProviderReadinessLine(id.userLabel(), state.providerConfigurations[id]) }
        }
        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error.title, color = ErrorRed, fontWeight = FontWeight.Medium)
        }
        WebSearchSettingsRow(
            enabled = webSearchEnabled,
            onEnabledChange = onWebSearchEnabledChange,
        )
        ModelSettingsPrimaryEntry(onOpen)
        ModelSettingsRecordsCard(
            onOpenConversationCostLedger = onOpenConversationCostLedger,
            onOpenContextSelections = onOpenContextSelections,
            onOpenRuntimeDiagnostics = onOpenRuntimeDiagnostics,
        )
    }
}

@Composable
private fun WebSearchSettingsRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val supportingText = if (enabled) {
        "开启后每次普通对话均检索公开网页并标注来源；可能产生服务费用"
    } else {
        "已关闭；普通对话不会使用网页检索"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = P5AInteractiveShape,
            color = ForegroundSurface,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    modifier = Modifier.size(scaledAppIconSize(22.dp)),
                    tint = AccentOrange,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "实时网页搜索",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(12.dp))
                SettingsSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            supportingText,
            modifier = Modifier.padding(horizontal = 18.dp),
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ModelSettingsConfigurationPage(
    state: ModelSettingsUiState,
    onSave: (ProviderId, String?) -> Unit,
    onSetProviderEnabled: (ProviderId, Boolean) -> Unit,
    onSetProviderPreset: (ProviderId, ModelPresetId) -> Unit,
    onRevealStoredCredential: (ProviderId) -> Unit,
    onTestConnection: (ProviderId, ModelPresetId?) -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(ProviderId.OPENROUTER) }
    var lastRelayProvider by remember { mutableStateOf(ProviderId.RELAYCORE_CLAUDE) }
    val configuration = state.providerConfigurations[selectedProvider] ?: return
    var probePreset by remember(selectedProvider) { mutableStateOf<ModelPresetId?>(null) }
    val enabled = state.pendingProviderEnabled[selectedProvider] ?: configuration.settings.enabled
    val presetId = state.pendingProviderPresets[selectedProvider] ?: configuration.settings.presetId
    // The stored-key state is deliberately a stable, full-length visual mask.  It never reflects
    // the actual key length and must look identical before and after the visibility control is used.
    val savedKeyMask = "••••••••••••••••••••••••••••••••"
    var keyInput by remember(selectedProvider, configuration.credentialState) {
        mutableStateOf(if (configuration.credentialState == CredentialState.STORED) savedKeyMask else "")
    }
    var keyEdited by remember(selectedProvider, configuration.credentialState) { mutableStateOf(false) }
    var keyVisible by remember(selectedProvider) { mutableStateOf(false) }
    var revealRequested by remember(selectedProvider) { mutableStateOf(false) }
    LaunchedEffect(revealRequested, state.revealedCredential, state.revealedCredentialProviderId) {
        if (revealRequested && !keyEdited && state.revealedCredential != null && state.revealedCredentialProviderId == selectedProvider) {
            keyInput = state.revealedCredential
            keyVisible = true
        }
    }

    LaunchedEffect(state.keySaveRevision) {
        if (state.keySaveRevision > 0 && state.keySavedProvider == selectedProvider) {
            keyInput = savedKeyMask
            keyEdited = false
            keyVisible = false
            revealRequested = false
        }
    }
    val unsavedChanges = keyEdited
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("服务商与模型", color = BodyText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ModelSettingsProviderBrowser(
            selectedProvider = selectedProvider,
            selectedPreset = probePreset ?: presetId,
            enabled = !state.probingConnection,
            onProviderSelected = { provider, platform ->
                val target = if (platform && provider == ProviderId.RELAYCORE_CLAUDE) lastRelayProvider else provider
                selectedProvider = target
                if (target in com.nanzhufeng.ai.domain.RelayCoreModels.providers) lastRelayProvider = target
            },
            onPresetSelected = {
                probePreset = it.takeIf { id -> id == ModelPresetId.GLM_OCR }
                if (probePreset == null) onSetProviderPreset(selectedProvider, it)
            },
        )
        Text("线路配置", color = BodyText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Surface(modifier = Modifier.fillMaxWidth(), shape = P5AInteractiveShape, color = ForegroundSurface) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelProviderBrand(selectedProvider, platform = selectedProvider !in com.nanzhufeng.ai.domain.RelayCoreModels.providers)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val group = when (selectedProvider) {
                        ProviderId.RELAYCORE_CLAUDE -> "Claude 分组"
                        ProviderId.RELAYCORE_GPT -> "GPT 分组"
                        ProviderId.RELAYCORE_GEMINI -> "Gemini 分组"
                        else -> configuration.provider.displayName
                    }
                    Text(group, fontWeight = FontWeight.SemiBold, color = BodyText, fontSize = 14.sp)
                }
                SettingsSwitch(checked = enabled, onCheckedChange = { onSetProviderEnabled(selectedProvider, it) }, enabled = true)
            }
        }
        Text("API Key", color = BodyText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        OutlinedTextField(
                    value = keyInput,
                    onValueChange = { input ->
                        keyInput = if (!keyEdited && input.startsWith(savedKeyMask)) input.removePrefix(savedKeyMask) else input
                        keyEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    singleLine = true,
                    shape = P5AInteractiveShape,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ForegroundSurface,
                        unfocusedContainerColor = ForegroundSurface,
                        disabledContainerColor = ForegroundSurface,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedLabelColor = BodyText,
                        unfocusedLabelColor = BodyText,
                        disabledLabelColor = SecondaryText,
                        focusedPlaceholderColor = InputPlaceholderText,
                        unfocusedPlaceholderColor = InputPlaceholderText,
                        disabledPlaceholderColor = InputPlaceholderText,
                    ),
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (keyVisible) { keyVisible = false; revealRequested = false }
                            else if (!keyEdited && configuration.credentialState == CredentialState.STORED) { revealRequested = true; onRevealStoredCredential(selectedProvider) }
                            else keyVisible = true
                        }) {
                            Icon(
                                if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                            )
                        }
                    },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onTestConnection(selectedProvider, probePreset) },
                enabled = !state.saving && !state.probingConnection && !unsavedChanges,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = P5AInteractiveShape, border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText),
            ) {
                if (state.probingConnection && state.probingProviderId == selectedProvider) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在测试…")
                } else Text("测试连接")
            }
            Button(
                        onClick = { onSave(selectedProvider, keyInput.takeIf { keyEdited && it.isNotBlank() }) },
                        enabled = keyEdited && keyInput.isNotBlank() && !state.saving && !state.probingConnection,
                        modifier = Modifier.widthIn(min = 92.dp).heightIn(min = 48.dp),
                        shape = P5AInteractiveShape,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) {
                if (state.saving && state.savingProviderId == selectedProvider) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.saving && state.savingProviderId == selectedProvider) "正在保存…" else "保存")
            }
        }
        if (unsavedChanges) Text("API Key 尚未保存，请先保存再测试连接。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        val feedback = if (unsavedChanges) state.feedbackFor(selectedProvider).copy(notice = null, error = null) else state.feedbackFor(selectedProvider)
        ConnectionTestFeedback(feedback)
        ModelSettingsSaveFeedback(feedback)
    }
}

@Composable
private fun ConnectionTestFeedback(state: ModelSettingsUiState) {
    when {
        state.error?.title?.startsWith("连接测试") == true -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SemanticErrorSurface,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.error.title, color = ErrorRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(state.error.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ModelSettingsSaveFeedback(state: ModelSettingsUiState) {
    val saveError = state.error?.takeUnless { it.title.startsWith("连接测试") }
    when {
        saveError != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SemanticErrorSurface,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(saveError.title, color = ErrorRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(saveError.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.error == null && state.notice != null -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(state.notice, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            if (state.notice == "API Key 已安全保存在本机。") Text(
                "尚未测试连接；请点击“测试连接”确认 API Key 是否可用。",
                color = SecondaryText, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModelSettingsPrimaryEntry(onClick: () -> Unit) {
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        color = ForegroundSurface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = AccentOrange, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = Color.White, modifier = Modifier.size(scaledAppIconSize(22.dp)))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("模型设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("OpenRouter、Qwen、DeepSeek、智谱的 API Key 与模型", color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "进入模型设置", tint = AccentOrange, modifier = Modifier.size(scaledAppIconSize(22.dp)))
        }
    }
}

@Composable
private fun ModelSettingsRecordsCard(
    onOpenConversationCostLedger: () -> Unit,
    onOpenContextSelections: () -> Unit,
    onOpenRuntimeDiagnostics: () -> Unit,
) {
    Surface(
        color = ForegroundSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                "调用记录",
                modifier = Modifier.padding(start = 18.dp, top = 15.dp, end = 18.dp, bottom = 7.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            ModelSettingsGroupedEntry("费用与用量", "统计对话、标题、历史与南枫转写的 Token 与金额", onOpenConversationCostLedger)
            HorizontalDivider(modifier = Modifier.padding(start = 18.dp), color = NeutralBorder)
            ModelSettingsGroupedEntry("上下文记录", "查看所有回答参考了什么", onOpenContextSelections)
            HorizontalDivider(modifier = Modifier.padding(start = 18.dp), color = NeutralBorder)
            ModelSettingsGroupedEntry("运行诊断", "查看调用耗时、失败原因和连接问题", onOpenRuntimeDiagnostics)
        }
    }
}

@Composable
private fun ModelSettingsGroupedEntry(title: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "进入$title", tint = SecondaryText, modifier = Modifier.size(scaledAppIconSize(20.dp)))
        }
    }
}

@Composable
internal fun ModelSettingsContextSelectionsPage(
    records: List<ContextSelectionAuditRecord>,
    conversations: List<Conversation>,
) {
    val conversationTitles = conversations.associate { it.id.value to it.title }
    val visibleRecords = records.filter { it.selectedSources.isNotEmpty() }
    if (visibleRecords.isEmpty()) {
        Text("还没有上下文记录。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    } else Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        visibleRecords.forEach { audit ->
            Surface(modifier = Modifier.fillMaxWidth(), color = ForegroundSurface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            audit.conversationTitle(conversationTitles),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${audit.selectedSources.size} 项", color = AccentOrange, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                Text(modelNameAnnotatedText(prefix = "${audit.providerId.userLabel()} · ", modelName = audit.modelId), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = NeutralBorder)
                    audit.selectedSources.take(2).forEach { source ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                source.kind,
                                modifier = Modifier.widthIn(min = 72.dp, max = 92.dp),
                                color = BodyText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(source.title, modifier = Modifier.weight(1f), color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (audit.selectedSources.size > 2) Text("另有 ${audit.selectedSources.size - 2} 项", modifier = Modifier.align(Alignment.End), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                    RecordFooter("本轮输入约 ${audit.budget.fixedInputTokens} Token", audit.createdAt)
                }
            }
        }
    }
}

@Composable
internal fun ModelSettingsDiagnosticsPage(state: ModelSettingsUiState, conversations: List<Conversation>) {
    val conversationTitles = conversations.associate { it.id.value to it.title }
    if (state.recentDiagnostics.isEmpty()) {
        Text("近 7 天没有连接失败记录。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    } else Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.recentDiagnostics.forEach { diagnostic ->
            var detailsVisible by remember(diagnostic.id) { mutableStateOf(false) }
            Surface(modifier = Modifier.fillMaxWidth(), color = ForegroundSurface, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        diagnostic.conversationTitle(conversationTitles),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(modelNameAnnotatedText(prefix = "${diagnostic.providerId.userLabel()} · ", modelName = diagnostic.apiModelId), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text(diagnostic.userFacingSummary(), color = ErrorRed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    diagnostic.redactedBody?.takeIf { it.isNotBlank() }?.let { redactedBody ->
                        TextButton(onClick = { detailsVisible = !detailsVisible }) {
                            Text("技术详情")
                            Icon(
                                if (detailsVisible) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (detailsVisible) "收起技术详情" else "展开技术详情",
                                modifier = Modifier.size(scaledAppIconSize(18.dp)),
                            )
                        }
                        if (detailsVisible) {
                            Surface(color = SettingsPageBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${diagnostic.endpointHost} · ${diagnostic.httpStatus?.let { "HTTP $it" } ?: "未收到服务器响应"}", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                                    Text(redactedBody.take(500), color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                                }
                            }
                        }
                    }
                    RecordFooter(diagnostic.latencyLabel(), diagnostic.createdAt)
                }
            }
        }
    }
}

@Composable
private fun RecordFooter(label: String, createdAt: java.time.Instant) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(createdAt.recordTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

private fun ProviderDiagnosticRecord.latencyLabel(): String = latencyMs?.let { "耗时 ${it.readableDuration()}" } ?: "耗时未记录"

private fun Long.readableDuration(): String {
    if (this < 1_000L) return "$this 毫秒"
    val tenths = this / 100L
    return if (tenths % 10L == 0L) "${tenths / 10L} 秒" else "${tenths / 10L}.${tenths % 10L} 秒"
}

private fun ContextSelectionAuditRecord.conversationTitle(titles: Map<String, String>): String =
    conversationId?.let { titles[it] ?: "已删除的对话" } ?: "历史未关联对话"

private fun ProviderDiagnosticRecord.conversationTitle(titles: Map<String, String>): String =
    conversationId?.let { titles[it] ?: "已删除的对话" } ?: "连接测试或历史未关联记录"

private fun ProviderDiagnosticRecord.userFacingSummary(): String = when (errorClass) {
    ProviderDiagnosticErrorClass.AUTHENTICATION -> "API Key 无效或没有访问权限。"
    ProviderDiagnosticErrorClass.BALANCE -> "服务额度或余额不足。"
    ProviderDiagnosticErrorClass.RATE_LIMIT -> "请求过于频繁，请稍后再试。"
    ProviderDiagnosticErrorClass.MODEL_NOT_FOUND -> "当前服务商找不到这个模型。"
    ProviderDiagnosticErrorClass.STREAM_REQUIRED -> "当前模型只支持另一种响应方式。"
    ProviderDiagnosticErrorClass.INVALID_REQUEST -> "当前模型不接受这类请求参数。"
    ProviderDiagnosticErrorClass.TIMEOUT -> "等待服务响应超时。"
    ProviderDiagnosticErrorClass.NETWORK -> "未收到服务器响应，请检查网络后重试。"
    ProviderDiagnosticErrorClass.RESPONSE_TOO_LARGE -> "服务返回内容过大，无法安全读取。"
    ProviderDiagnosticErrorClass.RESPONSE_FORMAT -> "服务返回的数据格式无法识别。"
    ProviderDiagnosticErrorClass.SERVER -> "服务暂时异常，请稍后重试。"
    ProviderDiagnosticErrorClass.UNKNOWN -> "调用失败，请查看技术详情。"
}

private fun ProviderId.userLabel(): String = when (this) {
    ProviderId.OPENROUTER -> "OpenRouter"
    ProviderId.QWEN -> "Qwen"
    ProviderId.DEEPSEEK -> "DeepSeek"
    ProviderId.ZHIPU -> "智谱"
    ProviderId.RELAYCORE_CLAUDE -> "RelayCore Claude"
    ProviderId.RELAYCORE_GPT -> "RelayCore GPT"
    ProviderId.RELAYCORE_GEMINI -> "RelayCore Gemini"
    ProviderId.MOCK -> "本地测试"
}

private fun java.time.Instant.recordTimestamp(): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(this)

@Composable
private fun AiPresetSelectionSurface(
    selected: ModelPresetDescriptor,
    enabled: Boolean,
    allowedProvider: ProviderId,
    onSelected: (ModelPresetDescriptor) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(16.dp),
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText),
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(com.nanzhufeng.ai.domain.modelPresetNameForUser(selected), fontWeight = FontWeight.Bold)
                Text(com.nanzhufeng.ai.domain.RelayCoreModels.spec(selected.id)?.let { "RC" } ?: selected.modelFamilyHint, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开模型预设")
        }
        AiSelectionSurface(expanded = expanded, onDismiss = { expanded = false }) {
            NanfengModelServiceCatalog.presets.filter {
                it.usage == ModelPresetUsage.CHAT && NanfengModelServiceCatalog.providerFor(it.id) == allowedProvider
            }.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(com.nanzhufeng.ai.domain.modelPresetNameForUser(preset), fontWeight = FontWeight.Bold, color = BodyText)
                            Text(preset.description, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelected(preset)
                        expanded = false
                    },
                    modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
                )
            }
            if (allowedProvider == ProviderId.ZHIPU) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = NeutralBorder)
                Column(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 400.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("GLM-OCR", fontWeight = FontWeight.Bold, color = BodyText)
                    Text("图片与 PDF 转 Markdown · 使用同一智谱 API Key", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    Text("仅在左侧栏“$GLM_OCR_WORKSPACE_TITLE”中调用，不加入聊天模型选择。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AiSelectionSurface(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = ForegroundSurface,
        shadowElevation = 8.dp,
        content = content,
    )
}

/** Platform and Relay profile rows share geometry, typography and the interaction shape. */
@Composable
internal fun ModelProviderSegmentedTabs(
    choices: List<Pair<ProviderId, String>>,
    selectedProvider: ProviderId,
    widthFraction: Float = 1f,
    horizontalInset: Dp = 16.dp,
    onSelect: (ProviderId) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = horizontalInset), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(widthFraction),
            shape = RoundedCornerShape(999.dp),
            color = ForegroundSurface,
        ) {
            Row(Modifier.fillMaxWidth().padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                choices.forEach { (provider, label) ->
                    val selected = selectedProvider == provider
                    Surface(
                        onClick = { onSelect(provider) },
                        modifier = Modifier.weight(1f).height(singleLineControlHeight(32.dp, 10.sp)),
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) AccentOrange else Color.Transparent,
                        contentColor = if (selected) Color.White else BodyText,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(label, style = centeredControlTextStyle(10.sp, MaterialTheme.typography.labelMedium),
                                fontWeight = FontWeight.Medium, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
