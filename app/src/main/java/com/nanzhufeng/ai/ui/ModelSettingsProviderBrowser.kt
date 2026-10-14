package com.nanzhufeng.ai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzhufeng.ai.R
import com.nanzhufeng.ai.domain.*

@Composable
internal fun ModelProviderBrand(provider: ProviderId, platform: Boolean = false, modifier: Modifier = Modifier) {
    val drawable = when (provider) {
        ProviderId.MOCK -> return
        ProviderId.OPENROUTER -> R.drawable.model_brand_openrouter
        ProviderId.DEEPSEEK -> R.drawable.model_brand_deepseek
        ProviderId.ZHIPU -> R.drawable.model_brand_zhipu
        ProviderId.QWEN -> R.drawable.model_brand_qwen
        ProviderId.RELAYCORE_GPT -> if (platform) R.drawable.model_brand_relaycore else R.drawable.model_brand_gpt
        ProviderId.RELAYCORE_GEMINI -> if (platform) R.drawable.model_brand_relaycore else R.drawable.model_brand_gemini
        else -> if (platform) R.drawable.model_brand_relaycore else R.drawable.model_brand_claude
    }
    ModelBrandImage(drawable, modifier.size(20.dp))
}

/** Model vendor identity stays distinct from the transport platform (OpenRouter / RC). */
@Composable
internal fun ModelPresetBrand(preset: ModelPresetId, modifier: Modifier = Modifier) {
    val provider = NanfengModelServiceCatalog.providerFor(preset)
    val drawable = if (provider == ProviderId.OPENROUTER) when {
        preset.name.startsWith("CLAUDE_") -> R.drawable.model_brand_claude
        preset.name.startsWith("GPT_") -> R.drawable.model_brand_gpt
        preset.name.startsWith("GEMINI_") -> R.drawable.model_brand_gemini
        else -> R.drawable.model_brand_openrouter
    } else null
    if (drawable != null) ModelBrandImage(drawable, modifier.size(24.dp))
    else ModelProviderBrand(provider, modifier = modifier.size(24.dp))
}

@Composable
private fun ModelBrandImage(drawable: Int, modifier: Modifier) {
    val monochrome = drawable == R.drawable.model_brand_gpt || drawable == R.drawable.model_brand_zhipu
    val darkFilter = if (monochrome && BodyText.luminance() > 0.5f) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ))) else null
    Image(painterResource(drawable), contentDescription = null, colorFilter = darkFilter, modifier = modifier)
}

private val modelPlatforms = listOf(
    ProviderId.OPENROUTER to "OpenRouter",
    ProviderId.RELAYCORE_CLAUDE to "RelayCore",
    ProviderId.DEEPSEEK to "DeepSeek",
    ProviderId.ZHIPU to "智谱",
    ProviderId.QWEN to "Qwen",
)
private val relayFamilies = listOf(
    ProviderId.RELAYCORE_CLAUDE to "Claude",
    ProviderId.RELAYCORE_GPT to "GPT",
    ProviderId.RELAYCORE_GEMINI to "Gemini",
)

internal fun modelSettingsPurpose(id: ModelPresetId): String = when (id) {
    ModelPresetId.CLAUDE_OPUS_5_5 -> "深度推理 · 复杂分析与代码"
    ModelPresetId.RELAYCORE_OPUS_5_5 -> "深度推理 · 复杂分析与代码"
    ModelPresetId.CLAUDE_SONNET_5_5 -> "日常对话 · 写作与代码"
    ModelPresetId.RELAYCORE_SONNET_5_5 -> "日常对话 · 写作与代码"
    ModelPresetId.CLAUDE_HAIKU_5_5 -> "快速回复 · 高频轻量任务"
    ModelPresetId.GPT_6_1_SOL -> "专业分析 · 工程与复杂任务"
    ModelPresetId.RELAYCORE_GPT_6_1_SOL -> "专业分析 · 工程与复杂任务"
    ModelPresetId.GPT_6_LUNA -> "轻量问答 · 成本与速度均衡"
    ModelPresetId.RELAYCORE_GPT_6_ASTRA -> "高难推理 · 长程复杂任务"
    ModelPresetId.GEMINI_3_8_FLASH -> "快速处理文字、图片与文件"
    ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH -> "快速处理文字、图片与文件"
    ModelPresetId.RELAYCORE_GEMINI_3_1_PRO -> "复杂分析 · 多媒体理解"
    ModelPresetId.QWEN_3_7_PLUS -> "日常问答 · 轻量多媒体任务"
    ModelPresetId.QWEN_3_8_MAX -> "深度分析 · 复杂任务"
    ModelPresetId.QWEN_3_6_FLASH -> "知识整理 · 快速检索"
    ModelPresetId.DEEPSEEK_V4_PRO -> "深度推理 · 专业分析"
    ModelPresetId.DEEPSEEK_V4_FLASH -> "快速问答 · 图片理解"
    ModelPresetId.GLM_5_3 -> "复杂分析 · 推理与 Agent"
    ModelPresetId.GLM_5_3_FLASH -> "快速文本问答 · 高频任务"
    else -> NanfengModelServiceCatalog.preset(id).description
}

