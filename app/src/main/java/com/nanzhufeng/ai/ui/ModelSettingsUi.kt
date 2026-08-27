package com.nanzhufeng.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.unit.dp
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.ModelPresetDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
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
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ProviderReadinessLine("OpenAI / Claude / Gemini", openRouter)
            ProviderReadinessLine("Qwen", qwen)
            ProviderReadinessLine("DeepSeek", deepSeek)
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
        "需要当前信息时自动检索公开网页并标注来源；可能产生服务费用"
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
    onSave: (ProviderId, Boolean, ModelPresetId, String?) -> Unit,
    onRevealStoredCredential: (ProviderId) -> Unit,
    onTestConnection: (ProviderId) -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(ProviderId.OPENROUTER) }
    val configuration = state.providerConfigurations[selectedProvider] ?: return
    var enabled by remember(selectedProvider, configuration.settings.enabled) { mutableStateOf(configuration.settings.enabled) }
    var presetId by remember(selectedProvider, configuration.settings.presetId) { mutableStateOf(configuration.settings.presetId) }
    // The stored-key state is deliberately a stable, full-length visual mask.  It never reflects
    // the actual key length and must look identical before and after the visibility control is used.
    val savedKeyMask = "••••••••••••••••••••••••••••••••"
    var keyInput by remember(selectedProvider, configuration.credentialState) {
        mutableStateOf(if (configuration.credentialState == CredentialState.STORED) savedKeyMask else "")
    }
    var keyEdited by remember(selectedProvider, configuration.credentialState) { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.revealedCredential) {
        if (!keyEdited && state.revealedCredential != null) {
            keyInput = state.revealedCredential
            keyVisible = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = RoundedCornerShape(999.dp),
            color = ForegroundSurface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(ProviderId.OPENROUTER, ProviderId.QWEN, ProviderId.DEEPSEEK).forEach { provider ->
                    val label = when (provider) {
                        ProviderId.OPENROUTER -> "OpenRouter"
                        ProviderId.QWEN -> "Qwen"
                        ProviderId.DEEPSEEK -> "DeepSeek"
                        ProviderId.MOCK -> ""
                    }
                    val selected = selectedProvider == provider
                    Surface(
                        onClick = { selectedProvider = provider },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) AccentOrange else Color.Transparent,
                        contentColor = if (selected) Color.White else BodyText,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = P5AInteractiveShape,
                    color = ForegroundSurface,
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(configuration.provider.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = BodyText)
                SettingsSwitch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    enabled = !state.saving,
                )
            }
        }

        AiPresetSelectionSurface(
                    selected = NanfengModelServiceCatalog.preset(presetId),
                    enabled = !state.saving,
                    allowedProvider = selectedProvider,
                    onSelected = { presetId = it.id },
        )

        OutlinedButton(
                    onClick = { onTestConnection(selectedProvider) },
                    enabled = !state.saving && !state.probingConnection,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = P5AInteractiveShape,
                    border = null,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText),
        ) {
            if (state.probingConnection) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在测试…")
            } else Text("测试连接")
        }
        ConnectionTestFeedback(state)
        ModelSettingsSaveFeedback(state)

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
                            if (keyVisible) keyVisible = false
                            else if (!keyEdited && configuration.credentialState == CredentialState.STORED) onRevealStoredCredential(selectedProvider)
                            else keyVisible = true
                        }) {
                            Icon(
                                if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                            )
                        }
                    },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                        onClick = { onSave(selectedProvider, enabled, presetId, keyInput.takeIf { keyEdited && it.isNotBlank() }) },
                        enabled = !state.saving,
                        shape = P5AInteractiveShape,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.saving) "正在保存…" else "保存")
            }
        }
    }
}

@Composable
private fun ConnectionTestFeedback(state: ModelSettingsUiState) {
    when {
        state.probingConnection -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                "正在检查连接，请稍候。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                color = BodyText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.notice?.startsWith("连接成功：") == true -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NeutralAssistantSurface,
        ) {
            Text(
                state.notice,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                color = AccentOrange,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
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
        state.notice == "API Key 已安全保存在本机。" -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NeutralAssistantSurface,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(state.notice, color = AccentOrange, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text("尚未测试连接；请点击“测试连接”确认 API Key 是否可用。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.notice == "模型服务设置已安全保存在本机。" -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NeutralAssistantSurface,
        ) {
            Text(
                state.notice,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                color = AccentOrange,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
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
                Text("OpenRouter、Qwen、DeepSeek 的 API Key、模型与连接", color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
            ModelSettingsGroupedEntry("费用与用量", "统计对话、标题与提醒整理的 Token 与金额", onOpenConversationCostLedger)
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
    if (records.isEmpty()) {
        Text("还没有上下文记录。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    } else records.forEach { audit ->
        Surface(modifier = Modifier.fillMaxWidth(), color = ForegroundSurface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(audit.conversationTitle(conversationTitles), fontWeight = FontWeight.SemiBold)
                Text(audit.createdAt.recordTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                Text("${audit.providerId.userLabel()} · ${audit.modelId}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                if (audit.selectedSources.isEmpty()) {
                    Text("本次没有加入记忆、知识库或历史资料。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("已加入 ${audit.selectedSources.size} 项资料", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    audit.selectedSources.take(2).forEach { source ->
                        Text("${source.kind} · ${source.title}", color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (audit.selectedSources.size > 2) Text("其余 ${audit.selectedSources.size - 2} 项资料", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
                Text("本次消息与附件约 ${audit.budget.fixedInputTokens} Token", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun ModelSettingsDiagnosticsPage(state: ModelSettingsUiState, conversations: List<Conversation>) {
    val conversationTitles = conversations.associate { it.id.value to it.title }
    if (state.recentDiagnostics.isEmpty()) {
        Text("近 7 天没有连接失败记录。", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
    } else state.recentDiagnostics.forEach { diagnostic ->
        var detailsVisible by remember(diagnostic.id) { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxWidth(), color = ForegroundSurface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(diagnostic.conversationTitle(conversationTitles), fontWeight = FontWeight.SemiBold)
                Text(diagnostic.createdAt.recordTimestamp(), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                Text("${diagnostic.providerId.userLabel()} · ${diagnostic.apiModelId}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text(diagnostic.userFacingSummary(), color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                diagnostic.redactedBody?.takeIf { it.isNotBlank() }?.let {
                    TextButton(onClick = { detailsVisible = !detailsVisible }) {
                        Text(if (detailsVisible) "收起技术详情" else "查看技术详情")
                    }
                    if (detailsVisible) {
                        Text("${diagnostic.endpointHost} · ${diagnostic.httpStatus?.let { "HTTP $it" } ?: "未收到服务器响应"}", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                        Text(it.take(500), color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                    }
                }
            }
        }
    }
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = ForegroundSurface, contentColor = BodyText),
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(selected.displayName, fontWeight = FontWeight.SemiBold)
                Text(selected.modelFamilyHint, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "展开模型预设")
        }
        AiSelectionSurface(expanded = expanded, onDismiss = { expanded = false }) {
            NanfengModelServiceCatalog.presets.filter { NanfengModelServiceCatalog.providerFor(it.id) == allowedProvider }.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(preset.displayName, fontWeight = FontWeight.SemiBold, color = BodyText)
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
