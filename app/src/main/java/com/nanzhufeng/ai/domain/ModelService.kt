package com.nanzhufeng.ai.domain

object NanfengModelServiceCatalog {
    /** Only current selections migrate; persisted response attribution keeps its original identity. */
    fun currentPreset(preset: ModelPresetId): ModelPresetId = when (preset) {
        ModelPresetId.CLAUDE_OPUS_5 -> ModelPresetId.CLAUDE_OPUS_5_5
        ModelPresetId.CLAUDE_SONNET_5 -> ModelPresetId.CLAUDE_SONNET_5_5
        ModelPresetId.CLAUDE_HAIKU_4_5 -> ModelPresetId.CLAUDE_HAIKU_5_5
        ModelPresetId.GPT_5_6_SOL -> ModelPresetId.GPT_6_1_SOL
        ModelPresetId.GPT_5_6_TERRA, ModelPresetId.GPT_5_6_LUNA -> ModelPresetId.GPT_6_LUNA
        else -> preset
    }

    val openRouter = ProviderDescriptor(
        id = ProviderId.OPENROUTER,
        displayName = "OpenRouter",
        fixedEndpoint = "https://openrouter.ai/api/v1",
    )

    val qwen = ProviderDescriptor(
        id = ProviderId.QWEN,
        displayName = "Qwen 官方直连",
        fixedEndpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    )

    val deepSeek = ProviderDescriptor(
        id = ProviderId.DEEPSEEK,
        displayName = "DeepSeek 官方直连",
        fixedEndpoint = "https://api.deepseek.com/v1",
    )

    val zhipu = ProviderDescriptor(
        id = ProviderId.ZHIPU,
        displayName = "智谱 BigModel 官方直连",
        fixedEndpoint = "https://open.bigmodel.cn/api/paas/v4",
    )

    val presets = RelayCoreModels.specs.map { spec -> ModelPresetDescriptor(spec.preset, spec.compactName, "平台列出；调用身份以响应为准。", "RelayCore") } + listOf(
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_OPUS_5_5,
            displayName = "Claude Opus 5.5",
            description = "适合复杂任务，默认 Medium effort。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_SONNET_5_5,
            displayName = "Claude Sonnet 5.5",
            description = "高效处理日常工作，默认 High effort。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_HAIKU_5_5,
            displayName = "Claude Haiku 5.5",
            description = "快速处理高频、轻量任务。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_6_1_SOL,
            displayName = "GPT-6.1 Sol",
            description = "适合专业复杂任务和深度分析。",
            modelFamilyHint = "OpenAI",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_6_LUNA,
            displayName = "GPT-6 Luna",
            description = "适合高频、轻量任务。",
            modelFamilyHint = "OpenAI",
        ),

