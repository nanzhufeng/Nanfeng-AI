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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.DialogProperties
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.ModelPresetDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.ModelServiceConfiguration
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog
import com.nanzhufeng.ai.domain.ProviderId
import com.nanzhufeng.ai.domain.ChatRoutingPolicy

@Composable
private fun ProviderReadinessLine(label: String, configuration: ModelServiceConfiguration?) {
    val ready = configuration?.settings?.enabled == true && configuration.credentialState == CredentialState.STORED
    Text(
        "${if (ready) "●" else "○"} $label · ${if (ready) "已连接" else "未启用"}",
        color = if (ready) BrandGreen else SecondaryText,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
internal fun ModelServiceStatusCard(state: ModelSettingsUiState, onOpen: () -> Unit) {
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = BrandGreen)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI 模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val openRouter = state.providerConfigurations[ProviderId.OPENROUTER] ?: state.configuration
                val qwen = state.providerConfigurations[ProviderId.QWEN]
                val deepSeek = state.providerConfigurations[ProviderId.DEEPSEEK]
                val summary = when {
                    state.loading -> "正在读取…"
                    openRouter == null -> "设置暂不可用"
                    openRouter.settings.enabled && openRouter.credentialState == CredentialState.STORED ->
                        "OpenRouter 已配置 · ${openRouter.preset.displayName}"
                    openRouter.credentialState == CredentialState.STORED ->
                        "OpenRouter 密钥已保存"
                    else -> "尚未配置"
                }
                Text(summary, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ProviderReadinessLine("OpenAI / Claude / Gemini", openRouter)
                ProviderReadinessLine("Qwen", qwen)
                ProviderReadinessLine("DeepSeek", deepSeek)
                Text("本地调用记录 ${state.callAuditSummary.recordCount} 条 · ${state.callAuditSummary.storageBytes / 1024} KB", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error.title, color = ErrorRed, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpen,
            enabled = !state.loading && state.providerConfigurations.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("管理模型")
        }
    }
}

@Composable
internal fun ModelSettingsDialog(
    state: ModelSettingsUiState,
    onDismiss: () -> Unit,
    onSave: (ProviderId, Boolean, ModelPresetId, String?) -> Unit,
    onRevealStoredCredential: (ProviderId) -> Unit,
    onVerifyRegistry: () -> Unit,
    onSaveRoutingPolicy: (ChatRoutingPolicy) -> Unit,
    onTestConnection: (ProviderId) -> Unit,
) {
    var selectedProvider by remember { mutableStateOf(ProviderId.OPENROUTER) }
    val configuration = state.providerConfigurations[selectedProvider] ?: return
    var enabled by remember(selectedProvider, configuration.settings.enabled) { mutableStateOf(configuration.settings.enabled) }
    var presetId by remember(selectedProvider, configuration.settings.presetId) { mutableStateOf(configuration.settings.presetId) }
    val savedKeyMask = "••••••••"
    var keyInput by remember(selectedProvider, configuration.credentialState) {
        mutableStateOf(if (configuration.credentialState == CredentialState.STORED) savedKeyMask else "")
    }
    var keyEdited by remember(selectedProvider, configuration.credentialState) { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var routingPolicy by remember(state.routingPolicy) { mutableStateOf(state.routingPolicy) }
    LaunchedEffect(state.revealedCredential) {
        if (!keyEdited && state.revealedCredential != null) {
            keyInput = state.revealedCredential
            keyVisible = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !state.saving, dismissOnClickOutside = !state.saving),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("模型设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("OpenAI、Claude 与 Gemini 共用 OpenRouter；Qwen、DeepSeek 使用官方直连。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ProviderId.OPENROUTER, ProviderId.QWEN, ProviderId.DEEPSEEK).forEach { provider ->
                        val label = when (provider) {
                            ProviderId.OPENROUTER -> "OpenRouter"
                            ProviderId.QWEN -> "Qwen"
                            ProviderId.DEEPSEEK -> "DeepSeek"
                            ProviderId.MOCK -> ""
                        }
                        FilterChip(selected = selectedProvider == provider, onClick = { selectedProvider = provider }, label = { Text(label) })
                    }
                }

                Text("路由", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("自动模式只按本机任务事实选定一个模型；每次发送只请求一次，失败后会清楚显示原因，不会静默换模型或跨服务商重发。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("自动兜底：已关闭；允许跨服务商兜底：关闭。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                RoutingSwitchRow("自动路由", routingPolicy.autoRoutingEnabled) { routingPolicy = routingPolicy.copy(autoRoutingEnabled = it) }
                RoutingSwitchRow("质量升级", routingPolicy.qualityEscalationEnabled) { routingPolicy = routingPolicy.copy(qualityEscalationEnabled = it) }
                Text("跨模型复核：${when (routingPolicy.crossModelReviewPolicy) { com.nanzhufeng.ai.domain.CrossModelReviewPolicy.NEVER -> "从不"; com.nanzhufeng.ai.domain.CrossModelReviewPolicy.IMPORTANT_ONLY -> "仅重要任务"; com.nanzhufeng.ai.domain.CrossModelReviewPolicy.ALWAYS -> "始终" }}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.nanzhufeng.ai.domain.CrossModelReviewPolicy.entries.forEach { policy ->
                        FilterChip(selected = routingPolicy.crossModelReviewPolicy == policy, onClick = { routingPolicy = routingPolicy.copy(crossModelReviewPolicy = policy) }, label = { Text(when (policy) { com.nanzhufeng.ai.domain.CrossModelReviewPolicy.NEVER -> "从不"; com.nanzhufeng.ai.domain.CrossModelReviewPolicy.IMPORTANT_ONLY -> "重要任务"; com.nanzhufeng.ai.domain.CrossModelReviewPolicy.ALWAYS -> "始终" }) })
                    }
                }
                OutlinedButton(onClick = { onSaveRoutingPolicy(routingPolicy) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(16.dp)) { Text("保存路由策略") }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF4F7FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6E0F2)),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(configuration.provider.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFF28496F))
                        Switch(checked = enabled, onCheckedChange = { enabled = it }, enabled = !state.saving)
                    }
                }

                AiPresetSelectionSurface(
                    selected = NanfengModelServiceCatalog.preset(presetId),
                    enabled = !state.saving,
                    allowedProvider = selectedProvider,
                    onSelected = { presetId = it.id },
                )

                if (selectedProvider == ProviderId.OPENROUTER) Text(
                    when (state.registryStatus.status) {
                        com.nanzhufeng.ai.domain.RegistryVerificationDisplayStatus.VERIFIED -> "模型目录已核验"
                        com.nanzhufeng.ai.domain.RegistryVerificationDisplayStatus.STABLE_FALLBACK -> "本次核验失败，正在使用上次稳定目录"
                        com.nanzhufeng.ai.domain.RegistryVerificationDisplayStatus.NOT_VERIFIED -> "请先核验模型目录，才能直接发送真实对话"
                    },
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selectedProvider == ProviderId.OPENROUTER) OutlinedButton(
                    onClick = onVerifyRegistry,
                    enabled = !state.saving && !state.verifyingRegistry,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.verifyingRegistry) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("核验模型目录")
                }

                OutlinedButton(
                    onClick = { onTestConnection(selectedProvider) },
                    enabled = !state.saving && !state.verifyingRegistry && !state.probingConnection,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.probingConnection) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("测试连接（仅发送固定 hi）")
                }
                Text("仅在你点击后使用当前服务商、模型和本机 Key 发送 1-token 固定文本；不会发送对话、附件或上下文。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { input ->
                        keyInput = if (!keyEdited && input.startsWith(savedKeyMask)) input.removePrefix(savedKeyMask) else input
                        keyEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("API Key") },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (keyVisible) keyVisible = false
                            else if (!keyEdited && configuration.credentialState == CredentialState.STORED) onRevealStoredCredential(selectedProvider)
                            else keyVisible = true
                        }) {
                            Icon(
                                if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key",
                            )
                        }
                    },
                )
                Text(if (configuration.credentialState == CredentialState.STORED && !keyEdited) "已安全保存。输入新值可替换。" else "仅保存在本机。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)

                if (state.recentDiagnostics.isNotEmpty()) {
                    Text("连接诊断（仅本机，7 天）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("以下仅显示已脱敏的失败摘要；不会显示 Key、消息正文、附件或完整响应。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    state.recentDiagnostics.take(3).forEach { diagnostic ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFF3F1),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${diagnostic.providerId.name} · ${diagnostic.errorClass.name} · ${diagnostic.httpStatus ?: "无 HTTP 响应"}", color = ErrorRed, style = MaterialTheme.typography.labelLarge)
                                Text("${diagnostic.endpointHost} · ${diagnostic.apiModelId}", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                                diagnostic.redactedBody?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }

                if (state.recentContextSelections.isNotEmpty()) {
                    Text("上下文选材诊断（仅本机）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("仅记录本次实际选中的资料标题、类型和估算 Token；不保存正文、附件、提示词或回复。", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    state.recentContextSelections.forEach { audit ->
                        Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFF5F8F6), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${audit.providerId.name} · ${audit.modelId} · ${audit.tokenizerId}", style = MaterialTheme.typography.labelLarge)
                                Text("上下文 ${audit.budget.contextWindowTokens} · 预留输出 ${audit.budget.reservedOutputTokens} · 当前消息/附件 ${audit.budget.fixedInputTokens} · 检索预算 ${audit.budget.retrievalTokens} Token", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                                if (audit.indexStatus != com.nanzhufeng.ai.domain.LocalContextBroker.AssemblyStatus.READY) Text("本地索引不可用：本次未注入 Memory、知识库或历史资料。", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                                val sources = audit.selectedSources.joinToString("；") { "${it.kind}：${it.title}（${it.estimatedTokens}）" }
                                Text(if (sources.isBlank()) "本次未选中本地资料。" else sources, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                state.error?.let { error ->
                    Text(error.title, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                    Text(error.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !state.saving) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(selectedProvider, enabled, presetId, keyInput.takeIf { keyEdited && it.isNotBlank() }) },
                        enabled = !state.saving,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
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
    }
}

@Composable
private fun RoutingSwitchRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

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
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(selected.displayName, fontWeight = FontWeight.SemiBold)
                Text(selected.modelFamilyHint, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "展开模型预设")
        }
        AiSelectionSurface(expanded = expanded, onDismiss = { expanded = false }) {
            NanfengModelServiceCatalog.presets.filter { NanfengModelServiceCatalog.providerFor(it.id) == allowedProvider }.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(preset.displayName, fontWeight = FontWeight.SemiBold, color = Color(0xFF28496F))
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
        containerColor = Color.White,
        shadowElevation = 8.dp,
        content = content,
    )
}
