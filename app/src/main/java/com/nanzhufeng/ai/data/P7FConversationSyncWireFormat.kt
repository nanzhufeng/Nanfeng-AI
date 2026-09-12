package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRevision
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict, text-only reader for the selected-conversation cloud boundary.
 *
 * Current Android documents use `nodes`; early Desktop documents used `messages` and may also
 * carry safe-settings/reminder records. Android restores only the one conversation record and
 * never imports those adjacent records into unrelated local owners.
 */
object P7FConversationSyncWireFormat {
    fun decode(snapshot: NfaiSyncPreparedSnapshot): ConversationSnapshot {
        require(snapshot.appId == "com.nanzhufeng.ai") { "云端文档不属于本应用。" }
        require(snapshot.documentId.startsWith("conversation-") && snapshot.documentId.length <= 64) { "云端文档标识无效。" }
        require(snapshot.records.size in 1..3) { "云端恢复内容超出单对话范围。" }
        require(snapshot.records.all { it.classification == "NORMAL" }) { "云端恢复包含不允许的数据分类。" }
        val record = snapshot.records.singleOrNull { it.kind == "conversation" }
            ?: error("云端恢复缺少对话。")
        require(snapshot.records.all { it === record || it.kind in setOf("safe_settings", "relation") }) { "云端恢复包含不支持的记录。" }
        val value = JSONObject(record.contentJson)
        return if (value.keys().asSequence().toSet().contains("nodes")) {
            decodeCurrent(record.id, record.revision, value)
        } else {
            decodeDesktopLegacy(record.id, record.revision, value)
        }
    }

    private fun decodeCurrent(id: String, semanticRevision: Long, value: JSONObject): ConversationSnapshot {
        value.requireKeys("title", "currentLeafMessageId", "createdAtEpochMs", "updatedAtEpochMs", "surface", "nodes")
        val conversationId = ConversationId(id.requireNonBlank("云端对话标识无效。"))
        val createdAt = Instant.ofEpochMilli(value.getLong("createdAtEpochMs"))
        val updatedAt = Instant.ofEpochMilli(value.getLong("updatedAtEpochMs"))
        val nodes = value.getJSONArray("nodes").decodeNodes(conversationId)
        return ConversationSnapshot(
            conversation = Conversation(
                id = conversationId,
                title = value.getString("title").requireNonBlank("云端对话标题无效。"),
                currentLeafMessageId = value.nullableString("currentLeafMessageId")?.let(::MessageNodeId),
                createdAt = createdAt,
                updatedAt = updatedAt,
                revision = semanticRevision.coerceAtLeast(1),
                surface = enumValueOf(value.getString("surface")),
            ),
            nodes = nodes,
            draft = ConversationDraft(updatedAt = updatedAt),
        )
    }

    private fun decodeDesktopLegacy(id: String, semanticRevision: Long, value: JSONObject): ConversationSnapshot {
        value.requireKeys("title", "createdAt", "updatedAt", "currentLeafId", "messages")
        val conversationId = ConversationId(id.requireNonBlank("云端对话标识无效。"))
        val createdAt = Instant.parse(value.getString("createdAt"))
        val updatedAt = Instant.parse(value.getString("updatedAt"))
        val messages = value.getJSONArray("messages")
        require(messages.length() in 1..1_000) { "云端消息数量无效。" }
        val nodes = buildList {
            repeat(messages.length()) { index ->
                val item = messages.getJSONObject(index)
                item.requireKeys("id", "parentId", "ordinal", "role", "createdAt", "revision", "delivery", "blocks")
                val blocks = item.getJSONArray("blocks")
                require(blocks.length() in 1..64) { "云端消息文本无效。" }
                val content = buildList {
                    repeat(blocks.length()) { position ->
                        val block = blocks.getJSONObject(position)
                        block.requireKeys("kind", "text")
                        require(block.getString("kind") == "TEXT") { "云端消息包含非文本内容。" }
                        add(ContentBlock.Text(block.getString("text").requireNonBlank("云端消息文本为空。")))
                    }
                }
                add(MessageNode(
                    id = MessageNodeId(item.getString("id").requireNonBlank("云端消息标识无效。")),
                    conversationId = conversationId,
                    parentMessageId = item.nullableString("parentId")?.let(::MessageNodeId),
                    siblingPosition = item.getInt("ordinal"),
                    role = enumValueOf(item.getString("role")),
                    content = content,
                    createdAt = Instant.parse(item.getString("createdAt")),
                    deliveryState = enumValueOf(item.getString("delivery")),
                    revision = MessageRevision(item.getInt("revision")),
                ))
            }
        }
        val leaf = value.nullableString("currentLeafId") ?: nodes.lastOrNull()?.id?.value
            ?: error("云端对话没有可恢复消息。")
        return ConversationSnapshot(
            conversation = Conversation(
                id = conversationId,
                title = value.getString("title").requireNonBlank("云端对话标题无效。"),
                currentLeafMessageId = MessageNodeId(leaf),
                createdAt = createdAt,
                updatedAt = updatedAt,
                revision = semanticRevision.coerceAtLeast(1),
                surface = ConversationSurface.CHAT,
            ),
            nodes = nodes,
            draft = ConversationDraft(updatedAt = updatedAt),
        )
    }

    private fun JSONArray.decodeNodes(conversationId: ConversationId): List<MessageNode> {
        require(length() in 1..1_000) { "云端消息数量无效。" }
        return buildList {
            repeat(length()) { index ->
                val item = getJSONObject(index)
                item.requireKeys("id", "parentMessageId", "siblingPosition", "role", "createdAtEpochMs", "deliveryState", "revision", "revisesMessageId", "text")
                val text = item.getJSONArray("text")
                val delivery = enumValueOf<MessageDeliveryState>(item.getString("deliveryState"))
                val content = buildList {
                    repeat(text.length()) { position -> add(ContentBlock.Text(text.getString(position).requireNonBlank("云端消息文本为空。"))) }
                }
                require(content.isNotEmpty() || delivery != MessageDeliveryState.COMPLETE) { "完整云端消息不能没有文本。" }
                val revision = item.getInt("revision")
                val revises = item.nullableString("revisesMessageId")?.let(::MessageNodeId)
                add(MessageNode(
                    id = MessageNodeId(item.getString("id").requireNonBlank("云端消息标识无效。")),
                    conversationId = conversationId,
                    parentMessageId = item.nullableString("parentMessageId")?.let(::MessageNodeId),
                    siblingPosition = item.getInt("siblingPosition"),
                    role = enumValueOf(item.getString("role")),
                    content = content,
                    createdAt = Instant.ofEpochMilli(item.getLong("createdAtEpochMs")),
                    deliveryState = delivery,
                    revision = MessageRevision(revision, revises),
                ))
            }
        }
    }

    private fun JSONObject.requireKeys(vararg names: String) {
        require(keys().asSequence().toSet() == names.toSet()) { "云端数据字段无效。" }
    }

    private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
    private fun String.requireNonBlank(message: String): String = also { require(it.isNotBlank() && it.length <= 4_096) { message } }
}