        ModelPresetDescriptor(
            id = ModelPresetId.MUSE_WONDER,
            displayName = "Muse Wonder",
            description = "适合复杂分析与长程任务。",
            modelFamilyHint = "Meta",
        ),

        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_FABLE_5,
            displayName = "Claude Fable 5",
            description = "旧版深度选择；既有会话保留其实际归因。",
            modelFamilyHint = "Anthropic",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_FABLE_5_1,
            displayName = "Claude Fable 5.1",
            description = "适合长程代码、研究与复杂知识工作。",
            modelFamilyHint = "Anthropic",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_OPUS_5,
            displayName = "Claude Opus 5",
            description = "适合复杂任务与深度推理。",
            modelFamilyHint = "Anthropic",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_SONNET_5,
            displayName = "Claude Sonnet 5",
            description = "高效处理日常工作。",
            modelFamilyHint = "Anthropic",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_HAIKU_4_5,
            displayName = "Claude Haiku 4.5",
            description = "快速获得简洁答案。",
            modelFamilyHint = "Anthropic",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_SOL,
            displayName = "GPT-5.6 Sol",
            description = "前沿能力，适合专业复杂任务。",
            modelFamilyHint = "OpenAI",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_TERRA,
            displayName = "GPT-5.6 Terra",
            description = "能力与成本更均衡。",
            modelFamilyHint = "OpenAI",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_LUNA,
            displayName = "GPT-5.6 Luna",
            description = "适合高频、轻量任务。",
            modelFamilyHint = "OpenAI",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_6_ASTRA,
            displayName = "GPT-6 Astra",
            description = "适合高难分析、工程与长程复杂任务。",
            modelFamilyHint = "OpenAI",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GROK_4_1_FAST,
            displayName = "Grok 4.1 Fast（已下线）",
            description = "OpenRouter 当前目录已无此精确模型；保留仅为识别旧会话。",
            modelFamilyHint = "xAI · OpenRouter",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GROK_4_5,
            displayName = "Grok 4.5（已移除）",
            description = "已从产品模型列表移除；保留仅为识别旧会话。",
            modelFamilyHint = "xAI · OpenRouter",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GROK_4_6_HIGH,
            displayName = "Grok 4.6 High（已移除）",
            description = "已从产品模型列表移除；保留仅为识别旧会话。",
            modelFamilyHint = "xAI · OpenRouter",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GEMINI_3_8_FLASH,
            displayName = "Gemini 3.8 Flash",
            description = "快速处理文字、图片和文件任务。",
            modelFamilyHint = "Google · OpenRouter",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GEMINI_3_7_FLASH,
            displayName = "Gemini 3.7 Flash",
            description = "已由 Gemini 3.8 Flash 替换；保留仅为识别历史记录。",
            modelFamilyHint = "Google · OpenRouter",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.KIMI_K3,
            displayName = "Kimi K3",
            description = "复杂分析 · Agent · 长上下文",
            modelFamilyHint = "Moonshot AI · OpenRouter",
            usage = ModelPresetUsage.RETIRED,
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.QWEN_3_7_PLUS,
            displayName = "Qwen3.7-Plus",
            description = "日常问答与轻量多媒体任务。",
            modelFamilyHint = "Qwen · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.QWEN_3_8_MAX,
            displayName = "Qwen3.8-Max",
            description = "适合深度分析与复杂任务。",
            modelFamilyHint = "Qwen · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.QWEN_3_6_FLASH,
            displayName = "Qwen3.6 Flash",
            description = "适合大批量知识整理与快速检索。",
            modelFamilyHint = "Qwen · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.DEEPSEEK_V4_PRO,
            displayName = "DeepSeek V4 Pro",
            description = "适合深度推理与专业分析。",
            modelFamilyHint = "DeepSeek · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.DEEPSEEK_V4_FLASH,
            displayName = "DeepSeek V4.1 Flash",
            description = "适合快速问答、高频任务与图片理解。",
            modelFamilyHint = "DeepSeek · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GLM_5_3,
            displayName = "GLM-5.3",
            description = "适合深度推理、复杂分析与 Agent 任务。",
            modelFamilyHint = "智谱 · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GLM_5_3_FLASH,
            displayName = "GLM-5.3 Flash",
            description = "智谱官方直连的快速文本任务。",
            modelFamilyHint = "智谱 · 官方直连",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GLM_OCR,
            displayName = "GLM-OCR",
            description = "图片与 PDF 转 Markdown；仅在文档转换工作区使用。",
            modelFamilyHint = "智谱 · 官方直连",
            usage = ModelPresetUsage.DOCUMENT_OCR,
        ),
    )

    /** Only these entries may appear in chat routing and composer model selection. */
    val chatPresets: List<ModelPresetDescriptor> get() = presets.filter { it.usage == ModelPresetUsage.CHAT }

    fun provider(providerId: ProviderId): ProviderDescriptor? = when (providerId) {
        ProviderId.OPENROUTER -> openRouter
        ProviderId.QWEN -> qwen
        ProviderId.DEEPSEEK -> deepSeek
        ProviderId.ZHIPU -> zhipu
        ProviderId.RELAYCORE_CLAUDE -> ProviderDescriptor(providerId, "RelayCore Claude · Kiro正价", "https://api.relaycore.cn/v1")
        ProviderId.RELAYCORE_GPT -> ProviderDescriptor(providerId, "RelayCore GPT · Pro3", "https://api.relaycore.cn/v1")
        ProviderId.RELAYCORE_GEMINI -> ProviderDescriptor(providerId, "RelayCore Gemini", "https://api.relaycore.cn/v1")
        ProviderId.MOCK -> null
    }

    fun providerFor(presetId: ModelPresetId): ProviderId = RelayCoreModels.spec(presetId)?.provider ?: when (presetId) {
        ModelPresetId.QWEN_3_7_PLUS, ModelPresetId.QWEN_3_8_MAX, ModelPresetId.QWEN_3_6_FLASH -> ProviderId.QWEN
        ModelPresetId.DEEPSEEK_V4_PRO, ModelPresetId.DEEPSEEK_V4_FLASH -> ProviderId.DEEPSEEK
        ModelPresetId.GLM_5_3, ModelPresetId.GLM_5_3_FLASH, ModelPresetId.GLM_OCR -> ProviderId.ZHIPU
        else -> ProviderId.OPENROUTER
    }

    fun defaultPreset(providerId: ProviderId): ModelPresetId = when (providerId) {
        ProviderId.OPENROUTER -> ModelPresetId.GPT_6_LUNA
        ProviderId.QWEN -> ModelPresetId.QWEN_3_7_PLUS
        ProviderId.DEEPSEEK -> ModelPresetId.DEEPSEEK_V4_PRO
        ProviderId.ZHIPU -> ModelPresetId.GLM_5_3_FLASH
        ProviderId.RELAYCORE_CLAUDE -> ModelPresetId.RELAYCORE_SONNET_5_5
        ProviderId.RELAYCORE_GPT -> ModelPresetId.RELAYCORE_GPT_6_1_SOL
        ProviderId.RELAYCORE_GEMINI -> ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH
        ProviderId.MOCK -> ModelPresetId.GPT_6_LUNA
    }

    fun preset(presetId: ModelPresetId): ModelPresetDescriptor =
        presets.firstOrNull { it.id == presetId } ?: presets.first()

    enum class AutoTask { DEFAULT_TEXT, COMPLEX_REASONING, ATTACHMENT }

    /** Product-owned Auto order. It is independent from picker/catalog display order. */
    private val autoPlans = mapOf(
        AutoTask.DEFAULT_TEXT to listOf(
            ModelPresetId.RELAYCORE_GPT_6_1_SOL,
            ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH,
            ModelPresetId.DEEPSEEK_V4_FLASH,
            ModelPresetId.RELAYCORE_SONNET_5_5,
            ModelPresetId.QWEN_3_7_PLUS,
            ModelPresetId.GLM_5_3_FLASH,
            ModelPresetId.QWEN_3_6_FLASH,
            ModelPresetId.RELAYCORE_GEMINI_3_1_PRO,
            ModelPresetId.DEEPSEEK_V4_PRO,
            ModelPresetId.GLM_5_3,
            ModelPresetId.QWEN_3_8_MAX,
            ModelPresetId.RELAYCORE_OPUS_5_5,
            ModelPresetId.RELAYCORE_GPT_6_ASTRA,
            ModelPresetId.CLAUDE_SONNET_5_5,
            ModelPresetId.GPT_6_LUNA,
            ModelPresetId.CLAUDE_HAIKU_5_5,
            ModelPresetId.GPT_6_1_SOL,
            ModelPresetId.CLAUDE_OPUS_5_5,
            ModelPresetId.GEMINI_3_8_FLASH,
        ),
        AutoTask.COMPLEX_REASONING to listOf(
            ModelPresetId.RELAYCORE_GPT_6_ASTRA,
            ModelPresetId.RELAYCORE_OPUS_5_5,
            ModelPresetId.RELAYCORE_GPT_6_1_SOL,
            ModelPresetId.DEEPSEEK_V4_PRO,
            ModelPresetId.QWEN_3_8_MAX,
            ModelPresetId.GLM_5_3,
            ModelPresetId.RELAYCORE_GEMINI_3_1_PRO,
            ModelPresetId.RELAYCORE_SONNET_5_5,
            ModelPresetId.RELAYCORE_GEMINI_3_8_FLASH,
            ModelPresetId.DEEPSEEK_V4_FLASH,
            ModelPresetId.QWEN_3_7_PLUS,
            ModelPresetId.GLM_5_3_FLASH,
            ModelPresetId.QWEN_3_6_FLASH,
            ModelPresetId.CLAUDE_OPUS_5_5,
            ModelPresetId.GPT_6_1_SOL,
            ModelPresetId.CLAUDE_SONNET_5_5,
            ModelPresetId.GPT_6_LUNA,
            ModelPresetId.CLAUDE_HAIKU_5_5,
            ModelPresetId.GEMINI_3_8_FLASH,
        ),
        AutoTask.ATTACHMENT to listOf(
            ModelPresetId.QWEN_3_7_PLUS,
            ModelPresetId.DEEPSEEK_V4_FLASH,
            ModelPresetId.QWEN_3_6_FLASH,
            ModelPresetId.QWEN_3_8_MAX,
            ModelPresetId.GLM_5_3_FLASH,
            ModelPresetId.DEEPSEEK_V4_PRO,
            ModelPresetId.GLM_5_3,
            ModelPresetId.GEMINI_3_8_FLASH,
            ModelPresetId.GPT_6_LUNA,
            ModelPresetId.CLAUDE_SONNET_5_5,
            ModelPresetId.GPT_6_1_SOL,
            ModelPresetId.CLAUDE_OPUS_5_5,
            ModelPresetId.CLAUDE_HAIKU_5_5,
        ),
    )

    fun autoTask(facts: AutoRoutingFacts): AutoTask = when {
        facts.hasAttachment -> AutoTask.ATTACHMENT
        facts.requiresComplexReasoning -> AutoTask.COMPLEX_REASONING
        else -> AutoTask.DEFAULT_TEXT
    }

    /** Every plan is exhaustive; availability and exact capabilities are applied afterwards. */
    fun autoRoutingCandidates(facts: AutoRoutingFacts): List<ModelPresetId> {
        return autoPlans.getValue(autoTask(facts))
    }
}

