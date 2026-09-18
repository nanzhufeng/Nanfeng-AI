package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.ContentBlock
import com.nanzhufeng.ai.domain.Conversation
import com.nanzhufeng.ai.domain.ConversationDraft
import com.nanzhufeng.ai.domain.ConversationId
import com.nanzhufeng.ai.domain.ConversationSnapshot
import com.nanzhufeng.ai.domain.ConversationSurface
import com.nanzhufeng.ai.domain.CloudResponseModelUsage
import com.nanzhufeng.ai.domain.ConversationCostSource
import com.nanzhufeng.ai.domain.MessageDeliveryState
import com.nanzhufeng.ai.domain.MessageNode
import com.nanzhufeng.ai.domain.MessageNodeId
import com.nanzhufeng.ai.domain.MessageRevision
import com.nanzhufeng.ai.domain.MessageRole
import com.nanzhufeng.ai.domain.NfaiSyncPreparedSnapshot
import com.nanzhufeng.ai.domain.ProviderCost
import com.nanzhufeng.ai.domain.ProviderUsage
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.titleRevision(): Long? = if (!has("titleRevision") || isNull("titleRevision")) null else {
    val raw = get("titleRevision")
    require(raw is Int || raw is Long) { "标题版本必须是正整数。" }
    getLong("titleRevision").also { require(it > 0) { "标题版本必须是正整数。" } }
}

/**
 * Strict, text-only reader for the selected-conversation cloud boundary.
 *
 * Current Android documents use `nodes`; early Desktop documents used `messages` and may also
 * carry safe-settings/reminder records. Android restores only the one conversation record and
 * never imports those adjacent records into unrelated local owners.
 */
object P7FConversationSyncWireFormat {
    fun decode(snapshot: NfaiSyncPreparedSnapshot): ConversationSnapshot = decodeWithModelUsage(snapshot).snapshot

    /** The wire decoder keeps portable accounting separate from Android's local Provider route. */
    fun decodeWithModelUsage(snapshot: NfaiSyncPreparedSnapshot): P7FDecodedConversation {
        require(snapshot.appId == "com.nanzhufeng.ai") { "云端文档不属于本应用。" }
        require(snapshot.documentId.startsWith("conversation-") && snapshot.documentId.length <= 64) { "云端文档标识无效。" }
        require(snapshot.records.size in 1..3) { "云端恢复内容超出单对话范围。" }
        require(snapshot.records.all { it.classification == "NORMAL" }) { "云端恢复包含不允许的数据分类。" }
        val record = snapshot.records.singleOrNull { it.kind == "conversation" }
            ?: error("云端恢复缺少对话。")
        require(snapshot.records.all { it === record || it.kind in setOf("safe_settings", "relation") }) { "云端恢复包含不支持的记录。" }
        val value = JSONObject(record.contentJson)
        val restored = if (value.keys().asSequence().toSet().contains("nodes")) {
            decodeCurrent(record.id, record.revision, value)
        } else {
            decodeDesktopLegacy(record.id, record.revision, value)
        }
        val nodesById = restored.nodes.associateBy { it.id }
        val accounting = value.decodeModelUsage().mapValues { (id, usage) ->
            val at = nodesById[id]?.createdAt
            // Legacy Desktop displayed a versioned token-based estimate without
            // persisting chargeMicros. Recover that same historical projection once;
            // never relabel it as a Provider settlement or use today's timestamp.
            val estimate = if (usage.cost.totalMicros == null && at != null)
                com.nanzhufeng.ai.domain.ConversationCostEstimator.estimate(usage.modelId, usage.usage, at) else null
            if (estimate == null) usage else usage.copy(cost = estimate, costSource = ConversationCostSource.LOCAL_ESTIMATE)
        }
        return P7FDecodedConversation(restored, accounting)
    }

