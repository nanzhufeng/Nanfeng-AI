package com.nanzhufeng.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

/**
 * P6-A's Android source adapter for one already-persisted ordinary text conversation.
 * It is deliberately narrower than a workspace export: projects, Knowledge, Memory,
 * relationships and private attachments stay out until each has a complete semantic mapper.
 */
sealed interface ConversationExchangePreparation {
    data class Prepared(val snapshot: NfaiExchangePreparedSnapshot) : ConversationExchangePreparation
    data class Rejected(val reason: String) : ConversationExchangePreparation
}

class ExportConversationExchangeUseCase(
    private val conversations: ConversationRepository,
    private val appVersion: String,
    private val clock: Clock,
    private val exportId: () -> String = { UUID.randomUUID().toString() },
) {
    fun prepare(conversationId: ConversationId): ConversationExchangePreparation = runCatching {
        val snapshot = conversations.findById(conversationId) ?: return ConversationExchangePreparation.Rejected("当前对话不存在或已不可读取。")
        val conversation = snapshot.conversation
        require(conversation.surface == ConversationSurface.CHAT) { "临时或工作区对话不能作为跨端交换源。" }
        require(conversation.deletedAt == null && conversation.archivedAt == null) { "仅支持当前活动对话。" }
        require(conversation.projectId == null) { "当前对话属于项目；项目保真映射尚未完成。" }
        require(conversation.currentLeafMessageId != null && snapshot.nodes.isNotEmpty()) { "当前对话没有可交换的已保存消息。" }
        require(snapshot.draft.text.isBlank() && snapshot.draft.attachments.isEmpty()) { "请先处理草稿后再导出跨端交换包。" }
        require(snapshot.nodes.all { node -> node.content.isNotEmpty() && node.content.all { it is ContentBlock.Text } }) { "当前只支持不含附件或工具结果的文本会话。" }

        val hasHighSensitiveText = snapshot.nodes
            .flatMap { node -> node.content.filterIsInstance<ContentBlock.Text>() }
            .any { MemoryDomain.sensitiveRejection(it.text) != null }
        val exchange = JSONObject().apply {
            put("format", NFAI_EXCHANGE_V1_FORMAT)
            put("version", NFAI_EXCHANGE_V1_VERSION)
            put("export", JSONObject().apply {
                put("id", exportId())
                put("createdAt", clock.instant().toString())
                put("origin", JSONObject().put("platform", "ANDROID").put("appVersion", appVersion))
                put("semanticHash", "")
                put("sensitivity", if (hasHighSensitiveText) "HIGH_SENSITIVE" else "NORMAL")
            })
            put("projects", JSONArray())
            put("conversations", JSONArray().put(conversationJson(snapshot)))
            put("knowledge", JSONArray())
            put("memory", JSONArray())
            put("relations", JSONArray())
            put("settings", JSONObject().put("uiLanguage", "zh-CN").put("theme", "SYSTEM"))
        }
        ConversationExchangePreparation.Prepared(
            NfaiExchangePreparedSnapshot(
                selection = NfaiExchangeExportSelection(
                    projectIds = emptySet(),
                    conversationIds = setOf(conversation.id.value),
                    knowledgeIds = emptySet(),
                    memoryIds = emptySet(),
                    relationIds = emptySet(),
                ),
                exchangeJson = NfaiExchangeV1Gateway.withComputedSemanticHash(exchange),
            ),
        )
    }.getOrElse { ConversationExchangePreparation.Rejected(it.message ?: "当前对话不满足跨端交换条件。") }

    private fun conversationJson(snapshot: ConversationSnapshot): JSONObject = snapshot.conversation.let { conversation ->
        JSONObject().apply {
            put("id", conversation.id.value)
            put("projectId", JSONObject.NULL)
            put("title", conversation.title)
            put("currentLeafId", requireNotNull(conversation.currentLeafMessageId).value)
            put("pinned", conversation.pinnedAt != null)
            put("archived", false)
            put("revision", conversation.revision)
            put("createdAt", conversation.createdAt.toString())
            put("updatedAt", conversation.updatedAt.toString())
            put("messages", JSONArray().apply {
                snapshot.nodes.sortedWith(compareBy<MessageNode> { it.createdAt }.thenBy { it.id.value }).forEach { node ->
                    put(JSONObject().apply {
                    put("id", node.id.value)
                    put("parentId", node.parentMessageId?.value ?: JSONObject.NULL)
                    put("ordinal", node.siblingPosition)
                    put("role", node.role.name.lowercase())
                    put("delivery", node.deliveryState.name)
                    put("revision", node.revision.revision)
                    put("createdAt", node.createdAt.toString())
                    put("blocks", JSONArray().apply {
                        node.content.forEachIndexed { ordinal, block ->
                            put(JSONObject().put("kind", "TEXT").put("ordinal", ordinal).put("text", (block as ContentBlock.Text).text))
                        }
                    })
                    })
                }
            })
        }
    }
}