/**
 * The curated model catalog is the single user-facing naming source for Composer and transcript
 * metadata. Transport/vendor information remains in attribution and consent facts, never in a
 * model-name slot.
 */
fun modelPresetNameForUser(preset: ModelPresetDescriptor): String =
    RelayCoreModels.spec(preset.id)?.let { "${it.uiName} · RC" } ?: modelDisplayNameForUser(preset.displayName)

fun modelDisplayNameForUser(displayName: String): String {
    if (displayName.trim().endsWith(" · RC")) return displayName.trim()
    RelayCoreModels.specByName(displayName)?.takeIf { " · " in displayName }?.let { return "${it.uiName} · RC" }
    if (displayName.substringAfterLast(" · ", "") in (RelayCoreModels.specs.map { it.channel } + setOf("Kiro", "Kiro正价", "Pro1", "Pro2", "Pro3"))) return "${displayName.trim().substringBeforeLast(" · ")} · RC"
    val raw = displayName.trim().removePrefix("模型：").trim()
    NanfengModelServiceCatalog.presets
        .sortedByDescending { it.displayName.length }
        .firstOrNull { preset -> raw.contains(preset.displayName, ignoreCase = true) }
        ?.let { return it.displayName }
    return raw
        .replaceFirst(Regex("^(?:OpenRouter|Google|通义千问|Qwen 官方直连|DeepSeek 官方直连|智谱 BigModel 官方直连|智谱官方直连)\\s*(?:[·:：]\\s*)"), "")
        .substringBefore(" · ")
        .trim()
        .ifBlank { raw }
}

