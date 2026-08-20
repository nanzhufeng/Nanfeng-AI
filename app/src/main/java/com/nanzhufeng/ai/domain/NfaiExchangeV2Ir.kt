package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * P6 v2's side-effect-free owner-fidelity IR guard. It deliberately does not stage a package,
 * access Room, or register a user entry: the v2 mapper/import transaction is a later increment.
 */
object NfaiExchangeV2Ir {
    private val forbidden = Regex("(?i)(credential|api[_-]?key|authorization|provider[_-]?(raw|payload)|runtime[_-]?chunk|diagnostic|route[_-]?pref|\\buri\\b|\\bpath\\b)")
    private val stableId = Regex("[a-z0-9][a-z0-9_-]{1,63}")
    private val sha256 = Regex("[a-f0-9]{64}")

    data class Verified(val semanticHash: String, val hasHighSensitiveData: Boolean)

    fun validate(exchange: JSONObject): Verified {
        exchange.requireExact("format", "version", "export", "projects", "conversations", "knowledge", "memory", "relations", "settings")
        require(exchange.getString("format") == "nfai.exchange" && exchange.getInt("version") == 2) { "不支持的 v2 交换 IR。" }
        rejectUnsafe(exchange)
        val export = exchange.getJSONObject("export")
        export.requireExact("id", "createdAt", "origin", "semanticHash", "sensitivity")
        requireId(export.getString("id")); requireHash(export.getString("semanticHash"))
        require(export.getJSONObject("origin").getString("platform") in setOf("ANDROID", "DESKTOP")) { "来源平台无效。" }
        require(export.getString("semanticHash") == semanticHash(exchange)) { "v2 semantic hash 不一致。" }

        val projects = exchange.getJSONArray("projects").objects("projects")
        val conversations = exchange.getJSONArray("conversations").objects("conversations")
        val knowledge = exchange.getJSONArray("knowledge").objects("knowledge")
        val memory = exchange.getJSONArray("memory").objects("memory")
        val relations = exchange.getJSONArray("relations").objects("relations")
        val ids = mutableSetOf<String>()
        listOf(projects, conversations, knowledge, memory, relations).flatten().forEach { item ->
            val id = item.getString("id"); requireId(id); require(ids.add(id)) { "跨 owner stable ID 重复。" }
        }
        projects.forEach { project ->
            project.requireExact("id", "title", "description", "appearance", "pinned", "archived", "createdAt", "updatedAt", "schemaVersion", "instructionHistory")
            project.getJSONObject("appearance").requireExact("color", "icon")
            val revisions = project.getJSONArray("instructionHistory").objects("项目指令历史")
            require(revisions.map { it.getString("id") }.distinct().size == revisions.size) { "项目指令 revision 重复。" }
            revisions.forEach { revision ->
                revision.requireExact("id", "revision", "content", "source", "contentHash", "createdAt", "schemaVersion")
                require(revision.getString("source") == "USER" && hash(revision.getString("content")) == revision.getString("contentHash")) { "项目指令历史无效。" }
            }
        }
        conversations.forEach { conversation ->
            conversation.requireExact("id", "projectId", "title", "currentLeafId", "pinned", "archived", "revision", "createdAt", "updatedAt", "autoTitlePending", "surface", "schemaVersion", "settings", "messages")
            require(conversation.isNull("projectId") || conversation.getString("projectId") in ids) { "会话项目不存在。" }
            require(conversation.getString("surface") == "CHAT") { "WORK 会话不能进入 v2 交换。" }
            val settings = conversation.getJSONObject("settings")
            settings.requireExact("defaultProviderId", "defaultModelId", "harnessId", "harnessVersion", "contextPolicyVersion", "memorySources", "schemaVersion")
            settings.getJSONArray("memorySources").objects("会话记忆来源").forEach { source ->
                source.requireExact("memoryId", "sourceKind", "sourceVersion")
                require(memory.any { it.getString("id") == source.getString("memoryId") }) { "会话记忆来源不在选择闭包中。" }
            }
        }
        knowledge.forEach { item ->
            item.requireExact("id", "title", "body", "sourceEvidence", "provenance", "attachments", "status", "scope", "projectId", "tags", "contentHash", "createdAt", "updatedAt", "schemaVersion", "history")
            require(item.isNull("projectId") || item.getString("projectId") in ids) { "知识项目不存在。" }; requireHash(item.getString("contentHash"))
            item.getJSONArray("history"); item.getJSONArray("sourceEvidence"); item.getJSONArray("attachments")
        }
        memory.forEach { item ->
            item.requireExact("id", "title", "body", "scope", "scopeId", "source", "sourceStableId", "sourceSummary", "status", "contentHash", "conceptHash", "createdAt", "updatedAt", "lastConfirmedAt", "deletedAt", "schemaVersion", "history")
            require(item.isNull("scopeId") || item.getString("scopeId") in ids) { "记忆 scope 不在选择闭包中。" }; requireHash(item.getString("contentHash")); requireHash(item.getString("conceptHash")); item.getJSONArray("history")
        }
        relations.forEach { item ->
            item.requireExact("id", "type", "fromId", "toId", "scope", "projectId", "status", "createdAt", "updatedAt", "createdByIntentId", "latestIntentId", "suggestionSource", "history")
            require(knowledge.any { it.getString("id") == item.getString("fromId") } && knowledge.any { it.getString("id") == item.getString("toId") }) { "关系端点不在选择闭包中。" }
            require(item.isNull("projectId") || projects.any { it.getString("id") == item.getString("projectId") }) { "关系项目不存在。" }; item.getJSONArray("history")
        }
        exchange.getJSONObject("settings").requireExact("uiLanguage", "theme")
        return Verified(export.getString("semanticHash"), canonical(exchange).contains("\"HIGH_SENSITIVE\""))
    }

    fun semanticHash(exchange: JSONObject): String { val copy = JSONObject(canonical(exchange)); copy.getJSONObject("export").remove("semanticHash"); return hash(canonical(copy)) }
    private fun rejectUnsafe(value: Any?) { when (value) { is JSONObject -> value.keys().asSequence().toList().forEach { key -> require(key != "sourceReference" && !forbidden.containsMatchIn(key)) { "禁止的 v2 字段。" }; rejectUnsafe(value.get(key)) }; is JSONArray -> (0 until value.length()).forEach { rejectUnsafe(value.get(it)) } } }
    private fun JSONObject.requireExact(vararg expected: String) { require(keys().asSequence().toSet() == expected.toSet()) { "字段缺失或未知。" } }
    private fun JSONArray.objects(name: String): List<JSONObject> = List(length()) { index -> get(index) as? JSONObject ?: error("$name 必须是对象数组。") }
    private fun requireId(value: String) { require(stableId.matches(value)) { "stable ID 无效。" } }
    private fun requireHash(value: String) { require(sha256.matches(value)) { "SHA-256 无效。" } }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun canonical(value: Any?): String = when (value) { null, JSONObject.NULL -> "null"; is String -> quote(value); is Boolean, is Int, is Long -> value.toString(); is Number -> require(value.toDouble().isFinite() && value.toLong().toDouble() == value.toDouble()) { "仅允许安全整数。" }.let { value.toLong().toString() }; is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { "${quote(it)}:${canonical(value.get(it))}" }; is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }; else -> error("不支持的 JSON 值。") }
    private fun quote(value: String): String = buildString { append('"'); value.forEach { char -> when (char) { '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char) } }; append('"') }
}
