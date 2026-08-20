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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nanzhufeng.ai.domain.CredentialState
import com.nanzhufeng.ai.domain.ModelPresetDescriptor
import com.nanzhufeng.ai.domain.ModelPresetId
import com.nanzhufeng.ai.domain.NanfengModelServiceCatalog

@Composable
internal fun ModelServiceStatusCard(state: ModelSettingsUiState, onOpen: () -> Unit) {
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = BrandGreen)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("AI 模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val configuration = state.configuration
                val summary = when {
                    state.loading -> "正在读取…"
                    configuration == null -> "设置暂不可用"
                    configuration.settings.enabled && configuration.credentialState == CredentialState.STORED ->
                        "已配置 · ${configuration.provider.displayName} · ${configuration.preset.displayName}"
                    configuration.credentialState == CredentialState.STORED ->
                        "密钥已保存 · ${configuration.provider.displayName} · ${configuration.preset.displayName}"
                    else -> "尚未配置"
                }
                Text(summary, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(state.error.title, color = ErrorRed, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpen,
            enabled = !state.loading && state.configuration != null,
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
    onSave: (Boolean, ModelPresetId, String?) -> Unit,
    onRevealStoredCredential: () -> Unit,
) {
    val configuration = state.configuration ?: return
    var enabled by remember(configuration.settings.enabled) { mutableStateOf(configuration.settings.enabled) }
    var presetId by remember(configuration.settings.presetId) { mutableStateOf(configuration.settings.presetId) }
    val savedKeyMask = "••••••••"
    var keyInput by remember(configuration.credentialState) {
        mutableStateOf(if (configuration.credentialState == CredentialState.STORED) savedKeyMask else "")
    }
    var keyEdited by remember(configuration.credentialState) { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
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
                    onSelected = { presetId = it.id },
                )

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
                            else if (!keyEdited && configuration.credentialState == CredentialState.STORED) onRevealStoredCredential()
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

                state.error?.let { error ->
                    Text(error.title, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                    Text(error.suggestion, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !state.saving) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(enabled, presetId, keyInput.takeIf { keyEdited && it.isNotBlank() }) },
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
private fun AiPresetSelectionSurface(
    selected: ModelPresetDescriptor,
    enabled: Boolean,
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
            NanfengModelServiceCatalog.presets.forEach { preset ->
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
