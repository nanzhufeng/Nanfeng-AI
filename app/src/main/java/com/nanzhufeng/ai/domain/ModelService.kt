package com.nanzhufeng.ai.domain

object NanfengModelServiceCatalog {
    val openRouter = ProviderDescriptor(
        id = ProviderId.OPENROUTER,
        displayName = "OpenRouter",
        fixedEndpoint = "https://openrouter.ai/api/v1",
    )

    val presets = listOf(
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_FABLE_5,
            displayName = "Claude Fable 5",
            description = "应对最棘手的复杂任务。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_OPUS_5,
            displayName = "Claude Opus 5",
            description = "适合复杂任务与深度推理。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_SONNET_5,
            displayName = "Claude Sonnet 5",
            description = "高效处理日常工作。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.CLAUDE_HAIKU_4_5,
            displayName = "Claude Haiku 4.5",
            description = "快速获得简洁答案。",
            modelFamilyHint = "Anthropic",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_SOL,
            displayName = "GPT-5.6 Sol",
            description = "前沿能力，适合专业复杂任务。",
            modelFamilyHint = "OpenAI",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_TERRA,
            displayName = "GPT-5.6 Terra",
            description = "能力与成本更均衡。",
            modelFamilyHint = "OpenAI",
        ),
        ModelPresetDescriptor(
            id = ModelPresetId.GPT_5_6_LUNA,
            displayName = "GPT-5.6 Luna",
            description = "适合高频、轻量任务。",
            modelFamilyHint = "OpenAI",
        ),
    )

    fun provider(providerId: ProviderId): ProviderDescriptor? = when (providerId) {
        ProviderId.OPENROUTER -> openRouter
        ProviderId.MOCK -> null
    }

    fun preset(presetId: ModelPresetId): ModelPresetDescriptor =
        presets.firstOrNull { it.id == presetId } ?: presets.first()
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
        val settings = settingsRepository.load(providerId)
        return ModelServiceConfiguration(
            provider = provider,
            settings = settings,
            preset = NanfengModelServiceCatalog.preset(settings.presetId),
            credentialState = if (credentialStore.hasCredential(providerId)) CredentialState.STORED else CredentialState.MISSING,
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
    fun execute(
        providerId: ProviderId,
        enabled: Boolean,
        presetId: ModelPresetId,
        replacementCredential: String?,
    ): SaveModelServiceConfigurationResult {
        if (NanfengModelServiceCatalog.provider(providerId) == null) {
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
        if (enabled && !credentialStore.hasCredential(providerId)) {
            return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderCredentialMissing)
        }
        runCatching { settingsRepository.save(ProviderSettings(providerId, enabled, presetId)) }
            .getOrElse { return SaveModelServiceConfigurationResult.Rejected(AiTaskError.PersistenceConflict) }
        val configuration = loadConfiguration.execute(providerId)
            ?: return SaveModelServiceConfigurationResult.Rejected(AiTaskError.ProviderConfigurationInvalid)
        return SaveModelServiceConfigurationResult.Saved(configuration)
    }
}