    private fun decodeCurrent(id: String, semanticRevision: Long, value: JSONObject): ConversationSnapshot {
        value.requireKeys(
            required = setOf("title", "currentLeafMessageId", "createdAtEpochMs", "updatedAtEpochMs", "surface", "nodes"),
            optional = setOf("archivedAtEpochMs", "pinnedAtEpochMs", "favoritedAtEpochMs", "titleRevision"),
        )
        val conversationId = ConversationId(id.requireNonBlank("云端对话标识无效。"))
        val createdAt = Instant.ofEpochMilli(value.getLong("createdAtEpochMs"))
        val updatedAt = Instant.ofEpochMilli(value.getLong("updatedAtEpochMs"))
        val nodes = value.getJSONArray("nodes").decodeNodes(conversationId)
        val currentLeaf = value.nullableString("currentLeafMessageId")?.let(::MessageNodeId)
        requirePortableTree(nodes, currentLeaf)
        return ConversationSnapshot(
            conversation = Conversation(
                id = conversationId,
                title = value.getString("title").requireNonBlank("云端对话标题无效。"),
                titleRevision = value.titleRevision(),
                currentLeafMessageId = currentLeaf,
                createdAt = createdAt,
                updatedAt = updatedAt,
                revision = semanticRevision.coerceAtLeast(1),
                surface = enumValueOf(value.getString("surface")),
                archivedAt = value.nullableEpochMs("archivedAtEpochMs"),
                pinnedAt = value.nullableEpochMs("pinnedAtEpochMs"),
                favoritedAt = value.nullableEpochMs("favoritedAtEpochMs"),
            ),
            nodes = nodes,
            draft = ConversationDraft(updatedAt = updatedAt),
        )
    }