@Composable
internal fun ModelSettingsProviderBrowser(
    selectedProvider: ProviderId,
    selectedPreset: ModelPresetId,
    enabled: Boolean,
    onProviderSelected: (ProviderId, Boolean) -> Unit,
    onPresetSelected: (ModelPresetId) -> Unit,
) {
    val relay = selectedProvider in RelayCoreModels.providers
    val presets = NanfengModelServiceCatalog.chatPresets.filter { NanfengModelServiceCatalog.providerFor(it.id) == selectedProvider }.sortedBy { if (selectedProvider == ProviderId.RELAYCORE_CLAUDE && it.id == ModelPresetId.RELAYCORE_SONNET_5_5) 0 else 1 }
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtLeast(1f)
    val textMeasurer = rememberTextMeasurer()
    val platformLabelStyle = LocalTextStyle.current.copy(fontSize = scaledAppTextUnit(11.sp), fontWeight = FontWeight.SemiBold)
    val labelWidth = modelPlatforms.maxOf { (_, label) ->
        textMeasurer.measure(label, style = platformLabelStyle, maxLines = 1, softWrap = false).size.width
    }
    // Outer padding + row padding + icon + gap are 48dp; leave 16dp breathing room.
    val minimumRailWidth = with(density) { labelWidth.toDp() } + 64.dp
    val rowHeight = 60.dp * scale
    // All six current OpenRouter entries expand fully. Future entries use this same bounded viewport.
    val openRouterCount = NanfengModelServiceCatalog.chatPresets.count { NanfengModelServiceCatalog.providerFor(it.id) == ProviderId.OPENROUTER }
    val browserHeight = maxOf(260.dp * scale, rowHeight * minOf(openRouterCount, 6) + 16.dp)
    Surface(shape = RoundedCornerShape(20.dp), color = ForegroundSurface, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints {
            val railWidth = maxOf(minimumRailWidth, (maxWidth * 0.28f).coerceAtMost(144.dp * scale))
            Row(Modifier.fillMaxWidth().height(browserHeight)) {
                Surface(color = lerp(ForegroundSurface, SettingsPageBackground, 0.5f), modifier = Modifier.width(railWidth).fillMaxHeight()) {
                    Column(Modifier.padding(6.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        modelPlatforms.forEach { (provider, label) ->
                            val active = if (provider == ProviderId.RELAYCORE_CLAUDE) relay else provider == selectedProvider
                            Surface(
                                onClick = { onProviderSelected(provider, true) }, enabled = enabled,
                                shape = RoundedCornerShape(12.dp), color = if (active) AccentOrange.copy(alpha = 0.10f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp * scale).semantics { selected = active; role = Role.Tab },
                            ) {
                                Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ModelProviderBrand(provider, platform = true, modifier = Modifier.size(18.dp))
                                    Text(label, color = if (active) AccentOrange else BodyText, style = platformLabelStyle, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(8.dp)) {
                    if (relay) {
                        Surface(shape = RoundedCornerShape(13.dp), color = SecondaryText.copy(alpha = 0.065f)) {
                            Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
                                relayFamilies.forEach { (provider, label) ->
                                    val active = provider == selectedProvider
                                    Surface(
                                        onClick = { onProviderSelected(provider, false) }, enabled = enabled,
                                        shape = RoundedCornerShape(10.dp), color = if (active) AccentOrange.copy(alpha = 0.10f) else Color.Transparent,
                                        modifier = Modifier.weight(1f).heightIn(min = singleLineControlHeight(32.dp, 11.sp)).semantics { selected = active; role = Role.Tab },
                                    ) {
                                        Row(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)) {
                                            ModelProviderBrand(provider, modifier = Modifier.size(14.dp))
                                            Text(label, fontSize = 11.sp, color = if (active) AccentOrange else BodyText, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    key(selectedProvider) {
                        val visibleModelCapacity = if (relay) 5 else 6
                        val listModifier = if (presets.size > visibleModelCapacity) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier
                        Column(listModifier.fillMaxWidth().selectableGroup()) {
                            presets.forEach { preset ->
                                val active = preset.id == selectedPreset
                                Surface(
                                    onClick = { onPresetSelected(preset.id) }, enabled = enabled,
                                    shape = RoundedCornerShape(13.dp), color = if (active) AccentOrange.copy(alpha = 0.09f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = rowHeight).semantics { selected = active; role = Role.RadioButton },
                                ) {
                                    Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RadioButton(selected = active, onClick = null, modifier = Modifier.size(18.dp), colors = RadioButtonDefaults.colors(selectedColor = AccentOrange, unselectedColor = SecondaryText.copy(alpha = 0.5f)))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(modelPresetNameForUser(preset), color = BodyText, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                                            Text(modelSettingsPurpose(preset.id), color = SecondaryText, fontSize = 9.sp, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                            if (selectedProvider == ProviderId.ZHIPU) {
                                val active = selectedPreset == ModelPresetId.GLM_OCR
                                Surface(
                                    onClick = { onPresetSelected(ModelPresetId.GLM_OCR) }, enabled = enabled,
                                    shape = RoundedCornerShape(13.dp), color = if (active) AccentOrange.copy(alpha = 0.09f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth().height(rowHeight).semantics { selected = active; role = Role.RadioButton },
                                ) {
                                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RadioButton(selected = active, onClick = null, modifier = Modifier.size(18.dp), colors = RadioButtonDefaults.colors(selectedColor = AccentOrange))
                                        Column(Modifier.weight(1f)) {
                                            Text("GLM-OCR", color = BodyText, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                                            Text("图片、PDF 转 Markdown · 共用智谱 Key", color = SecondaryText, fontSize = 9.sp, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