/**
 * Composer and Assistant message footers use compact names for visual consistency. Pickers,
 * settings and persisted message attribution keep the catalog's full display name.
 */
/** Reading-only answer footer; route names remain frozen in the accounting records. */
fun assistantResponseFooterNameForUser(displayName: String, provider: ProviderId? = null): String {
    val raw = displayName.trim()
    val parts = raw.split(" · ")
    val channels = setOf("Kiro", "Kiro正价", "Pro1", "Pro2", "Pro3", "Gemini")
    val channelIndex = parts.indexOfFirst { it in channels }
    val relay = if (provider != null) provider in RelayCoreModels.providers else channelIndex > 0
    if (provider != null && !relay) {
        val name = if (channelIndex > 0) parts.take(channelIndex).joinToString(" · ") else raw.removeSuffix(" · RC")
        return modelDisplayNameForUser(name).removeSuffix(" · RC")
    }
    return if (relay) {
        val name = if (channelIndex > 0) parts.take(channelIndex).joinToString(" · ") else raw.removeSuffix(" · RC")
        "${relayCoreFooterModelName(name)} · RC"
    } else if (raw.endsWith(" · RC")) "${relayCoreFooterModelName(raw.removeSuffix(" · RC"))} · RC" else modelDisplayNameForUser(raw)
}

