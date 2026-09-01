package com.nanzhufeng.ai.data

import android.content.Context
import com.nanzhufeng.ai.domain.AssistantExperienceSettings
import com.nanzhufeng.ai.domain.AssistantExperienceSettingsRepository
import com.nanzhufeng.ai.domain.ConversationStyle

/** App-private preferences for explicitly user-authored model personalization and Memory use. */
class AndroidAssistantExperienceSettingsRepository(context: Context) : AssistantExperienceSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): AssistantExperienceSettings = AssistantExperienceSettings(
        personalizationEnabled = preferences.getBoolean(PERSONALIZATION_ENABLED, false),
        displayName = preferences.getString(DISPLAY_NAME, "").orEmpty(),
        occupation = preferences.getString(OCCUPATION, "").orEmpty(),
        interests = preferences.getString(INTERESTS, "").orEmpty(),
        customInstructions = preferences.getString(CUSTOM_INSTRUCTIONS, "").orEmpty(),
        // Preserve the existing normal-chat behavior on upgrade: active Memory was already
        // locally ranked for ordinary chat before this explicit setting was introduced.
        memoryRetrievalEnabled = preferences.getBoolean(MEMORY_RETRIEVAL_ENABLED, true),
        librarySearchEnabled = preferences.getBoolean(LIBRARY_SEARCH_ENABLED, true),
        autoHistoryKnowledgeEnabled = preferences.getBoolean(AUTO_HISTORY_KNOWLEDGE_ENABLED, false),
        webSearchEnabled = preferences.getBoolean(WEB_SEARCH_ENABLED, true),
        conversationStyle = ConversationStyle.fromPersistedId(preferences.getString(CONVERSATION_STYLE, null)),
    )

    override fun save(settings: AssistantExperienceSettings): AssistantExperienceSettings {
        check(
            preferences.edit()
                .putBoolean(PERSONALIZATION_ENABLED, settings.personalizationEnabled)
                .putString(DISPLAY_NAME, settings.displayName)
                .putString(OCCUPATION, settings.occupation)
                .putString(INTERESTS, settings.interests)
                .putString(CUSTOM_INSTRUCTIONS, settings.customInstructions)
                .putBoolean(MEMORY_RETRIEVAL_ENABLED, settings.memoryRetrievalEnabled)
                .putBoolean(LIBRARY_SEARCH_ENABLED, settings.librarySearchEnabled)
                .putBoolean(AUTO_HISTORY_KNOWLEDGE_ENABLED, settings.autoHistoryKnowledgeEnabled)
                .putBoolean(WEB_SEARCH_ENABLED, settings.webSearchEnabled)
                .putString(CONVERSATION_STYLE, settings.conversationStyle.persistedId)
                .commit(),
        ) { "个性化与记忆设置无法写入本机。" }
        return load()
    }

    private companion object {
        const val FILE = "assistant_experience_settings_v1"
        const val PERSONALIZATION_ENABLED = "personalization_enabled"
        const val DISPLAY_NAME = "display_name"
        const val OCCUPATION = "occupation"
        const val INTERESTS = "interests"
        const val CUSTOM_INSTRUCTIONS = "custom_instructions"
        const val MEMORY_RETRIEVAL_ENABLED = "memory_retrieval_enabled"
        const val LIBRARY_SEARCH_ENABLED = "library_search_enabled"
        const val AUTO_HISTORY_KNOWLEDGE_ENABLED = "auto_history_knowledge_enabled"
        const val WEB_SEARCH_ENABLED = "web_search_enabled"
        const val CONVERSATION_STYLE = "conversation_style"
    }
}