    private fun decodeDesktopLegacy(id: String, semanticRevision: Long, value: JSONObject): ConversationSnapshot {
        value.requireKeys(
            required = setOf("title", "createdAt", "updatedAt", "currentLeafId", "messages"),
            optional = setOf("pinned", "archived", "favorited", "titleRevision", "surface"),
        )
        val conversationId = ConversationId(id.requireNonBlank("云端对话标识无效。"))
        val createdAt = Instant.parse(value.getString("createdAt"))
        val updatedAt = Instant.parse(value.getString("updatedAt"))
        val messages = value.getJSONArray("messages")
        require(messages.length() in 1..1_000) { "云端消息数量无效。" }
        // Older Desktop exports could retain the same message record twice
        // after an interrupted write.  A byte-for-byte identical duplicate is
        // not a second turn; keep one deterministic copy.  Different records
        // claiming one ID remain a hard refusal instead of guessing content.
        val uniqueMessages = buildList {
            val byId = linkedMapOf<String, String>()
            repeat(messages.length()) { index ->
                val item = messages.getJSONObject(index)
                val id = item.getString("id").requireNonBlank("云端消息标识无效。")
                val canonical = item.toString()
                val previous = byId.putIfAbsent(id, canonical)
                if (previous == null) add(item) else require(previous == canonical) { "云端消息 ID 冲突。" }
            }
        }
        val retainedMessageIds = mutableSetOf<String>()
        // Desktop's historical exchange permits two independent root branches
        // to both carry ordinal 0. Android's durable tree correctly requires
        // sibling positions to be unique, so normalize only the portable
        // position at this boundary. The input order remains stable and no
        // message, title, or text is dropped.
        val nextSiblingPositionByParent = mutableMapOf<String?, Int>()
        val nodes = mutableListOf<MessageNode>()
        val portableParentBySourceId = mutableMapOf<String, MessageNodeId?>()
        var pending = uniqueMessages.toList()
        while (pending.isNotEmpty()) {
            val deferred = mutableListOf<JSONObject>()
            var progressed = false
            pending.forEach { item ->
                item.requireKeys(
                    required = setOf("id", "parentId", "ordinal", "role", "createdAt", "revision", "delivery", "blocks"),
                    optional = setOf("modelUsage"),
                )
                val messageId = item.getString("id").requireNonBlank("云端消息标识无效。")
                val sourceParentId = item.nullableString("parentId")
                if (sourceParentId != null && sourceParentId !in portableParentBySourceId) {
                    deferred += item
                    return@forEach
                }
                progressed = true
                val parentMessageId = sourceParentId?.let(portableParentBySourceId::get)
                val blocks = item.getJSONArray("blocks")
                require(blocks.length() in 1..64) { "云端消息文本无效。" }
                val content = buildList {
                    repeat(blocks.length()) { position ->
                        val block = blocks.getJSONObject(position)
                        when (block.getString("kind")) {
                            "TEXT" -> {
                                block.requireKeys("kind", "text")
                                // Older Desktop runtime placeholders can retain an empty TEXT
                                // block. It has no user-visible Android representation, so omit
                                // it with the same read-only compatibility treatment as a tool
                                // block instead of rejecting the whole cloud conversation.
                                block.getString("text").takeIf { it.isNotBlank() }
                                    ?.let { add(ContentBlock.Text(it)) }
                            }
                            // Desktop deliberately sends only attachment metadata, never its
                            // local bytes, path or private storage key.  Import the adjacent
                            // text instead of rejecting the whole cloud conversation.  An
                            // attachment-only message remains unsupported below because Android
                            // cannot truthfully reconstruct an attachable local asset from this
                            // metadata-only boundary.
                            "ASSET_REF" -> block.requireKeys("kind", "asset")
                            // Tool output is Desktop-local runtime evidence.  Old direct
                            // records can contain it; omit it from Android's text-only view
                            // instead of rejecting every otherwise recoverable conversation.
                            "TOOL_RESULT" -> Unit
                            // Unknown non-text runtime blocks are local-only. They
                            // must not erase a later completed text descendant.
                            else -> Unit
                        }
                    }
                }
                if (content.isEmpty()) {
                    portableParentBySourceId[messageId] = parentMessageId
                    return@forEach
                }
                val normalizedSiblingPosition = maxOf(
                    item.getInt("ordinal").coerceAtLeast(0),
                    nextSiblingPositionByParent[parentMessageId?.value] ?: 0,
                )
                nextSiblingPositionByParent[parentMessageId?.value] = normalizedSiblingPosition + 1
                val node = MessageNode(
                    id = MessageNodeId(messageId),
                    conversationId = conversationId,
                    parentMessageId = parentMessageId,
                    siblingPosition = normalizedSiblingPosition,
                    role = desktopMessageRole(item.getString("role")),
                    content = content,
                    createdAt = Instant.parse(item.getString("createdAt")),
                    deliveryState = desktopDeliveryState(item.getString("delivery")),
                    revision = MessageRevision(item.getInt("revision")),
                )
                nodes += node
                retainedMessageIds += messageId
                portableParentBySourceId[messageId] = node.id
            }
            if (!progressed) break
            pending = deferred
        }
        val leaf = value.nullableString("currentLeafId")
            ?.takeIf { it in retainedMessageIds }
            ?: nodes.lastOrNull()?.id?.value
            ?: error("云端对话没有可恢复消息。")
        return ConversationSnapshot(
            conversation = Conversation(
                id = conversationId,
                title = value.getString("title").requireNonBlank("云端对话标题无效。"),
                titleRevision = value.titleRevision(),
                currentLeafMessageId = MessageNodeId(leaf),
                createdAt = createdAt,
                updatedAt = updatedAt,
                revision = semanticRevision.coerceAtLeast(1),
                surface = if (value.has("surface")) enumValueOf(value.getString("surface")) else ConversationSurface.CHAT,
                // `pinned` belongs to the source device's local drawer. Cloud-list pins are
                // carried by the separate account presentation document and must never turn
                // into Android's durable Conversation.pinnedAt during a cloud read.
                pinnedAt = null,
                archivedAt = updatedAt.takeIf { value.optBoolean("archived", false) },
                favoritedAt = updatedAt.takeIf { value.optBoolean("favorited", false) },
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
                item.requireKeys(
                    required = setOf("id", "parentMessageId", "siblingPosition", "role", "createdAtEpochMs", "deliveryState", "revision", "revisesMessageId", "text"),
                    optional = setOf("modelUsage"),
                )
                val text = item.getJSONArray("text")
                val delivery = enumValueOf<MessageDeliveryState>(item.getString("deliveryState"))
                val content = buildList {
                    // Message bodies are bounded by the envelope byte budget, not the
                    // identifier/title limit. Never truncate a completed long answer.
                    repeat(text.length()) { position ->
                        val body = text.getString(position)
                        require(body.isNotBlank()) { "云端消息文本为空。" }
                        add(ContentBlock.Text(body))
                    }
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

    /** Reject malformed parent graphs before a verified cloud record can reach local storage. */
    private fun requirePortableTree(nodes: List<MessageNode>, currentLeaf: MessageNodeId?) {
        val byId = nodes.associateBy(MessageNode::id)
        require(nodes.isNotEmpty() && byId.size == nodes.size) { "云端消息树标识无效。" }
        require(currentLeaf != null && currentLeaf in byId) { "云端消息树叶节点无效。" }
        nodes.forEach { node ->
            require(node.parentMessageId == null || node.parentMessageId in byId) { "云端消息树父节点无效。" }
            val ancestors = mutableSetOf<MessageNodeId>()
            var cursor: MessageNode? = node
            while (cursor?.parentMessageId != null) {
                require(ancestors.add(cursor.id)) { "云端消息树存在循环。" }
                cursor = byId[cursor.parentMessageId]
            }
        }
    }

    private fun JSONObject.requireKeys(vararg names: String) = requireKeys(names.toSet())

    private fun JSONObject.requireKeys(required: Set<String>, optional: Set<String> = emptySet()) {
        val actual = keys().asSequence().toSet()
        require(actual.containsAll(required) && actual.all { it in required || it in optional }) { "云端数据字段无效。" }
    }

    private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONObject.nullableEpochMs(name: String): Instant? = when {
        !has(name) || isNull(name) -> null
        else -> Instant.ofEpochMilli(getLong(name))
    }
    private fun String.requireNonBlank(message: String): String = also { require(it.isNotBlank() && it.length <= 4_096) { message } }

    private fun JSONObject.decodeModelUsage(): Map<MessageNodeId, CloudResponseModelUsage> {
        val isCurrent = has("nodes")
        val messages = getJSONArray(if (isCurrent) "nodes" else "messages")
        return buildMap {
            repeat(messages.length()) { index ->
                val message = messages.getJSONObject(index)
                if (!message.has("modelUsage")) return@repeat
                val messageId = MessageNodeId(message.getString("id").requireNonBlank("云端消息标识无效。"))
                val role = if (isCurrent) enumValueOf<MessageRole>(message.getString("role")) else desktopMessageRole(message.getString("role"))
                require(role == MessageRole.ASSISTANT) { "云端模型用量只能属于助手回答。" }
                val usage = message.getJSONObject("modelUsage")
                usage.requireKeys(
                    required = setOf("modelId", "modelDisplayName", "inputTokens", "outputTokens", "totalTokens", "cachedInputTokens", "reasoningTokens", "costPriceVersion", "costCurrencyCode", "costTotalMicros", "costSource"),
                )
                val decoded = CloudResponseModelUsage(
                    assistantMessageId = messageId,
                    modelId = usage.getString("modelId").requireNonBlank("云端模型标识无效。"),
                    modelDisplayName = usage.getString("modelDisplayName").requireNonBlank("云端模型名称无效。"),
                    usage = ProviderUsage(
                        inputTokens = usage.nullableLong("inputTokens"),
                        outputTokens = usage.nullableLong("outputTokens"),
                        totalTokens = usage.nullableLong("totalTokens"),
                        cachedInputTokens = usage.nullableLong("cachedInputTokens"),
                        reasoningTokens = usage.nullableLong("reasoningTokens"),
                    ),
                    cost = ProviderCost(
                        priceVersion = usage.nullableString("costPriceVersion"),
                        currencyCode = usage.nullableString("costCurrencyCode"),
                        totalMicros = usage.nullableLong("costTotalMicros"),
                    ),
                    costSource = usage.nullableString("costSource")?.let(ConversationCostSource::valueOf),
                )
                val previous = put(messageId, decoded)
                require(previous == null || previous == decoded) { "云端消息模型用量冲突。" }
            }
        }
    }

    /** Desktop persists role names in lower case; Android's domain enum is upper case. */
    private fun desktopMessageRole(value: String): MessageRole = when (value.lowercase(java.util.Locale.ROOT)) {
        "system" -> MessageRole.SYSTEM
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "tool" -> MessageRole.TOOL
        else -> error("云端消息角色无效。")
    }

    /** Desktop's retired UNKNOWN marker has no Android peer; it is a failed attempt, not text. */
    private fun desktopDeliveryState(value: String): MessageDeliveryState = when (value.uppercase(java.util.Locale.ROOT)) {
        "UNKNOWN" -> MessageDeliveryState.FAILED
        else -> enumValueOf(value.uppercase(java.util.Locale.ROOT))
    }
}

data class P7FDecodedConversation(
    val snapshot: ConversationSnapshot,
    val modelUsageByAssistantMessage: Map<MessageNodeId, CloudResponseModelUsage>,
)