private fun relayCoreFooterModelName(name: String): String = if (name == "Astra") "6 Astra" else RelayCoreModels.specs.firstOrNull {
    name == it.modelName || name == it.composerName || name == it.apiId
}?.composerName ?: name

fun composerModelShortNameForUser(displayName: String): String = if (displayName.trim().endsWith(" · RC")) assistantResponseFooterNameForUser(displayName) else RelayCoreModels.specByName(displayName)?.let { "${it.composerName} · RC" }?.takeIf { " · " in displayName } ?: when (modelDisplayNameForUser(displayName)) {
    "Claude Opus 5.5" -> "Opus 5.5"
    "Claude Sonnet 5.5" -> "Sonnet 5.5"
    "Claude Haiku 5.5" -> "Haiku 5.5"
    "GPT-6.1 Sol" -> "6.1 Sol"
    "GPT-6 Luna" -> "6 Luna"
    "Claude Fable 5" -> "Fable 5"
    "Claude Fable 5.1" -> "Fable 5.1"
    "Claude Opus 5" -> "Opus 5"
    "Claude Sonnet 5" -> "Sonnet 5"
    "Claude Haiku 4.5" -> "Haiku 4.5"
    "GPT-5.6 Sol" -> "5.6 Sol"
    "GPT-5.6 Terra" -> "5.6 Terra"
    "GPT-5.6 Luna" -> "5.6 Luna"
    "GPT-6 Astra" -> "6 Astra"
    "Astra" -> "6 Astra"
    "Grok 4.1 Fast" -> "4.1 Fast"
    "Grok 4.6 High" -> "4.6 High"
    "Gemini 3.7 Flash" -> "Gemini 3.7"
    "Gemini 3.8 Flash" -> "Gemini 3.8"
    "Qwen3.7-Plus" -> "Qwen 3.7"
    "Qwen3.8-Max" -> "Qwen 3.8"
    "Qwen3.6 Flash" -> "Qwen 3.6"
    "DeepSeek V4 Pro" -> "DS V4"
    "DeepSeek V4 Flash" -> "DS V4"
    "DeepSeek V4.1 Flash" -> "DS V4.1"
    "GLM-5.3" -> "GLM 5.3"
    "GLM-5.3 Flash" -> "GLM 5.3"
    "GLM-OCR" -> "GLM-OCR"
    else -> modelDisplayNameForUser(displayName)
}

sealed interface SaveModelServiceConfigurationResult {
    data class Saved(val configuration: ModelServiceConfiguration) : SaveModelServiceConfigurationResult
    data class Rejected(val error: AiTaskError) : SaveModelServiceConfigurationResult
}

class LoadModelServiceConfigurationUseCase(
    private val settingsRepository: ModelServiceSettingsRepository,
    private val credentialStore: ProviderCredentialStore,
) {
    fun execute(providerId: ProviderId = ProviderId.OPENROUTER): ModelServiceConfiguration? {
        val provider = NanfengModelServiceCatalog.provider(providerId) ?: return null
        val storedCredential = credentialStore.hasCredential(providerId)
        val persistedSettings = settingsRepository.load(providerId)
        val settings = persistedSettings
        return ModelServiceConfiguration(
            provider = provider,
            settings = settings,
            preset = NanfengModelServiceCatalog.preset(settings.presetId),
            credentialState = if (storedCredential) CredentialState.STORED else CredentialState.MISSING,
        )
    }

    /** Only the visible settings screen may request this transient value. It is never persisted in UI state. */
    fun revealStoredCredential(providerId: ProviderId = ProviderId.OPENROUTER): CharArray? =
        credentialStore.loadCredential(providerId)
}

