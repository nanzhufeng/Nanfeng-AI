package com.nanzhufeng.ai.data.local

import android.content.Context
import com.nanzhufeng.ai.domain.ConversationListScope
import com.nanzhufeng.ai.domain.LocalSearchHistoryStore
import java.util.Locale

/** P6-F2-A keeps only normalized query strings in a private preference, never result payloads. */
class AndroidLocalSearchHistoryStore(context: Context) : LocalSearchHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences("local-search-history-v1", Context.MODE_PRIVATE)
    private fun key(scope: ConversationListScope) = "conversation-search:${scope.name}"
    override fun recent(scope: ConversationListScope): List<String> = preferences.getString(key(scope), "").orEmpty().split('\n').filter { it.isNotBlank() }.take(10)
    override fun record(query: String, scope: ConversationListScope) {
        val normalized = normalize(query) ?: return
        preferences.edit().putString(key(scope), (listOf(normalized) + recent(scope).filterNot { it == normalized }).take(10).joinToString("\n")).apply()
    }
    override fun remove(query: String, scope: ConversationListScope) {
        val normalized = normalize(query) ?: return
        preferences.edit().putString(key(scope), recent(scope).filterNot { it == normalized }.joinToString("\n")).apply()
    }
    override fun clear(scope: ConversationListScope) { preferences.edit().remove(key(scope)).apply() }
    private fun normalize(query: String): String? = query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
}