class SaveModelServiceConfigurationUseCase(
    private val settingsRepository: ModelServiceSettingsRepository,
    private val credentialStore: ProviderCredentialStore,
    private val loadConfiguration: LoadModelServiceConfigurationUseCase,
) {
    private val mutationLock = Any()
    private fun guardedWrite(action: () -> SaveModelServiceConfigurationResult): SaveModelServiceConfigurationResult = try {
        action()
    } catch (cancelled: java.util.concurrent.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SaveModelServiceConfigurationResult.Rejected(AiTaskError.PersistenceConflict)
    }
    fun setEnabled(providerId: ProviderId, enabled: Boolean): SaveModelServiceConfigurationResult = synchronized(mutationLock) {
        guardedWrite {
            if (NanfengModelServiceCatalog.provider(providerId) == null) return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
            settingsRepository.save(settingsRepository.load(providerId).copy(enabled = enabled))
            SaveModelServiceConfigurationResult.Saved(requireNotNull(loadConfiguration.execute(providerId)))
        }
    }
    fun setPreset(providerId: ProviderId, presetId: ModelPresetId): SaveModelServiceConfigurationResult = synchronized(mutationLock) {
        guardedWrite {
            val preset = NanfengModelServiceCatalog.preset(presetId)
            if (preset.usage != ModelPresetUsage.CHAT || NanfengModelServiceCatalog.providerFor(presetId) != providerId) return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
            settingsRepository.save(settingsRepository.load(providerId).copy(presetId = presetId))
            SaveModelServiceConfigurationResult.Saved(requireNotNull(loadConfiguration.execute(providerId)))
        }
    }
    fun saveCredential(providerId: ProviderId, replacement: String?): SaveModelServiceConfigurationResult = synchronized(mutationLock) {
        guardedWrite {
            if (NanfengModelServiceCatalog.provider(providerId) == null) return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
            val credential = replacement?.trim()?.takeIf(String::isNotEmpty) ?: return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialMissing)
            if (credential.length !in 8..4096 || credential.any(Char::isISOControl)) return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialInvalid)
            val chars = credential.toCharArray()
            val stored = try { credentialStore.saveCredential(providerId, chars) } finally { chars.fill('\u0000') }
            if (!stored) return@guardedWrite SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialStorageFailed)
            SaveModelServiceConfigurationResult.Saved(requireNotNull(loadConfiguration.execute(providerId)))
        }
    }
    fun saveDetails(providerId: ProviderId, presetId: ModelPresetId, credential: String?): SaveModelServiceConfigurationResult = synchronized(mutationLock) {
        guardedWrite {
            executeInternal(providerId, settingsRepository.load(providerId).enabled, presetId, credential, false)
        }
    }
    fun execute(
        providerId: ProviderId,
        enabled: Boolean,
        presetId: ModelPresetId,
        replacementCredential: String?,
    ): SaveModelServiceConfigurationResult = synchronized(mutationLock) {
        guardedWrite {
            executeInternal(providerId, enabled, presetId, replacementCredential, true)
        }
    }
    private fun executeInternal(providerId: ProviderId, enabled: Boolean, presetId: ModelPresetId, replacementCredential: String?, requireCredential: Boolean): SaveModelServiceConfigurationResult {
        if (NanfengModelServiceCatalog.provider(providerId) == null) {
            return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
        }
        val selectedPreset = NanfengModelServiceCatalog.preset(presetId)
        if (selectedPreset.usage != ModelPresetUsage.CHAT || NanfengModelServiceCatalog.providerFor(presetId) != providerId) {
            return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
        }
        val credential = replacementCredential?.trim()?.takeIf(String::isNotEmpty)
        if (credential != null) {
            if (credential.length !in 8..4096 || credential.any(Char::isISOControl)) {
                return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialInvalid)
            }
            val chars = credential.toCharArray()
            val stored = try {
                credentialStore.saveCredential(providerId, chars)
            } finally {
                chars.fill('\u0000')
            }
            if (!stored) return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialStorageFailed)
        }
        if (requireCredential && enabled && !credentialStore.hasCredential(providerId)) {
            return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialMissing)
        }
        settingsRepository.save(ProviderSettings(providerId, enabled, presetId))
        val configuration = loadConfiguration.execute(providerId)
            ?: return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
        return SaveModelServiceConfigurationResult.Saved(configuration)
    }
}
